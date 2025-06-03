import Foundation

struct ConnectedDevice: Identifiable, Codable {
    let id = UUID()
    let token: String
    let deviceName: String? // User agent or custom name
    let ipAddress: String
    let connectedAt: Date
    var lastSeenAt: Date
    
    // Exclude id from Codable as it's auto-generated
    enum CodingKeys: String, CodingKey {
        case token, deviceName, ipAddress, connectedAt, lastSeenAt
    }
    
    init(token: String, deviceName: String?, ipAddress: String) {
        self.token = token
        self.deviceName = deviceName
        self.ipAddress = ipAddress
        self.connectedAt = Date()
        self.lastSeenAt = Date()
    }
    
    var displayName: String {
        if let name = deviceName, !name.isEmpty {
            return name
        }
        return "Device (\(ipAddress))"
    }
    
    var isActive: Bool {
        // Consider device active if seen within last 5 minutes
        Date().timeIntervalSince(lastSeenAt) < 300
    }
} 