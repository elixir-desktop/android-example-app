package io.elixirdesktop.example

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import java.io.*
import java.util.zip.ZipInputStream


class Bridge(private val applicationContext : Context, private var webview : WebView) {
    private val server = ServerSocket(0)
    private var lastURL = String()

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
                "arm64-v8a", "armeabi-v7a" -> { //, "x86_64" -> {
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

        val runtime = "$prefix-runtime.zip"
        Log.d("RUNTIME", runtime)
        val lastUpdateTime: Long = applicationContext.packageManager
            .getPackageInfo(applicationContext.packageName, 0).lastUpdateTime / 1000

        thread(start = true) {
            val assets = applicationContext.assets.list("")
            val releasedir = applicationContext.filesDir.absolutePath + "/build-${lastUpdateTime}"
            val donefile = File("$releasedir/done")
            if (!donefile.exists()) {
                if (unpackZip(releasedir, applicationContext.assets.open("app.zip")) &&
                        unpackZip(releasedir, applicationContext.assets.open(runtime))) {
                    for (lib in File("$releasedir/lib").list()) {
                        val parts = lib.split("-")
                        val name = parts[0]

                        val nif = "$prefix-nif-$name.zip"
                        if (assets!!.contains(nif)) {
                            unpackZip("$releasedir/lib/$lib/priv", applicationContext.assets.open(nif))
                        }
                    }

                    donefile.writeText("$lastUpdateTime")
                }
            }

            if (!donefile.exists()) {
                Log.e("ERROR", "Failed to extract runtime")
                throw Exception("Failed to extract runtime")
            } else {
                var logdir = applicationContext.getExternalFilesDir("")?.path
                if (logdir == null) {
                    logdir = applicationContext.filesDir.absolutePath;
                }
                Log.d("ERLANG", "Starting beam...")
                val ret = startErlang(releasedir, logdir!!)
                Log.d("ERLANG", ret)
                if (ret != "ok") {
                    throw Exception(ret)
                }
            }
        }
    }


    private fun unpackZip(releasedir: String, inputStream: InputStream): Boolean
    {
        Log.d("RELEASEDIR", releasedir)
        File(releasedir).mkdirs()
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
                var fullpath = "$releasedir/$filename"
                if (ze.isDirectory) {
                    Log.d("DIR", fullpath)
                    File(fullpath).mkdirs()
                    zis.closeEntry()
                    ze =  zis.nextEntry
                    continue
                }

                Log.d("FILE", fullpath)

                var isBinary = filename.contains("/bin/") || filename.endsWith(".so")
                val fout = FileOutputStream(fullpath)

                var count = zis.read(buffer)
                while (count != -1) {
                    fout.write(buffer, 0, count)
                    count = zis.read(buffer)
                }

                fout.close()


                if (isBinary) {
                    File(fullpath).setExecutable(true)
                }

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

            val response = ref + "use_mock".toByteArray()
            writer.writeInt(response.size)
            writer.write(response)
        }
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