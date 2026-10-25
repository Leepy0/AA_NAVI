package com.leepy0.aanavi

import android.app.Application
import androidx.car.app.connection.CarConnection

class App : Application() {
    private var lastType = -1

    override fun onCreate() {
        super.onCreate()
        // shortcut: 앱 프로세스가 살아 있을 때만 감지한다. 실차 시험 전에 휴대폰에서 앱을 한 번 열어 둘 것.
        CarConnection(this).type.observeForever { type ->
            TestLog.add(this, "CarConnection type=$type")
            if (type == CarConnection.CONNECTION_TYPE_PROJECTION && lastType != type) AaNotifier.post(this)
            lastType = type
        }
    }
}
