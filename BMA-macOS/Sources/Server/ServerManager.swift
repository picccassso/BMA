import Foundation
import Vapor
import NIOCore
import NIOSSL

// Custom middleware for detailed request/response logging
struct RequestLoggingMiddleware: AsyncMiddleware {
    func respond(to request: Request, chainingTo next: AsyncResponder) async throws -> Response {
        let startTime = Date()
        let clientIP = request.remoteAddress?.description ?? "unknown"
        let userAgent = request.headers.first(name: "User-Agent") ?? "unknown"
        let auth = request.headers.bearerAuthorization?.token.prefix(8) ?? "none"
        
        print("📥 [REQUEST] \(request.method) \(request.url.path)")
        print("   └─ From: \(clientIP)")
        print("   └─ User-Agent: \(userAgent)")
        print("   └─ Auth: \(auth)...")
        
        do {
            let response = try await next.respond(to: request)
            let duration = Date().timeIntervalSince(startTime)
            
            print("📤 [RESPONSE] \(response.status.code) \(response.status.reasonPhrase) (\(String(format: "%.2f", duration * 1000))ms)")
            
            return response
        } catch {
            let duration = Date().timeIntervalSince(startTime)
            print("❌ [ERROR] \(error) (\(String(format: "%.2f", duration * 1000))ms)")
            throw error
        }
    }
}

@MainActor
class ServerManager: ObservableObject {
    @Published var isRunning = false
    @Published var serverURL: String = ""
    @Published var tailscaleURL: String = ""
    @Published var hasTailscale = false
    @Published var currentPairingToken: String? = nil
    @Published var useHTTPS = false // User can choose protocol
    
    private var app: Application?
    private let httpsPort = 8443
    private let httpPort = 8008
    private var pairingTokens: [String: Date] = [:] // token -> expiration
    
    init() {
        print("🚀 Initializing ServerManager...")
        Task {
            print("🔍 Checking Tailscale status...")
            await checkTailscaleStatus()
            print("📡 Setting up server...")
            await setupServer()
            print("✅ ServerManager initialization complete")
        }
    }
    
    private func checkTailscaleStatus() async {
        print("🔍 Starting Tailscale detection...")
        hasTailscale = await detectTailscale()
        if hasTailscale {
            print("✅ Tailscale detected! Getting hostname...")
            tailscaleURL = await getTailscaleHostname()
            print("🌐 Tailscale URL: \(tailscaleURL)")
        } else {
            print("❌ Tailscale not detected or not running")
        }
    }
    
