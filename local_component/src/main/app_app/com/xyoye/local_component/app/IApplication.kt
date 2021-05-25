package com.xyoye.local_component.app
import android.content.Context
import androidx.multidex.MultiDex
import com.xyoye.common_component.base.app.BaseApplication
import dagger.hilt.android.HiltAndroidApp

/**
 * Created by xyoye on 2020/7/27.
 */
@HiltAndroidApp
class IApplication : BaseApplication(){

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}