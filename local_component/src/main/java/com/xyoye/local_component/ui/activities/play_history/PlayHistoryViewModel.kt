package com.xyoye.local_component.ui.activities.play_history

import androidx.databinding.ObservableBoolean
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.media.HistoryMediaSource
import com.xyoye.common_component.source.media.TorrentMediaSource
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayHistoryViewModel : BaseViewModel() {

    val showAddButton = ObservableBoolean()
    val isEditMode = ObservableBoolean()

    lateinit var playHistoryLiveData: LiveData<MutableList<PlayHistoryEntity>>
    val playLiveData = MutableLiveData<Any>()

    fun initHistoryType(mediaType: MediaType) {
        playHistoryLiveData = if (mediaType == MediaType.OTHER_STORAGE) {
            DatabaseManager.instance.getPlayHistoryDao().getAll()
        } else {
            DatabaseManager.instance.getPlayHistoryDao().getSingleMediaType(mediaType)
        }
    }

    fun removeHistory(historyList: MutableList<PlayHistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val historyDao = DatabaseManager.instance.getPlayHistoryDao()
            historyList.forEach {
                historyDao.delete(it.url, it.mediaType)
            }
        }
    }

    fun openHistory(history: PlayHistoryEntity) {
        viewModelScope.launch {
            showLoading()
            val mediaSource = if (
                history.mediaType == MediaType.MAGNET_LINK && history.torrentPath != null
            ) {
                TorrentMediaSource.build(history.torrentIndex, history.torrentPath!!)
            } else {
                HistoryMediaSource(history)
            }
            hideLoading()

            if (mediaSource == null) {
                ToastCenter.showError("播放失败，无法打开播放资源")
                return@launch
            }

            VideoSourceManager.getInstance().setSource(mediaSource)
            playLiveData.postValue(Any())
        }
    }
}