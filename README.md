## About
This application is a port of the Transmission daemon for Android complemented with the following features: 

* Manage downloads directly from the application
* Multiple watch/download directories
* Support for HTTP(S)/SOCKS proxy
* WiFi/Ethernet mode to save on mobile data
* Keep CPU/WiFi awake to complete all downloads before the device goes to sleep
* Sequential download allows playing media files while downloading 
* Open .torrent files or torrent/magnet URLs and stream the selected files to a media player
* Builtin UPnP MediaServer. Download media files to a phone/tablet/TV-box and watch on a TV or another UPnP compatible media player connected to the same network
* M3U playlists for all torrents/folders containing audio/video files. To get the playlist URL - long click on the play icon

## Building

* Download the latest Android NDK from https://developer.android.com/ndk/downloads/
* Download the latest Android SDK or Android Studio from https://developer.android.com/studio/
* Extract the downloaded archives
* Export the environment variable ANDROID_NDK_ROOT pointing to the NDK directory

### Build dependencies

    $ git clone https://github.com/AndreyPavlenko/android-build.git
    $ export ANDROID_BUILD_ROOT="$PWD/android-build"
    $ cd "$ANDROID_BUILD_ROOT/packages/transmission"
    $ ./build.sh all

### Build the apks
    $ git clone https://github.com/AndreyPavlenko/transmissionbtc.git

Open the local.properties file in a text editor and enter the valid paths to ndk.dir, sdk.dir, depends and keystore, set the keystore properties.

    $ cd transmissionbtc
    $ gradle cleanAll assembleRelease
