package com.leepy0.aanavi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * GitHub Releases 자동 업데이트. PhotoCull과 같은 릴리스 형식(.github/workflows/android.yml → scripts/release_manifest.py).
 * 확인: github.com/<repo>/releases/latest/download/AA_NAVI.json (api.github.com은 쓰지 않음)
 * 받기: SHA-256이 매니페스트와 맞을 때만 설치. 설치: Android 시스템 설치 화면에서 사용자가 확인.
 * 블로킹 함수는 백그라운드 스레드에서 부른다.
 */
object Updater {
    private const val REPO = "Leepy0/AA_NAVI"
    private const val MANIFEST_URL = "https://github.com/$REPO/releases/latest/download/AA_NAVI.json"
    private const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    private const val MAX_MANIFEST = 64 * 1024
    private const val MAX_APK = 100L * 1024 * 1024

    data class Info(val version: String, val code: Int, val url: String, val sha256: String, val notes: String)

    /** 앱을 연 뒤 한 번만 자동 확인한다. */
    var autoChecked = false

    fun currentCode(ctx: Context): Int =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()

    /** 더 새 버전이 있으면 Info, 없거나 릴리스가 아직 없으면 null. 실패는 IOException. */
    fun check(currentCode: Int): Info? {
        val conn = open(MANIFEST_URL)
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val j = JSONObject(conn.inputStream.use { readLimited(it, MAX_MANIFEST) }.toString(Charsets.UTF_8))
            val version = j.optString("version")
            val code = j.optInt("versionCode").takeIf { it > 0 } ?: codeOf(version) ?: throw IOException("버전 없음")
            if (code <= currentCode) return null
            // 다른 곳의 APK를 받지 않게 이 저장소 릴리스 주소만 허용한다.
            val url = j.optString("url").takeIf { it.startsWith(DOWNLOAD_PREFIX) } ?: throw IOException("잘못된 주소")
            val sha = normalizeSha256(j.optString("sha256")) ?: throw IOException("체크섬 없음")
            return Info(version, code, url, sha, j.optString("notes"))
        } finally {
            conn.disconnect()
        }
    }

    /** cacheDir/update에 받고 SHA-256을 확인한다. 맞지 않으면 파일을 지우고 IOException. */
    fun download(ctx: Context, info: Info): File {
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val partial = File(dir, "AA_NAVI.apk.partial")
        val target = File(dir, "AA_NAVI-${info.code}.apk")
        val conn = open(info.url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            conn.inputStream.use { input ->
                partial.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_APK) throw IOException("파일이 너무 큼")
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != info.sha256) throw IOException("체크섬 불일치")
            if (!partial.renameTo(target)) throw IOException("파일 저장 실패")
            return target
        } catch (e: IOException) {
            partial.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** 시스템 설치 화면을 연다. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    /** "0.1.0" → 100, "v1.2.3" → 10203. app/build.gradle.kts와 같은 규칙. 해석할 수 없으면 null */
    fun codeOf(version: String): Int? {
        val parts = version.trim().removePrefix("v").split('.')
        if (parts.size != 3) return null
        val (major, minor, patch) = parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        if (minor >= 100 || patch >= 100) return null
        return (major * 10000 + minor * 100 + patch).takeIf { it > 0 }
    }

    /** "<hex>" 또는 sha256sum 형식에서 64자리 hex를 소문자로. 형식이 틀리면 null */
    fun normalizeSha256(raw: String?): String? {
        val token = raw?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase() ?: return null
        return token.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            // github.com → objects.githubusercontent.com 리다이렉트(https → https)는 기본으로 따라간다.
            connectTimeout = 10_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("User-Agent", "AA_NAVI")
        }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) throw IOException("응답이 너무 큼")
            out.write(buf, 0, n)
        }
    }
}
