package com.leepy0.aanavi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.leepy0.aanavi.car.NaviCarAppService

/**
 * AA 화면 heads-up 팝업. 탭 경로를 세 가지로 나눠 시험한다.
 * - 본문 탭: ACTION_NAVIGATE geo: → AA의 기본 내비(마지막에 쓴 내비)
 * - 버튼 1: 휴대폰 Activity로 TMAP URL 호출
 * - 버튼 2: 이 앱의 차 화면을 연 뒤 거기서 TMAP URL 호출
 */
object AaNotifier {
    private const val CHANNEL = "suggest"
    private const val ID = 1

    fun post(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "목적지 추천", NotificationManager.IMPORTANCE_HIGH)
        )
        val d = Dest.load(ctx)

        val navigate = CarPendingIntent.getCarApp(
            ctx, 1, Intent(CarContext.ACTION_NAVIGATE, d.geo), 0
        )
        val phoneTmap = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, LaunchActivity::class.java).putExtra(LaunchActivity.EXTRA_TARGET, Target.TMAP_RGO.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val viaCarApp = CarPendingIntent.getCarApp(
            ctx, 3,
            Intent(Intent.ACTION_VIEW)
                .setComponent(ComponentName(ctx, NaviCarAppService::class.java))
                .putExtra(LaunchActivity.EXTRA_TARGET, Target.TMAP_RGO.name),
            0,
        )

        val title = "${d.name}(으)로 갈까요?"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_nav)
            .setContentTitle(title)
            .setContentText("본문 탭 = 기본 내비로 안내")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setContentTitle(title)
                    .setContentText("본문 탭 = 기본 내비로 안내")
                    .setContentIntent(navigate)
                    .addAction(R.drawable.ic_nav, "TMAP(폰)", phoneTmap)
                    .addAction(R.drawable.ic_nav, "TMAP(차앱)", viaCarApp)
                    .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                    .build()
            )

        try {
            CarNotificationManager.from(ctx).notify(ID, n)
            TestLog.add(ctx, "AA 알림 게시")
        } catch (e: SecurityException) {
            TestLog.add(ctx, "알림 권한 없음: ${e.message}")
        }
    }
}
