import SwiftUI

struct ConnectedDevicesView: View {
    @EnvironmentObject var serverManager: ServerManager
    @Environment(\.dismiss) private var dismiss
    let onShowPairing: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            HStack {
                Text("Connected Devices")
                    .font(.title2)
                    .fontWeight(.semibold)
                
                Spacer()
                
                Button("Done") {
                    dismiss()
                }
            }
            .padding()
            
            if serverManager.connectedDevices.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "iphone.slash")
                        .font(.system(size: 60))
                        .foregroundColor(.gray)
                    
                    Text("No devices connected")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    
                    Text("Generate a QR code to pair new devices")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                    
                    Button("Show QR Code") {
                        onShowPairing()
                    }
                    .buttonStyle(.borderedProminent)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                VStack(spacing: 12) {
                    HStack {
                        Text("\(serverManager.connectedDevices.count) device(s) connected")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        
                        Spacer()
                        
                        Button("Disconnect All") {
                            serverManager.disconnectAllDevices()
                        }
                        .buttonStyle(.bordered)
                        .foregroundColor(.red)
                    }
                    
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(serverManager.connectedDevices) { device in
                                DeviceRow(device: device)
                                    .environmentObject(serverManager)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                    
                    Divider()
                    
                    HStack {
                        Button("Pair New Device") {
                            onShowPairing()
                        }
                        .buttonStyle(.bordered)
                        
                        Spacer()
                    }
                }
            }
        }
        .frame(width: 450, height: 400)
        .onAppear {
            // Clean up inactive devices when view appears
            serverManager.cleanupInactiveDevices()
        }
    }
}

struct DeviceRow: View {
    let device: ConnectedDevice
    @EnvironmentObject var serverManager: ServerManager
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Circle()
                        .fill(device.isActive ? Color.green : Color.orange)
                        .frame(width: 8, height: 8)
                    
                    Text(device.displayName)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    if !device.isActive {
                        Text("Inactive")
                            .font(.caption)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.2))
                            .foregroundColor(.orange)
                            .cornerRadius(4)
                    }
                }
                
                Text("IP: \(device.ipAddress)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Text("Connected: \(device.connectedAt, style: .relative) ago")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                if device.isActive {
                    Text("Last seen: \(device.lastSeenAt, style: .relative) ago")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
            
            VStack(spacing: 8) {
                Button("Disconnect") {
                    serverManager.disconnectDevice(device)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .foregroundColor(.red)
                
                Button("Copy Token") {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(device.token, forType: .string)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
    }
} 