package com.leepy0.aanavi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.location.LocationManagerCompat
import java.util.Locale
import kotlin.concurrent.thread

/** 휴대폰 화면: 목적지 지정, 휴대폰에서 직접 내비 호출, 업데이트, 시험 기록 보기 */
class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var current: TextView
    private lateinit var name: EditText
    private lateinit var lat: EditText
    private lateinit var lng: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        val version = packageManager.getPackageInfo(packageName, 0).versionName
        current = TextView(this).apply { textSize = 18f }
        val query = EditText(this).apply {
            hint = "주소나 장소 이름 (예: 판교역)"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> search(text.toString()); true }
        }
        val num = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        name = field("목적지 이름", InputType.TYPE_CLASS_TEXT)
        lat = field("위도", num)
        lng = field("경도", num)
        log = TextView(this).apply { setTextIsSelectable(true) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(TextView(this@MainActivity).apply { text = "현재 버전 $version" })
            addView(button("업데이트 확인") { checkUpdate(manual = true) })

            addView(current)
            addView(query)
            addView(button("주소 검색") { search(query.text.toString()) })
            addView(button("지금 위치를 목적지로") { useCurrentLocation() })
            addView(TextView(this@MainActivity).apply { text = "직접 입력" })
            addView(name); addView(lat); addView(lng)
            addView(button("직접 입력한 값 저장") {
                val la = lat.text.toString().toDoubleOrNull()
                val ln = lng.text.toString().toDoubleOrNull()
                if (la == null || ln == null) toast("위도·경도를 숫자로 입력하세요") else setDest(Dest(name.text.toString(), la, ln))
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
        if (!Updater.autoChecked) {
            Updater.autoChecked = true
            checkUpdate(manual = false)
        }
    }

    private fun refresh() {
        val d = Dest.load(this)
        current.text = "목적지: ${d.name}"
        name.setText(d.name); lat.setText(d.lat.toString()); lng.setText(d.lng.toString())
        log.text = TestLog.read(this)
    }

    private fun setDest(d: Dest) {
        Dest.save(this, d)
        TestLog.add(this, "목적지 저장: $d")
        refresh()
    }

    /** 검색 결과가 여러 개면 고르게 한다. Geocoder는 블로킹이라 백그라운드에서 부른다. */
    private fun search(q: String) {
        if (q.isBlank()) return toast("주소나 장소 이름을 입력하세요")
        if (!Geocoder.isPresent()) return toast("이 휴대폰은 주소 검색을 지원하지 않습니다")
        thread {
            val found = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.KOREA).getFromLocationName(q, 5).orEmpty()
            }
            runOnUiThread {
                val list = found.getOrElse { return@runOnUiThread toast("검색 실패: ${it.message}") }
                when (list.size) {
                    0 -> toast("검색 결과가 없습니다")
                    1 -> setDest(list[0].toDest(q))
                    else -> AlertDialog.Builder(this)
                        .setTitle("어느 곳인가요?")
                        .setItems(list.map { it.label() }.toTypedArray()) { _, i -> setDest(list[i].toDest(q)) }
                        .show()
                }
            }
        }
    }

    private fun Address.label(): String = getAddressLine(0) ?: featureName ?: "$latitude, $longitude"

    private fun Address.toDest(q: String) = Dest(q.trim(), latitude, longitude)

    private fun useCurrentLocation() {
        val perm = Manifest.permission.ACCESS_FINE_LOCATION
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        toast("위치 찾는 중…")
        LocationManagerCompat.getCurrentLocation(lm, provider, android.os.CancellationSignal(), mainExecutor) { loc: android.location.Location? ->
            if (loc == null) toast("위치를 찾지 못했습니다. 위치 설정을 확인하세요")
            else setDest(Dest("지금 위치", loc.latitude, loc.longitude))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) useCurrentLocation()
    }

    /** 자동 확인은 새 버전이 있을 때만 알린다. 수동 확인은 결과를 모두 알린다. */
    private fun checkUpdate(manual: Boolean) {
        if (manual) toast("업데이트 확인 중…")
        thread {
            val result = runCatching { Updater.check(Updater.currentCode(this)) }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                result.onSuccess { info ->
                    when {
                        info != null -> AlertDialog.Builder(this)
                            .setTitle("새 버전 ${info.version}")
                            .setMessage(info.notes.ifBlank { "변경 내용 없음" })
                            .setPositiveButton("설치") { _, _ -> installUpdate(info) }
                            .setNegativeButton("나중에", null)
                            .show()
                        manual -> toast("최신 버전입니다")
                    }
                }.onFailure { if (manual) toast("업데이트 확인 실패: ${it.message}") }
            }
        }
    }

    private fun installUpdate(info: Updater.Info) {
        if (!Updater.canInstall(this)) {
            toast("이 앱의 '알 수 없는 앱 설치'를 허용한 뒤 다시 확인하세요")
            startActivity(Updater.installPermissionIntent(this))
            return
        }
        toast("${info.version} 받는 중…")
        thread {
            val apk = runCatching { Updater.download(this, info) }
            runOnUiThread {
                apk.onSuccess { Updater.install(this, it) }
                    .onFailure { toast("업데이트 받기 실패: ${it.message}") }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun field(hint: String, type: Int) =
        EditText(this).apply { this.hint = hint; inputType = type }

    private fun button(text: String, onClick: () -> Unit) =
        Button(this).apply { this.text = text; setOnClickListener { onClick() } }

    private companion object {
        const val REQ_LOCATION = 1
    }
}
