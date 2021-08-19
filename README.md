# TodoApp Android: An Android Sample App

This Android Studio project wraps the [Desktop Sample App](https://github.com/elixir-desktop/desktop-example-app) to run on an Android phone.

## How to build & run

1. Install [Android Studio](https://developer.android.com/studio) + NDK. 

2. Go to "Files -> New -> Project from Version Control -> GitHub"

3. [Connect your Phone](https://developer.android.com/studio/run/device) to Android Studio

4. Start the App

## Known todos

### The x86_64 emulator does not work 

So you've got to run on your phone or you the slow ARM emulator. If you have an idea how to fix the issue. Please send a pull request to the runtime project.

### Initial Startup time is slow

Running the app for the first time it will extract the full Elixir & App runtime at start. On my Phone this takes anywhere from 10-20 seconds. After that the startup is releatively quick.

### Menus and other integration not yet available

This sample only launch the elixir app and shows it in an Android WebView. There is no integration yet with the Android Clipboard, sharing or other OS capabilities. They can though easily be added to the `Bridge.kt` file when needed.

##  Other notes

- The current sample is using __Android API 23__ and above

- The Erlang runtime is for ease of use embedded in this example git repository. The native runtimes for Android ARM, ARM64 and X86_64 and the exqlite nif are are generated using the [Desktop Runtime](https://github.com/elixir-desktop/runtime) repository. 

- Android specific settings, icons and metadata are all contained in this Android Studio wrapper project. 

- `Bridge.kt` and the native library are doing most of the wrapping of the Elixir runtime. 

## Screenshots

![Icons](/icon.jpg?raw=true "App in Icon View")
![App](/app.jpg?raw=true "Running App")

## Architecture

![App](/android_elixir.png?raw=true "Architecture")

The Android App is initializing the Erlang VM and starting it up with a new environment variable `BRIDGE_PORT`. This environment variable is used by the `Bridge` project to connect to a local TCP server _inside the android app_. Through this new TCP communication channel all calls that usually would go to `wxWidgets` are now redirected. The Android side of things implements handling in `Bridge.kt`.  