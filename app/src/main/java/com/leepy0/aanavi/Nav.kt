package com.leepy0.aanavi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 시험용 목적지. 휴대폰 화면에서 바꾸면 모든 경로가 같은 값을 쓴다. */
data class Dest(val name: String, val lat: Double, val lng: Double) {
    val geo: Uri get() = Uri.parse("geo:$lat,$lng")

    companion object {
        private const val PREFS = "dest"

        fun load(ctx: Context): Dest {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Dest(
                p.getString("name", "강남역")!!,
                p.getString("lat", "37.4979")!!.toDouble(),
                p.getString("lng", "127.0276")!!.toDouble(),
            )
        }

        fun save(ctx: Context, d: Dest) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", d.name).putString("lat", d.lat.toString()).putString("lng", d.lng.toString())
                .apply()
        }
    }
}

/** 휴대폰 쪽에서 내비 앱을 여는 방법들. 어느 것이 AA 화면에 확인 없이 안내를 띄우는지가 시험 대상. */
enum class Target(val label: String) {
    TMAP_RGO("TMAP rGoX"),
    TMAP_GOAL("TMAP goalx"),
    NAVER("네이버 nmap"),
    GEO("geo: (선택창)");

    fun intent(d: Dest): Intent {
        val n = Uri.encode(d.name)
        val uri = when (this) {
            // TMAP 공식 문서가 없어 알려진 두 형식을 모두 시험한다
            TMAP_RGO -> "tmap://route?rGoName=$n&rGoX=${d.lng}&rGoY=${d.lat}"
            TMAP_GOAL -> "tmap://route?goalname=$n&goalx=${d.lng}&goaly=${d.lat}"
            NAVER -> "nmap://navigation?dlat=${d.lat}&dlng=${d.lng}&dname=$n&appname=com.leepy0.aanavi"
            GEO -> "geo:${d.lat},${d.lng}?q=${d.lat},${d.lng}($n)"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 실패해도 앱이 죽지 않게 하고 결과를 기록만 한다. */
    fun launch(ctx: Context, from: String) {
        val i = intent(Dest.load(ctx))
        try {
            ctx.startActivity(i)
            TestLog.add(ctx, "[$from] $label 호출 성공: ${i.data}")
        } catch (e: Exception) {
            TestLog.add(ctx, "[$from] $label 실패: ${e.javaClass.simpleName} ${e.message}")
        }
    }
}

/** 차에서 본 결과를 나중에 휴대폰 화면에서 확인하려고 파일에 남긴다. */
object TestLog {
    private const val TAG = "AANAVI"

    private fun file(ctx: Context) = File(ctx.filesDir, "log.txt")

    fun add(ctx: Context, msg: String) {
        Log.i(TAG, msg)
        val t = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        file(ctx).appendText("$t $msg\n")
    }

    fun read(ctx: Context): String = file(ctx).takeIf { it.exists() }?.readText().orEmpty()

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
