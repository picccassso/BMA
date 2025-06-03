import Foundation
import CoreImage
import SwiftUI

struct PairingData: Codable {
    let serverUrl: String
    let token: String
    let expiresAt: String
}

class QRCodeGenerator {
    
    static func generateQRCode(for pairingData: PairingData) -> NSImage? {
        // Encode pairing data to JSON
        let encoder = JSONEncoder()
        guard let jsonData = try? encoder.encode(pairingData),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            return nil
        }
        
        // Create QR code using correct Core Image API
        let context = CIContext()
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else {
            return nil
        }
        
        let data = Data(jsonString.utf8)
        filter.setValue(data, forKey: "inputMessage")
        
        // Set error correction level to medium
        filter.setValue("M", forKey: "inputCorrectionLevel")
        
        guard let outputImage = filter.outputImage else {
            return nil
        }
        
        // Scale up the QR code for better visibility
        let scaleTransform = CGAffineTransform(scaleX: 10, y: 10)
        let scaledImage = outputImage.transformed(by: scaleTransform)
        
        // Convert to NSImage
        guard let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) else {
            return nil
        }
        
        return NSImage(cgImage: cgImage, size: NSSize(width: 300, height: 300))
    }
    
    static func generatePairingQRCode(serverUrl: String, token: String, expiresAt: Date) -> NSImage? {
        let pairingData = PairingData(
            serverUrl: serverUrl,
            token: token,
            expiresAt: ISO8601DateFormatter().string(from: expiresAt)
        )
        
        return generateQRCode(for: pairingData)
    }
} 