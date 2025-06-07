Step-by-step Guide for setting up BMA **without** building!

Link: https://github.com/picccassso/BMA

⚠️ Note: This app is provided as open-source. 

You are encouraged to review and build it yourself, however, on the releases section there is 1) pre-built .apk file for you to install 2) pre-built .exe file 3) pre-built universal (Apple Silicon/Intel) universal binary. All of these can be used without installing any additional dependencies. For Linux, there is a build-flatpak.sh that will build a flatpak for you. Here are the instructions for doing it here:

*You WILL need flatpak-builder to build the flatpak, as well as flatpak already installed, both can be installed and set up through the following links:*

https://flatpak.org/
https://docs.flatpak.org/en/latest/flatpak-builder.html

In the BMA-Go folder, open a terminal and enter the following commands:

./build-flatpak.sh

flatpak install --user bma-go-linux.flatpak

To uninstall:

flatpak uninstall --user com.bma.BasicMusicApp


1. Please install Tailscale from the official download page and install it on your system, making sure you login into your account. Please install it on your Android device as well, and make sure you login into the **SAME** account as you did on your desktop system. **THIS IS CRUCIAL**.

Link: https://tailscale.com/download

3. After doing Step 1 and 2, you are ready to launch the app on your system and on your Android device. Please make sure to have Tailscale connected **BEFORE** you launch both apps on both devices. This will make the setup process MUCH easier. 

4. Launch the app on your desktop system. It will take you through the following steps:
    - Check if you have Tailscale installed and running.
    - Give you a QR code to my GitHub page in case you don't have the .apk file already installed.
    - Select your music folder. 
    - The server will be started up automatically.
    - You will need to click "Start Streaming", after which a QR code will appear on your screen. This will be used for pairing on your Android device. 
    
5. Launch the app on your mobile device. t will take through the following steps: 
    - Make you press continue to get to next screen.
    - Check for Tailscale and make you open it and connect if you don't have it up and running.
    - Ask you for camera permission to scan the QR code.
    - After scanning, you will be taken to the page with all your albums.
    
This is how you setup the desktop app and Android app for BMA. 



