BMA - Basic Music App (Yes, that's the name I am going with and I am a bit too lazy to change the folder names for it!).

As the name suggests, this app has been made to easily turn your PC/Mac/Linux system into a server for your music library which you can then connect to using the Android app made for this.

Built using Go + Fyne for desktop and Kotlin for Android.

Currently, it is in very early stages, however the desktop app is pretty much complete. The Android app will be made into a full fledged music player that will allow you to easily create playlist along using other features. 

Current Features:

- **HTTP Music Server** (Port 8008) - Stream MP3 files to connected devices
- **QR Code Pairing** - Secure device pairing with Bearer token authentication
- **Tailscale Integration** - Remote access over encrypted Tailscale networks
- **Album Organization** - Smart folder-based album detection and organization
- **Cross-Platform GUI** - Native-looking interface on Windows, Linux, and macOS
- **Android Compatible** - Works with the existing BMSA Android app
