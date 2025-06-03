import SwiftUI
import Foundation
#if canImport(Darwin)
import Darwin
#endif

struct ServerStatusBar: View {
    @EnvironmentObject var serverManager: ServerManager
    @EnvironmentObject var musicLibrary: MusicLibrary
    @State private var showingPairingSheet = false
    
    var body: some View {
        HStack {
            // Server status with Tailscale info
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Circle()
                        .fill(serverManager.isRunning ? Color.green : Color.red)
                        .frame(width: 10, height: 10)
                    
                    Text(serverManager.isRunning ? "Server Running" : "Server Stopped")
                        .font(.caption)
                    
                    // Protocol indicator
                    Text(serverManager.hasTailscale ? "HTTP via Tailscale" : "HTTP (Local)")
                        .font(.caption2)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(serverManager.hasTailscale ? Color.blue.opacity(0.2) : Color.orange.opacity(0.2))
                        .foregroundColor(serverManager.hasTailscale ? .blue : .orange)
                        .cornerRadius(4)
                    
                    // Tailscale indicator
                    if serverManager.hasTailscale {
                        Image(systemName: "shield.checkered")
                            .foregroundColor(.blue)
                            .font(.caption)
                            .help("Tailscale Connected")
                    }
                }
                
                if serverManager.isRunning && !serverManager.serverURL.isEmpty {
                    Text(serverManager.serverURL)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .textSelection(.enabled)
                        .help("Server URL")
                }
            }
            
            Spacer()
            
            // Folder selection
            if let folderPath = musicLibrary.selectedFolderPath {
                Text(URL(fileURLWithPath: folderPath).lastPathComponent)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Button("Select Folder") {
                musicLibrary.selectFolder()
            }
            .buttonStyle(.bordered)
            
            // Pairing button (only when server is running)
            if serverManager.isRunning {
                Button("Pair Device") {
                    showingPairingSheet = true
                }
                .buttonStyle(.bordered)
                .sheet(isPresented: $showingPairingSheet) {
                    PairingView()
                        .environmentObject(serverManager)
                }
            }
            
            // Server toggle
            Button(serverManager.isRunning ? "Stop Server" : "Start Server") {
                if serverManager.isRunning {
                    serverManager.stopServer()
                } else {
                    serverManager.startServer()
                }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

struct PairingView: View {
    @EnvironmentObject var serverManager: ServerManager
    @Environment(\.dismiss) private var dismiss
    @State private var qrCodeImage: NSImage?
    @State private var pairingToken: String = ""
    @State private var expirationDate: Date = Date()
    
    var body: some View {
        VStack(spacing: 20) {
            HStack {
                Text("Pair New Device")
                    .font(.title2)
                    .fontWeight(.semibold)
                
                Spacer()
                
                Button("Done") {
                    dismiss()
                }
            }
            .padding()
            
            VStack(spacing: 16) {
                Text("Scan this QR code with your mobile device")
                    .font(.headline)
                
                if let qrImage = qrCodeImage {
                    Image(nsImage: qrImage)
                        .interpolation(.none)
                        .resizable()
                        .frame(width: 250, height: 250)
                        .background(Color.white)
                        .cornerRadius(12)
                        .shadow(radius: 4)
                } else {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 250, height: 250)
                        .overlay(
                            ProgressView()
                        )
                }
                
                VStack(spacing: 8) {
                    HStack {
                        Text("Token:")
                            .fontWeight(.medium)
                        Text(pairingToken.prefix(8) + "...")
                            .font(.monospaced(.body)())
                            .foregroundColor(.secondary)
                        
                        Spacer()
                        
                        Button("Copy") {
                            NSPasteboard.general.clearContents()
                            NSPasteboard.general.setString(pairingToken, forType: .string)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                    
                    HStack {
                        Text("Expires:")
                            .fontWeight(.medium)
                        Text(expirationDate, style: .relative)
                            .foregroundColor(.secondary)
                        
                        Spacer()
                    }
                    
                    HStack {
                        Text("Server:")
                            .fontWeight(.medium)
                        
                        if serverManager.hasTailscale {
                            Text("HTTP via Tailscale")
                                .foregroundColor(.blue)
                        } else {
                            Text("Local Network (HTTP)")
                                .foregroundColor(.orange)
                        }
                        
                        Spacer()
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
                
                HStack {
                    Button("Generate New Token") {
                        generateNewToken()
                    }
                    .buttonStyle(.bordered)
                    
                    if serverManager.currentPairingToken != nil {
                        Button("Revoke Token") {
                            if let token = serverManager.currentPairingToken {
                                serverManager.revokePairingToken(token)
                                generateNewToken() // Generate a new one
                            }
                        }
                        .buttonStyle(.bordered)
                        .foregroundColor(.red)
                    }
                }
            }
            .padding()
        }
        .frame(width: 400, height: 500)
        .onAppear {
            generateNewToken()
        }
    }
    
    private func generateNewToken() {
        pairingToken = serverManager.generatePairingToken()
        expirationDate = Date().addingTimeInterval(3600) // 1 hour
        
        let serverUrl: String
        if serverManager.hasTailscale && !serverManager.tailscaleURL.isEmpty {
            // Use HTTP over Tailscale with port 8008
            serverUrl = "\(serverManager.tailscaleURL):8008"
        } else {
            // Use local HTTP URL
            serverUrl = serverManager.serverURL
        }
        
        qrCodeImage = QRCodeGenerator.generatePairingQRCode(
            serverUrl: serverUrl,
            token: pairingToken,
            expiresAt: expirationDate
        )
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
} 