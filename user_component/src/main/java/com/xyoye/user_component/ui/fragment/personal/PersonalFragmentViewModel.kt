package com.xyoye.user_component.ui.fragment.personal

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.network.RetrofitModule
import com.xyoye.common_component.network.request.httpRequest
import com.xyoye.data_component.data.CloudHistoryListData
import com.xyoye.data_component.data.FollowAnimeData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Created by xyoye on 2020/7/28.
 */

@HiltViewModel
class PersonalFragmentViewModel @Inject constructor(
    val Retrofit: RetrofitModule
)  : BaseViewModel() {
    var followData: FollowAnimeData? = null
    var historyData: CloudHistoryListData? = null

    val relationLiveData = MutableLiveData<Pair<Int, Int>>()

    fun getUserRelationInfo() {
        httpRequest<Pair<Int, Int>>(viewModelScope) {
            api {
                followData = Retrofit.service.getFollowAnime()
                historyData = Retrofit.service.getCloudHistory()
                Pair(
                    followData?.favorites?.size ?: 0,
                    historyData?.playHistoryAnimes?.size ?: 0
                )
            }

            onSuccess {
                relationLiveData.postValue(it)
            }
        }
    }
}