package com.leepy0.aanavi

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 휴대폰 화면: 목적지 입력, 휴대폰에서 직접 내비 호출, 시험 기록 보기 */
class MainActivity : Activity() {
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        val d = Dest.load(this)
        val name = field("목적지 이름", d.name, InputType.TYPE_CLASS_TEXT)
        val num = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val lat = field("위도", d.lat.toString(), num)
        val lng = field("경도", d.lng.toString(), num)
        log = TextView(this).apply { setTextIsSelectable(true) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(name); addView(lat); addView(lng)
            addView(button("목적지 저장") {
                Dest.save(this@MainActivity, Dest(name.text.toString(), lat.text.toString().toDouble(), lng.text.toString().toDouble()))
                TestLog.add(this@MainActivity, "목적지 저장: ${Dest.load(this@MainActivity)}")
                refresh()
            })
            Target.entries.forEach { t -> addView(button("${t.label} 휴대폰에서 호출") { t.launch(this@MainActivity, "휴대폰"); refresh() }) }
            addView(button("AA 알림 띄우기") { AaNotifier.post(this@MainActivity); refresh() })
            addView(button("기록 지우기") { TestLog.clear(this@MainActivity); refresh() })
            addView(log)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        log.text = TestLog.read(this)
    }

    private fun field(hint: String, value: String, type: Int) =
        EditText(this).apply { this.hint = hint; setText(value); inputType = type }

    private fun button(text: String, onClick: () -> Unit) =
        Button(this).apply { this.text = text; setOnClickListener { onClick() } }
}
