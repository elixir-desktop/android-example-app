# TodoApp Android: An Android Sample App

This Android Studio project wraps the [Desktop Sample App](https://github.com/elixir-desktop/desktop-example-app) to run on an Android phone.

## Runtime Notes

The pre-built Erlang runtime for Android ARM/ARM64/x86 is embedded in this example git repository. These native runtime files include Erlang OTP and the exqlite nif to use SQLite on the mobile. These runtimes are generated using the CI of the [Desktop Runtime](https://github.com/elixir-desktop/runtimes) repository.

Because Erlang OTP has many native hooks for networking and cryptographics the Erlang version used to compile your App must match the pre-built binary release that is embedded. In this example that is Erlang OTP 25.0.4. This sample is shipping with a `.tool-versions` file that `asdf` will automatically use to automate this requirement. 

## How to build & run

1. Install [Android Studio](https://developer.android.com/studio) + NDK.
1. Install git, npm, asdf

    ```
    sudo apt install git npm curl
    git clone https://github.com/asdf-vm/asdf.git ~/.asdf --branch v0.10.2
    echo ". $HOME/.asdf/asdf.sh" >> ~/.bashrc
    echo ". $HOME/.asdf/completions/asdf.bash" >> ~/.bashrc
    . $HOME/.asdf/asdf.sh
    ```

1. Install Erlang-OTP (with openssl) in the same version 25.0.4 as the bundled runtime edition:

    ```
    asdf install erlang 25.0.4
    asdf install elixir 1.13.4-otp-25
    ```

1. Go to "Files -> New -> Project from Version Control" and enter this URL: https://github.com/elixir-desktop/android-example-app/ 

1. Start the App


## Customize app name and branding

Update these places with your package name:

1) App name in [strings.xml](app/src/main/res/values/strings.xml#L2) and [settings.gradle](settings.gradle)
1) Package names in [Bridge.kt:1](app/src/main/java/io/elixirdesktop/example/Bridge.kt#L1) and [MainActivity.kt:1](app/src/main/java/io/elixirdesktop/example/MainActivity.kt#L1) (rename `package io.elixirdesktop.example` -> `com.yourapp.name` or use the Android Studios refactor tool)
1) App icon: [ic_launcher_foreground.xml](app/src/main/res/drawable-v24/ic_launcher_foreground.xml) and [ic_launcher-playstore.png](app/src/main/ic_launcher-playstore.png) 
1) App colors: [colors.xml](app/src/main/res/values/colors.xml) and launcher background [ic_launcher_background.xml](app/src/main/res/values/ic_launcher_background.xml)

## Known todos

### Initial Startup could be faster

Running the app for the first time will extract the full Elixir & App runtime at start. On my Phone this takes around 10 seconds. After that a cold app startup takes ~3-4 seconds.

### Menus and other integration not yet available

This sample only launch the elixir app and shows it in an Android WebView. There is no integration yet with the Android Clipboard, sharing or other OS capabilities. They can though easily be added to the `Bridge.kt` file when needed.

##  Other notes

- Android specific settings, icons and metadata are all contained in this Android Studio wrapper project. 

- `Bridge.kt` and the native library are doing most of the wrapping of the Elixir runtime. 

## Screenshots

![Icons](/icon.jpg?raw=true "App in Icon View")
![App](/app.png?raw=true "Running App")

## Architecture

![App](/android_elixir.png?raw=true "Architecture")

The Android App is initializing the Erlang VM and starting it up with a new environment variable `BRIDGE_PORT`. This environment variable is used by the `Bridge` project to connect to a local TCP server _inside the android app_. Through this new TCP communication channel all calls that usually would go to `wxWidgets` are now redirected. The Android side of things implements handling in `Bridge.kt`.  
