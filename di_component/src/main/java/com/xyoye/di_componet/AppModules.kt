package com.xyoye.di_componet

import com.xyoye.common_component.network.DefaultRetrofit
import com.xyoye.common_component.network.RetrofitModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModules {
    @Provides fun MakeRetrofit() : RetrofitModule
    {
        return RetrofitModule(
            DefaultRetrofit.service,
            DefaultRetrofit.resService,
            DefaultRetrofit.extService,
            DefaultRetrofit.torrentService,
            DefaultRetrofit.remoteService,
        )
    }
}