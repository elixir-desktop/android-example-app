# TodoApp Android: An Android Sample App

This Android Studio project wraps the [Desktop Sample App](https://github.com/elixir-desktop/desktop-example-app) to run on an Android phone.

## How to build & run

1. Install [Android Studio](https://developer.android.com/studio) + NDK.

1. Go to "Files -> New -> Project from Version Control" and enter this URL: https://github.com/elixir-desktop/android-example-app/ 

1. Update the `run_mix` to activate the correct Erlang/Elixir version during build.

1. [Connect your Phone](https://developer.android.com/studio/run/device) to Android Studio

1. Start the App

## Known todos

### Update built-in Runtime

The current runtime that is precompiled and sits in assets/ folder is based on dev branch of OTP currently under
https://github.com/diodechain/otp/tree/diode/beta
Because the included OTP apps have different versions such as `crypto-5.0.3` you can only compile this project 
with the very same OTP version. You can probably build it with `kerl`. But I'll update the runtime to a newer stable
OTP build soon`(tm)` because all neccesary changes have been merged by the Erlang team already.

### Initial Startup could be faster

Running the app for the first time will extract the full Elixir & App runtime at start. On my Phone this takes around 10 seconds. After that a cold app startup takes ~3-4 seconds.

### Menus and other integration not yet available

This sample only launch the elixir app and shows it in an Android WebView. There is no integration yet with the Android Clipboard, sharing or other OS capabilities. They can though easily be added to the `Bridge.kt` file when needed.

##  Other notes

- The current sample is using __Android API 23__ and above

- The Erlang runtime is for ease of use embedded in this example git repository. The native runtimes for Android ARM, ARM64 and X86_64 and the exqlite nif are are generated using the [Desktop Runtime](https://github.com/elixir-desktop/runtimes) repository. 

- Android specific settings, icons and metadata are all contained in this Android Studio wrapper project. 

- `Bridge.kt` and the native library are doing most of the wrapping of the Elixir runtime. 

## Screenshots

![Icons](/icon.jpg?raw=true "App in Icon View")
![App](/app.png?raw=true "Running App")

## Architecture

![App](/android_elixir.png?raw=true "Architecture")

The Android App is initializing the Erlang VM and starting it up with a new environment variable `BRIDGE_PORT`. This environment variable is used by the `Bridge` project to connect to a local TCP server _inside the android app_. Through this new TCP communication channel all calls that usually would go to `wxWidgets` are now redirected. The Android side of things implements handling in `Bridge.kt`.  