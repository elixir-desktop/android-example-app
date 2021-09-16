package io.elixirdesktop.example

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONArray
import org.tukaani.xz.XZInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import java.io.*
import java.util.*
import java.util.zip.ZipInputStream


class Bridge(private val applicationContext : Context, private var webview : WebView) {
    private val server = ServerSocket(0)
    private var lastURL = String()
    private val assets = applicationContext.assets.list("")

    init {
        Os.setenv("BRIDGE_PORT", server.localPort.toString(), false);
        // not really the home directory, but persistent between app upgrades
        Os.setenv("HOME", applicationContext.filesDir.absolutePath, false);

        setWebView(webview)

        thread(start = true) {
            while (true) {
                val socket = server.accept()
                println("Client connected: ${socket.inetAddress.hostAddress}")
                thread { handle(socket) }
            }
        }


        // The possible values are armeabi, armeabi-v7a, arm64-v8a, x86, x86_64, mips, mips64.
        var prefix = ""
        for (abi in Build.SUPPORTED_ABIS) {
            when (abi) {
                "arm64-v8a", "armeabi-v7a", "x86_64" -> {
                    prefix = abi
                    break
                }
                "armeabi" -> {
                    prefix = "armeabi-v7a"
                    break
                }
                else -> continue
            }
        }

        if (prefix == "") {
            throw Exception("Could not find any supported ABI")
        }

        val runtime = "$prefix-runtime"
        Log.d("RUNTIME", runtime)

        thread(start = true) {
            val packageInfo = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)

            val nativeDir = packageInfo.applicationInfo.nativeLibraryDir
            val lastUpdateTime: Long = packageInfo.lastUpdateTime / 1000

            val releaseDir = applicationContext.filesDir.absolutePath + "/build-${lastUpdateTime}"
            var binDir = "$releaseDir/bin"

            Os.setenv("BINDIR", binDir, false);
            Os.setenv("LIBERLANG", "$nativeDir/liberlang.so", false);
            val doneFile = File("$releaseDir/done")


            if (!doneFile.exists()) {

                // Creating symlinks for binaries
                // https://github.com/JeromeDeBretagne/erlanglauncher/issues/2
                File(binDir).mkdirs()
                for (file in File(nativeDir).list()) {
                    if (file.startsWith("lib__")) {
                        var name = File(file).name
                        name = name.substring(5, name.length - 3)
                        Log.d("BIN", "$nativeDir/$file -> $binDir/$name")
                        Os.symlink("$nativeDir/$file", "$binDir/$name")
                    }
                }

                if (unpackAsset(releaseDir, "app") &&
                        unpackAsset(releaseDir, runtime)) {
                    for (lib in File("$releaseDir/lib").list()) {
                        val parts = lib.split("-")
                        val name = parts[0]

                        val nif = "$prefix-nif-$name"
                        unpackAsset("$releaseDir/lib/$lib/priv", nif)
                    }

                    doneFile.writeText("$lastUpdateTime")
                }
            }

            if (!doneFile.exists()) {
                Log.e("ERROR", "Failed to extract runtime")
                throw Exception("Failed to extract runtime")
            } else {
                var logdir = applicationContext.getExternalFilesDir("")?.path
                if (logdir == null) {
                    logdir = applicationContext.filesDir.absolutePath;
                }
                Log.d("ERLANG", "Starting beam...")
                val ret = startErlang(releaseDir, logdir!!)
                Log.d("ERLANG", ret)
                if (ret != "ok") {
                    throw Exception(ret)
                }
            }
        }
    }


    private fun unpackAsset(releaseDir: String, assetName: String): Boolean {
        assets!!
        if (assets!!.contains("$assetName.zip.xz")) {
            val input = BufferedInputStream(applicationContext.assets.open("$assetName.zip.xz"))
            return unpackZip(releaseDir, XZInputStream(input))
        }
        if (assets!!.contains("$assetName.zip")) {
            val input = BufferedInputStream(applicationContext.assets.open("$assetName.zip"))
            return unpackZip(releaseDir, input)
        }
        return false
    }

    private fun unpackZip(releaseDir: String, inputStream: InputStream): Boolean {
        Log.d("RELEASEDIR", releaseDir)
        File(releaseDir).mkdirs()
        try
        {
            val zis = ZipInputStream(BufferedInputStream(inputStream))
            val buffer = ByteArray(1024)

            var ze =  zis.nextEntry
            while (ze != null)
            {
                val filename = ze.name

                // Need to create directories if not exists, or
                // it will generate an Exception...
                var fullpath = "$releaseDir/$filename"
                if (ze.isDirectory) {
                    Log.d("DIR", fullpath)
                    File(fullpath).mkdirs()
                    zis.closeEntry()
                    ze =  zis.nextEntry
                    continue
                }

                Log.d("FILE", fullpath)
                val fout = FileOutputStream(fullpath)
                var count = zis.read(buffer)
                while (count != -1) {
                    fout.write(buffer, 0, count)
                    count = zis.read(buffer)
                }

                fout.close()
                zis.closeEntry()
                ze = zis.nextEntry
            }

            zis.close()
        }
        catch(e: IOException)
        {
            e.printStackTrace()
            return false
        }

        return true
    }

    fun setWebView(_webview: WebView) {
        webview = _webview
        val settings = webview.settings
        settings.javaScriptEnabled = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        settings.useWideViewPort = true
        // enable Web Storage: localStorage, sessionStorage
        settings.domStorageEnabled = true
        // webview.webViewClient = WebViewClient()
        if (lastURL.isNotBlank()) {
            webview.post { webview.loadUrl(lastURL) }
        }
    }


    fun getLocalPort(): Int {
        return server.localPort;
    }

    private fun handle(socket: Socket) {
        val reader = DataInputStream(BufferedInputStream((socket.getInputStream())))
        val writer = DataOutputStream(socket.getOutputStream())
        val ref = ByteArray(8);

        while (true) {
            val length = reader.readInt();
            reader.readFully(ref);
            var data = ByteArray(length - ref.size);
            reader.readFully(data)

            println("Parsing:: ${String(data)}")

            val json = JSONArray(String(data))

            //val module = json.getString(0);
            val method = json.getString(1);
            val args = json.getJSONArray(2);

            if (method == ":loadURL") {
                lastURL = args.getString(1)
                webview.post { webview.loadUrl(lastURL) }
            }

            var response = ref
            response += if (method == ":getOsDescription") {
                val info = "Android ${Build.DEVICE} ${Build.BRAND} ${Build.VERSION.BASE_OS} ${
                    Build.SUPPORTED_ABIS.joinToString(",")
                }"
                stringToList(info).toByteArray()
            } else if (method == ":getCanonicalName") {
                val primaryLocale = getCurrentLocale(applicationContext)
                var locale = "${primaryLocale.language}_${primaryLocale.country}"
                stringToList(locale).toByteArray()

            } else {
                "use_mock".toByteArray()
            }
            writer.writeInt(response.size)
            writer.write(response)
        }
    }

    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            context.resources.configuration.locale
        }
    }

    private fun stringToList(str : String): String {
        val numbers = str.toByteArray().map { it.toInt().toString() }
        return "[${numbers.joinToString(",")}]"
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun startErlang(releaseDir: String, logdir: String): String

    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
