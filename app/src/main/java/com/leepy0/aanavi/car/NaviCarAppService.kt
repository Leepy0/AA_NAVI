package com.leepy0.aanavi.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import com.leepy0.aanavi.AaNotifier
import com.leepy0.aanavi.Dest
import com.leepy0.aanavi.LaunchActivity
import com.leepy0.aanavi.Target
import com.leepy0.aanavi.TestLog

class NaviCarAppService : CarAppService() {
    // shortcut: 시험용이라 모든 호스트 허용. 배포판은 호스트 허용 목록으로 바꿀 것.
    override fun createHostValidator() = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession() = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            handle(intent)
            return TestScreen(carContext)
        }

        override fun onNewIntent(intent: Intent) = handle(intent)

        /** 알림의 "TMAP(차앱)" 버튼으로 열렸을 때 */
        private fun handle(intent: Intent) {
            intent.getStringExtra(LaunchActivity.EXTRA_TARGET)?.let {
                Target.valueOf(it).launch(carContext, "알림 버튼(차앱)")
            }
        }
    }
}

/** AA 화면에서 각 경로를 직접 눌러 보는 목록 */
private class TestScreen(ctx: CarContext) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val d = Dest.load(carContext)
        val items = ItemList.Builder()
            .addItem(row("geo: 탐색 요청 (기본 내비)", "startCarApp ACTION_NAVIGATE") { navigate(d) })
        listOf(Target.TMAP_RGO, Target.TMAP_GOAL, Target.NAVER).forEach { t ->
            items.addItem(row("${t.label} (폰에서 호출)", "차앱에서 휴대폰 startActivity") { t.launch(carContext, "차 화면") })
        }
        items.addItem(row("AA 알림 다시 띄우기", "heads-up 팝업 시험") { AaNotifier.post(carContext) })
        return ListTemplate.Builder()
            .setTitle("목적지: ${d.name}")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .build()
    }

    private fun navigate(d: Dest) {
        try {
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, d.geo))
            TestLog.add(carContext, "[차 화면] ACTION_NAVIGATE 성공: ${d.geo}")
        } catch (e: Exception) {
            TestLog.add(carContext, "[차 화면] ACTION_NAVIGATE 실패: ${e.javaClass.simpleName} ${e.message}")
            CarToast.makeText(carContext, "실패: ${e.javaClass.simpleName}", CarToast.LENGTH_LONG).show()
        }
    }

    private fun row(title: String, text: String, onClick: () -> Unit) =
        Row.Builder().setTitle(title).addText(text).setOnClickListener(onClick).build()
}
