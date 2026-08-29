package com.mediaflow.data.ytdlp

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * yt-dlp-android 2.0.2 only maps a handful of CLI flags and never captures stdout,
 * so analysis flags such as --dump-single-json and --skip-download are ignored.
 * Options are applied through the embedded YoutubeDL Python API instead.
 */
internal object YtDlpRuntime {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.113 Safari/537.36"
    const val FILE_TIME_TOLERANCE_MS = 1_000L

    private val cwdLock = Any()
    @Volatile private var cwdInitialized = false

    fun analysisOptions(outputDirectory: File): JSONObject =
        baseOptions(outputDirectory, "analysis_%(id)s.%(ext)s")
            .put("skip_download", true)
            .put("simulate", true)
            .put("quiet", true)
            .put("no_warnings", true)

    fun downloadOptions(
        outputDirectory: File,
        outputTemplate: String,
        format: String? = null,
        referer: String? = null,
        mergeOutputFormat: String? = null,
    ): JSONObject {
        val opts = baseOptions(outputDirectory, outputTemplate)
            .put("skip_download", false)
            .put("simulate", false)
        format?.let { opts.put("format", it) }
        mergeOutputFormat?.let { opts.put("merge_output_format", it) }
        referer?.let { opts.getJSONObject("http_headers").put("Referer", it) }
        return opts
    }

    fun baseOptions(outputDirectory: File, outputTemplate: String): JSONObject {
        outputDirectory.mkdirs()
        val absoluteTemplate = absoluteOutputTemplate(outputDirectory, outputTemplate)
        val headers = JSONObject().put("User-Agent", USER_AGENT)
        val paths = JSONObject()
            .put("home", outputDirectory.absolutePath)
            .put("temp", outputDirectory.absolutePath)
        val youtubeArgs = JSONObject().put(
            "player_client",
            JSONArray().put("tv").put("web"),
        )
        return JSONObject()
            .put("outtmpl", absoluteTemplate)
            .put("paths", paths)
            .put("restrictfilenames", true)
            .put("nopart", true)
            .put("noplaylist", true)
            .put("cachedir", false)
            .put("writethumbnail", false)
            .put("writeinfojson", false)
            .put("check_formats", false)
            .put("retries", 3)
            .put("fragment_retries", 3)
            .put("socket_timeout", 30)
            .put("source_address", "0.0.0.0")
            .put("extractor_args", JSONObject().put("youtube", youtubeArgs))
            .put("http_headers", headers)
            .put("no_color", true)
    }

    fun absoluteOutputTemplate(outputDirectory: File, outputTemplate: String): String {
        val asFile = File(outputTemplate)
        return if (asFile.isAbsolute) asFile.absolutePath else File(outputDirectory, outputTemplate).absolutePath
    }

    fun ensureReady(context: Context) {
        val appContext = context.applicationContext
        YtDlp.init(appContext)
        synchronized(cwdLock) {
            if (cwdInitialized) return
            val cwd = File(appContext.filesDir, "yt_dlp_cwd").apply { mkdirs() }
            Python.getInstance().getModule("os").callAttr("chdir", cwd.absolutePath)
            cwdInitialized = true
        }
    }

    fun extractJson(context: Context, url: String, outputDirectory: File): String {
        ensureReady(context)
        outputDirectory.mkdirs()
        val globals = runScript(
            """
            import json, os, yt_dlp
            os.makedirs(out_dir, exist_ok=True)
            opts = json.loads(opts_json)
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
                result = json.dumps(ydl.sanitize_info(info))
            """.trimIndent(),
            mapOf(
                "url" to url,
                "out_dir" to outputDirectory.absolutePath,
                "opts_json" to analysisOptions(outputDirectory).toString(),
            ),
        )
        return globals.callAttr("__getitem__", "result").toString()
    }

    fun download(
        context: Context,
        url: String,
        options: JSONObject,
        outputDirectory: File,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        ensureReady(context)
        outputDirectory.mkdirs()
        runScript(
            """
            import json, os, yt_dlp
            os.makedirs(out_dir, exist_ok=True)
            opts = json.loads(opts_json)
            def hook(d):
                if listener is None or d.get('status') != 'downloading':
                    return
                try:
                    total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
                    downloaded = d.get('downloaded_bytes') or 0
                    if total:
                        listener.onProgress(100.0 * downloaded / total)
                    else:
                        raw = str(d.get('_percent_str') or '0').replace('%', '').strip()
                        listener.onProgress(float(raw.split()[-1]))
                except Exception:
                    pass
            opts['progress_hooks'] = [hook]
            with yt_dlp.YoutubeDL(opts) as ydl:
                ydl.download([url])
            """.trimIndent(),
            mapOf(
                "url" to url,
                "out_dir" to outputDirectory.absolutePath,
                "opts_json" to options.toString(),
                "listener" to onProgress?.let(::ProgressBridge),
            ),
        )
    }

    fun findOutputFile(
        directory: File,
        startedAt: Long,
        expectedBaseName: String? = null,
        prefix: String? = null,
    ): File? {
        val candidates = directory.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0L }
            .filter { file ->
                val name = file.name
                !name.endsWith(".part", ignoreCase = true) &&
                    !name.endsWith(".ytdl", ignoreCase = true) &&
                    !name.endsWith(".temp", ignoreCase = true)
            }
            .filter { it.lastModified() >= startedAt - FILE_TIME_TOLERANCE_MS }
        if (candidates.isEmpty()) return null

        val expected = expectedBaseName?.takeIf { it.isNotBlank() }
        if (expected != null) {
            val restricted = restrictFileName(expected)
            (
                candidates.firstOrNull { it.nameWithoutExtension == expected }
                    ?: candidates.firstOrNull { it.nameWithoutExtension.startsWith(expected) }
                    ?: candidates.firstOrNull { it.nameWithoutExtension == restricted }
                    ?: candidates.firstOrNull { it.nameWithoutExtension.startsWith(restricted) }
            )?.let { return it }
        }

        if (!prefix.isNullOrBlank()) {
            candidates.filter { it.name.startsWith(prefix) }
                .maxByOrNull { it.lastModified() }
                ?.let { return it }
        }

        return candidates.maxByOrNull { it.lastModified() }
    }

    fun restrictFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "mediaflow_download" }

    private fun runScript(script: String, bindings: Map<String, Any?>): PyObject {
        val py = Python.getInstance()
        val none = py.getBuiltins()["None"]
        val globals = py.getBuiltins().callAttr("dict")
        bindings.forEach { (key, value) ->
            globals.callAttr("__setitem__", key, value ?: none)
        }
        py.getBuiltins().callAttr("exec", script, globals)
        return globals
    }

    class ProgressBridge(private val emit: (Float) -> Unit) {
        fun onProgress(percent: Double) {
            emit(percent.toFloat().coerceIn(0f, 100f))
        }
    }
}
