package com.leepy0.aanavi

import android.app.Activity
import android.os.Bundle

/** 화면 없이 내비 URL만 호출하고 닫힌다. */
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_TARGET)?.let { Target.valueOf(it) } ?: Target.TMAP_GOAL
        target.launch(this, "알림 버튼(폰)")
        finish()
    }

    companion object {
        const val EXTRA_TARGET = "target"
    }
}