    private func detectTailscale() async -> Bool {
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .background).async {
                // Try multiple possible Tailscale paths
                let possiblePaths = [
                    "/usr/local/bin/tailscale",
                    "/opt/homebrew/bin/tailscale",
                    "/Applications/Tailscale.app/Contents/MacOS/Tailscale"
                ]
                
                var tailscalePath: String?
                for path in possiblePaths {
                    if FileManager.default.fileExists(atPath: path) {
                        tailscalePath = path
                        print("Found Tailscale at: \(path)")
                        break
                    }
                }
                
                guard let foundPath = tailscalePath else {
                    print("Tailscale binary not found in any of the expected locations: \(possiblePaths)")
                    continuation.resume(returning: false)
                    return
                }
                
                // Check if tailscale is actually connected
                Task {
                    let isConnected = await self.checkTailscaleConnection(at: foundPath)
                    continuation.resume(returning: isConnected)
                }
            }
        }
    }
    
    private func checkTailscaleConnection(at path: String) async -> Bool {
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .background).async {
                let task = Process()
                task.launchPath = path
                task.arguments = ["status", "--json"]
                
                let pipe = Pipe()
                task.standardOutput = pipe
                task.standardError = Pipe()
                
                do {
                    try task.run()
                    task.waitUntilExit()
                    
                    if task.terminationStatus == 0 {
                        let data = pipe.fileHandleForReading.readDataToEndOfFile()
                        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                           let backendState = json["BackendState"] as? String {
                            print("Tailscale status: \(backendState)")
                            let isRunning = backendState == "Running"
                            if isRunning {
                                print("✅ Tailscale is running and connected")
                            } else {
                                print("❌ Tailscale is not in Running state")
                            }
                            continuation.resume(returning: isRunning)
                        } else {
                            print("❌ Failed to parse Tailscale status JSON")
                            continuation.resume(returning: false)
                        }
                    } else {
                        print("❌ Tailscale status command failed with exit code: \(task.terminationStatus)")
                        continuation.resume(returning: false)
                    }
                } catch {
                    print("❌ Failed to check Tailscale status: \(error)")
                    continuation.resume(returning: false)
                }
            }
        }
    }
    
    private func getTailscaleHostname() async -> String {
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .background).async {
                // Find Tailscale path again
                let possiblePaths = [
                    "/usr/local/bin/tailscale",
                    "/opt/homebrew/bin/tailscale",
                    "/Applications/Tailscale.app/Contents/MacOS/Tailscale"
                ]
                
                var tailscalePath: String?
                for path in possiblePaths {
                    if FileManager.default.fileExists(atPath: path) {
                        tailscalePath = path
                        break
                    }
                }
                
                guard let foundPath = tailscalePath else {
                    print("❌ Cannot find Tailscale binary for hostname lookup")
                    continuation.resume(returning: "")
                    return
                }
                
                let task = Process()
                task.launchPath = foundPath
                task.arguments = ["status", "--json"]
                
                let pipe = Pipe()
                task.standardOutput = pipe
                task.standardError = Pipe()
                
                do {
                    try task.run()
                    task.waitUntilExit()
                    
                    if task.terminationStatus == 0 {
                        let data = pipe.fileHandleForReading.readDataToEndOfFile()
                        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                           let selfInfo = json["Self"] as? [String: Any],
                           let dnsName = selfInfo["DNSName"] as? String {
                            let cleanedName = dnsName.trimmingCharacters(in: CharacterSet(charactersIn: "."))
                            // Use HTTP, not HTTPS - Tailscale provides network encryption
                            let httpUrl = "http://\(cleanedName)"
                            print("✅ Found Tailscale hostname: \(httpUrl)")
                            continuation.resume(returning: httpUrl)
                        } else {
                            print("❌ Failed to extract DNS name from Tailscale status")
                            continuation.resume(returning: "")
                        }
                    } else {
                        print("❌ Failed to get Tailscale hostname")
                        continuation.resume(returning: "")
                    }
                } catch {
                    print("❌ Failed to get Tailscale hostname: \(error)")
                    continuation.resume(returning: "")
                }
            }
        }
    }
    
    private func setupServer() async {
        // Create Vapor app using new API
        app = try? await Application.make(.development)
        
        guard let app = app else { return }
        
        // Enable detailed logging
        app.logger.logLevel = .debug
        
        // Add request logging middleware
        app.middleware.use(RequestLoggingMiddleware())
        
        // Always run HTTP locally - Tailscale handles HTTPS termination
        app.http.server.configuration.hostname = "0.0.0.0"
        app.http.server.configuration.port = httpPort
        
        // Setup authentication middleware
        await setupAuthenticationMiddleware(app: app)
        
        // Setup routes
        await setupRoutes()
    }
    
    private func setupHTTPS(app: Application) async {
        // This method is no longer used - keeping for backward compatibility
        app.http.server.configuration.hostname = "0.0.0.0"
        app.http.server.configuration.port = httpPort
    }
    
    private func setupAuthenticationMiddleware(app: Application) async {
        // Create a custom middleware struct
        struct AuthMiddleware: AsyncMiddleware {
            let serverManager: ServerManager
            
            func respond(to request: Request, chainingTo next: AsyncResponder) async throws -> Response {
                // Skip authentication for certain endpoints
                let publicEndpoints = ["/health", "/info", "/pair"]
                let path = request.url.path
                
                if publicEndpoints.contains(path) || path.starts(with: "/pair/") {
                    return try await next.respond(to: request)
                }
                
                // Check for Bearer token
                guard let authorization = request.headers.bearerAuthorization else {
                    throw Abort(.unauthorized, reason: "Missing authorization token")
                }
                
                let token = authorization.token
                
                // Validate token
                let isValid = await MainActor.run {
                    serverManager.isValidTokenSync(token)
                }
                
                if isValid {
                    return try await next.respond(to: request)
                } else {
                    throw Abort(.unauthorized, reason: "Invalid or expired token")
                }
            }
        }
        
        app.middleware.use(AuthMiddleware(serverManager: self))
    }
    
    private func isValidTokenSync(_ token: String) -> Bool {
        guard let expiration = pairingTokens[token] else {
            return false
        }
        
        // Check if token is expired
        if Date() > expiration {
            pairingTokens.removeValue(forKey: token)
            return false
        }
        
        return true
    }
    
    func generatePairingToken(expiresInMinutes: Int = 60) -> String {
        let token = UUID().uuidString
        let expiration = Date().addingTimeInterval(TimeInterval(expiresInMinutes * 60))
        
        pairingTokens[token] = expiration
        currentPairingToken = token
        
        // Clean up expired tokens
        cleanupExpiredTokens()
        
        return token
    }
    
    private func cleanupExpiredTokens() {
        let now = Date()
        pairingTokens = pairingTokens.filter { $0.value > now }
    }
    
    func revokePairingToken(_ token: String) {
        pairingTokens.removeValue(forKey: token)
        if currentPairingToken == token {
            currentPairingToken = nil
        }
    }
    
    func revokeAllTokens() {
        pairingTokens.removeAll()
        currentPairingToken = nil
    }
    
    private func setupRoutes() async {
        guard let app = app else { return }
        
        // Health check endpoint (public)
        app.get("health") { req async -> String in
            print("🔍 Health check requested from \(req.remoteAddress?.description ?? "unknown")")
            return "{\"status\": \"healthy\"}"
        }
        
        // Server info endpoint (public)
        app.get("info") { req async -> Response in
            print("ℹ️ Server info requested")
            let tailscaleURL = await MainActor.run { self.tailscaleURL }
            let hasTailscale = await MainActor.run { self.hasTailscale }
            let httpsPort = await MainActor.run { self.httpsPort }
            let httpPort = await MainActor.run { self.httpPort }
            
            let info: [String: Any] = [
                "server": "BMA Music Server",
                "version": "2.0",
                "hasTailscale": hasTailscale,
                "tailscaleUrl": tailscaleURL,
                "httpsPort": httpsPort,
                "httpPort": httpPort,
                "protocol": hasTailscale ? "https" : "http"
            ]
            
            let encoder = JSONEncoder()
            encoder.outputFormatting = .prettyPrinted
            
            if let jsonData = try? encoder.encode(info.mapValues { String(describing: $0) }) {
                let response = Response(status: .ok)
                response.headers.contentType = .json
                response.body = .init(data: jsonData)
                print("✅ Server info sent successfully")
                return response
            } else {
                print("❌ Failed to encode server info")
                return Response(status: .internalServerError)
            }
        }
        
        // Pairing endpoint (public)
        app.post("pair") { req async -> Response in
            let token = await MainActor.run { self.generatePairingToken() }
            let hasTailscale = await MainActor.run { self.hasTailscale }
            let tailscaleURL = await MainActor.run { self.tailscaleURL }
            let httpPort = await MainActor.run { self.httpPort }
            let localIP = await MainActor.run { self.getLocalIPAddress() }
            
            let serverUrl: String
            if hasTailscale && !tailscaleURL.isEmpty {
                // Use HTTP over Tailscale with port 8008
                serverUrl = "\(tailscaleURL):\(httpPort)"
            } else {
                // Use local HTTP URL
                serverUrl = "http://\(localIP ?? "localhost"):\(httpPort)"
            }
            
            let pairingInfo = [
                "token": token,
                "serverUrl": serverUrl,
                "expiresAt": ISO8601DateFormatter().string(from: Date().addingTimeInterval(3600))
            ]
            
            let encoder = JSONEncoder()
            if let jsonData = try? encoder.encode(pairingInfo) {
                let response = Response(status: .ok)
                response.headers.contentType = .json
                response.body = .init(data: jsonData)
                return response
            } else {
                return Response(status: .internalServerError)
            }
        }
        
        // Revoke pairing token (authenticated)
        app.delete("pair", ":token") { req async -> Response in
            guard let token = req.parameters.get("token") else {
                return Response(status: .badRequest)
            }
            
            await MainActor.run {
                self.revokePairingToken(token)
            }
            return Response(status: .noContent)
        }
        
        // List songs endpoint (authenticated)
        app.get("songs") { req async -> Response in
            let authHeader = req.headers.bearerAuthorization?.token ?? "none"
            print("🎵 Songs requested with auth: \(authHeader.prefix(8))...")
            
            let songs = await MainActor.run { MusicLibrary.shared.songs }
            print("📊 Found \(songs.count) songs to return")
            
            let songData = songs.map { song in
                [
                    "id": song.id.uuidString,
                    "filename": song.filename,
                    "title": song.title,
                    "artist": song.artist ?? "",
                    "album": song.album ?? ""
                ]
            }
            
            let encoder = JSONEncoder()
            encoder.outputFormatting = .prettyPrinted
            
            if let jsonData = try? encoder.encode(songData) {
                let response = Response(status: .ok)
                response.headers.contentType = .json
                response.body = .init(data: jsonData)
                print("✅ Songs list sent successfully")
                return response
            } else {
                print("❌ Failed to encode songs data")
                return Response(status: .internalServerError)
            }
        }
        
        // Stream song endpoint (authenticated)
        app.get("stream", ":songId") { req async -> Response in
            guard let songId = req.parameters.get("songId") else {
                return Response(status: .badRequest)
            }
            
            let song = await MainActor.run { MusicLibrary.shared.getSong(by: songId) }
            
            guard let song = song else {
                return Response(status: .notFound)
            }
            
            let fileURL = URL(fileURLWithPath: song.path)
            
            guard FileManager.default.fileExists(atPath: song.path),
                  let fileData = try? Data(contentsOf: fileURL) else {
                return Response(status: .notFound)
            }
            
            let response = Response(status: .ok)
            response.headers.contentType = .mp3
            response.headers.add(name: .contentLength, value: String(fileData.count))
            response.body = .init(data: fileData)
            
            return response
        }
    }
    
    func startServer() {
        guard let app = app, !isRunning else { 
            print("⚠️ Server start requested but app is nil or already running")
            return 
        }
        
        print("🚀 Starting server...")
        print("📊 Tailscale available: \(hasTailscale)")
        print("🔗 Tailscale URL: \(tailscaleURL)")
        
        Task {
            do {
                try await app.startup()
                
                await MainActor.run {
                    self.isRunning = true
                    
                    // Show detailed network information
                    let localIP = self.getLocalIPAddress()
                    print("\n📡 SERVER NETWORK INFORMATION:")
                    print("   Local IP: \(localIP ?? "unknown")")
                    print("   HTTP Port: \(self.httpPort)")
                    print("   Listening on: 0.0.0.0:\(self.httpPort)")
                    
                    if self.hasTailscale && !self.tailscaleURL.isEmpty {
                        // Use HTTP over Tailscale (network-level encryption)
                        self.serverURL = "\(self.tailscaleURL):\(self.httpPort)"
                        print("\n🔒 TAILSCALE CONFIGURATION:")
                        print("   Tailscale URL: \(self.tailscaleURL)")
                        print("   Public Access: \(self.serverURL)")
                        print("   Note: HTTP over Tailscale (network-level encryption)")
                        
                        print("\n✅ AVAILABLE CONNECTION URLs:")
                        print("   📱 Android (via Tailscale): \(self.serverURL)")
                        print("   🌐 Local network: http://\(localIP ?? "localhost"):\(self.httpPort)")
                    } else {
                        // No Tailscale, use local HTTP
                        let hostname = localIP ?? "localhost"
                        self.serverURL = "http://\(hostname):\(self.httpPort)"
                        
                        print("\n🌐 LOCAL NETWORK CONFIGURATION:")
                        print("   Server URL: \(self.serverURL)")
                        print("   Protocol: HTTP only (no Tailscale)")
                        
                        print("\n✅ AVAILABLE CONNECTION URLs:")
                        print("   📱 Android (local): \(self.serverURL)")
                        print("   🖥️ Browser test: \(self.serverURL)/health")
                    }
                    
                    print("\n🎵 Server is ready for music streaming!")
                    print("📱 Generate a QR code to pair devices")
                    print("🔍 Watching for incoming connections...")
                }
                
            } catch {
                print("❌ Failed to start server: \(error)")
                await MainActor.run {
                    self.isRunning = false
                }
            }
        }
    }
    
    func stopServer() {
        guard let app = app else { return }
        
        Task {
            do {
                try await app.asyncShutdown()
                await MainActor.run {
                    self.isRunning = false
                    self.serverURL = ""
                    self.revokeAllTokens()
                    print("Server stopped")
                }
            } catch {
                print("Failed to shutdown server gracefully: \(error)")
                await MainActor.run {
                    self.isRunning = false
                    self.serverURL = ""
                    self.revokeAllTokens()
                }
            }
        }
    }
    
    private func getLocalIPAddress() -> String? {
        var address: String?
        
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0 else { return nil }
        guard let firstAddr = ifaddr else { return nil }
        
        for ifptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let interface = ifptr.pointee
            
            let addrFamily = interface.ifa_addr.pointee.sa_family
            if addrFamily == UInt8(AF_INET) {
                
                let name = String(cString: interface.ifa_name)
                if name == "en0" || name == "en1" {
                    
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                                &hostname, socklen_t(hostname.count),
                                nil, socklen_t(0), NI_NUMERICHOST)
                    address = String(cString: hostname)
                }
            }
        }
        freeifaddrs(ifaddr)
        
        return address
    }
    
    deinit {
        Task { [weak app] in
            do {
                try await app?.asyncShutdown()
            } catch {
                print("Failed to shutdown server in deinit: \(error)")
            }
        }
    }
} 