package com.xyoye.anime_component.ui.activities.anime_follow

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.network.RetrofitModule
import com.xyoye.common_component.network.request.httpRequest
import com.xyoye.data_component.data.FollowAnimeData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AnimeFollowViewModel @Inject constructor(
    val Retrofit: RetrofitModule
): BaseViewModel() {
    val followLiveData = MutableLiveData<FollowAnimeData>()

    fun getUserFollow() {
        httpRequest<FollowAnimeData>(viewModelScope) {
            onStart { showLoading() }

            api {
                Retrofit.service.getFollowAnime()
            }

            onError { showNetworkError(it) }

            onSuccess {
                followLiveData.postValue(it)
            }

            onComplete { hideLoading() }
        }
    }
}