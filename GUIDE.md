Step-by-step Guide for setting up BMA.

Before anything, please download the source code from GitHub along with the .apk file from the release section on the same page. 

Link: https://github.com/picccassso/BMA

After that, unzip the source code to a folder of your choice, and install the .apk file on your Android device. 

⚠️ Note: This app is provided as open-source.
You are encouraged to review and build it yourself, however, I have provided a .exe for Windows, a working UNIX executable file for MacOS (test-build) and ___ for Linux. I will mention this again before step 3. 
Some platforms may display warnings when running unsigned binaries — this is expected and does not indicate a security issue.

1. Please install Tailscale from the official download page and install it on your system, making sure you login into your account. Please install it on your Android device as well, and make sure you login into the **SAME** account as you did on your desktop system. **THIS IS CRUCIAL**.

Link: https://tailscale.com/download


2. To build the app yourself, you will be needing the Go language installed on your system. Please install it from their official website. After you do that, please install fyne as well, as the app is built using the fyne GUI framework. I have left the command used in order to install it. 

Link: https://go.dev/dl/

Fyne install command (**AFTER** installing Go): go get fyne.io/fyne/v2

If you would like to package the app as a .app or .exe, you can also install fyne CLI using the command below:

go install fyne.io/fyne/v2/cmd/fyne@latest

*I have provided a .exe for Windows, a working UNIX executable file for MacOS (test-build) and ___ for Linux.*

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


