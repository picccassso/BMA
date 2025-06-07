🎵 **BMA** – Basic Music App

A secure, self-hosted music streaming solution built with Go (desktop) and Kotlin (Android).

🖥️ What is **BMA**?

BMA lets you stream your personal music library from your PC, Mac, or Linux machine to your Android device — securely and privately.
Using Tailscale for secure networking, your desktop becomes a music server, and your phone becomes your personal player. No cloud, no open ports, no setup headaches.

🛠️ **How It Works**

- The desktop app (written in Go) turns your system into a lightweight music server.
- You select a folder to act as your music library.
- The server runs locally and securely, streaming over HTTP within a Tailscale mesh network.
- Your Android device scans a QR code to pair with the server.
- Music metadata is pulled instantly, albums and tracks are displayed, and you’re ready to listen.
- // 🔐 Note: The app currently streams over HTTP, secured by Tailscale’s encrypted tunnel. HTTPS support is planned. //

📱 **Android App Features**

- ✅ Instant connection via QR pairing
- 🎵 Smart album organization: tap an album to browse its tracks
- 🔊 Mini player UI with play/pause, next/previous, shuffle, repeat
- 🔍 Search functionality for albums and tracks
- 📶 Connection status and server info available in Settings
- 📡 Automatically connects to your server if reachable

🧩 **Future Improvements**
- 📊 Streaming time tracker (stats)
- 📁 Playlist support
- 📃 Queue management
- 👨🏻‍🔧 Any feedback given by community!

🤝 Tech Stack
- Desktop App: Go
- Mobile App: Kotlin (Jetpack Compose)
- Networking: Tailscale
- Streaming: HTTP (within Tailscale network)

🪳**KNOWN BUGS/ISSUES**
- Some users get stuck on "Checking server connection..." or when loading library.
- Some users can't get past "Tailscale not found. Please download and install it" - especially Linux, even if it is up and running and connected.
- Android App gets in a loop if it is disconnected and needs to be reset completely to work again and to connect to server.
- Shuffle mode does not work properly, it will shuffle, but does not keep the position of the queue.
- App keeps playing even if you close the app on android.
- Going back from Settings/Library takes you directly to the home screen/app launcher instead of back to library.
- If there is a space after typing half a word when searching a song, it will not display anything.
