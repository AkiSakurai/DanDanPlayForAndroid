package com.xyoye.player_component.ui.activities.player

import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.RetrofitModule
import com.xyoye.common_component.network.request.httpRequest
import com.xyoye.common_component.source.inter.ExtraSource
import com.xyoye.common_component.source.inter.VideoSource
import com.xyoye.common_component.source.media.TorrentMediaSource
import com.xyoye.common_component.utils.DDLog
import com.xyoye.common_component.utils.DanmuUtilsModule
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.SendDanmuBean
import com.xyoye.data_component.data.SendDanmuData
import com.xyoye.data_component.entity.DanmuBlockEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import master.flame.danmaku.danmaku.model.BaseDanmaku
import java.math.BigDecimal
import java.util.*
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val DanmuUtils : DanmuUtilsModule,
    val Retrofit: RetrofitModule
)  : BaseViewModel() {

    val localDanmuBlockLiveData = DatabaseManager.instance.getDanmuBlockDao().getAll(false)
    val cloudDanmuBlockLiveData = DatabaseManager.instance.getDanmuBlockDao().getAll(true)

    fun addPlayHistory(source: VideoSource?, position: Long, duration: Long) {
        source ?: return

        GlobalScope.launch(context = Dispatchers.IO) {
            var sourceDanmuPath: String? = null
            var sourceEpisodeId = 0
            var sourceSubtitlePath: String? = null
            if (source is ExtraSource) {
                sourceDanmuPath = source.getDanmuPath()
                sourceEpisodeId = source.getEpisodeId()
                sourceSubtitlePath = source.getSubtitlePath()
            }

            var torrentPath: String? = null
            var torrentIndex = -1
            if (source is TorrentMediaSource) {
                torrentPath = source.getTorrentPath()
                torrentIndex = source.getTorrentIndex()
            }

            val history = PlayHistoryEntity(
                0,
                source.getVideoTitle(),
                source.getVideoUrl(),
                source.getMediaType(),
                position,
                duration,
                Date(),
                sourceDanmuPath,
                sourceEpisodeId,
                sourceSubtitlePath,
                torrentPath,
                torrentIndex,
                JsonHelper.toJson(source.getHttpHeader())
            )

            DatabaseManager.instance.getPlayHistoryDao()
                .insert(history)
        }
    }

    fun bindSource(sourcePath: String, videoPath: String, isSubtitle: Boolean) {
        viewModelScope.launch {
            if (isSubtitle) {
                DatabaseManager.instance.getVideoDao().updateSubtitle(
                    videoPath, sourcePath
                )
            } else {
                DatabaseManager.instance.getVideoDao().updateDanmu(
                    videoPath, sourcePath
                )
            }
        }
    }

    fun addDanmuBlock(keyword: String, isRegex: Boolean) {
        viewModelScope.launch {
            DatabaseManager.instance.getDanmuBlockDao().insert(
                DanmuBlockEntity(0, keyword, isRegex)
            )
        }
    }

    fun removeDanmuBlock(id: Int) {
        viewModelScope.launch {
            DatabaseManager.instance.getDanmuBlockDao().delete(id)
        }
    }

    fun sendDanmu(episodeId: Int, danmuPath: String?, sendDanmuBean: SendDanmuBean) {
        if (episodeId > 0) {
            sendDanmuToServer(sendDanmuBean, episodeId)
        }
        if (danmuPath != null) {
            writeDanmuToFile(sendDanmuBean, danmuPath)
        }
    }

    private fun sendDanmuToServer(sendDanmuBean: SendDanmuBean, episodeId: Int) {
        httpRequest<SendDanmuData>(viewModelScope) {

            api {
                val timeDecimal = BigDecimal(sendDanmuBean.position.toDouble() / 1000)
                    .setScale(2, BigDecimal.ROUND_HALF_UP)

                val mode = when {
                    sendDanmuBean.isScroll -> BaseDanmaku.TYPE_SCROLL_RL
                    sendDanmuBean.isTop -> BaseDanmaku.TYPE_FIX_TOP
                    else -> BaseDanmaku.TYPE_FIX_BOTTOM
                }
                val color = sendDanmuBean.color and 0x00FFFFFF

                val params = hashMapOf<String, String>()
                params["time"] = timeDecimal.toString()
                params["mode"] = mode.toString()
                params["color"] = color.toString()
                params["comment"] = sendDanmuBean.text
                Retrofit.service.sendDanmu(episodeId.toString(), params)
            }

            onSuccess {
                DDLog.i("发送弹幕成功")
            }

            onError {
                ToastCenter.showOriginalToast("发送弹幕失败\n x${it.code} ${it.msg}")
            }
        }
    }

    private fun writeDanmuToFile(sendDanmuBean: SendDanmuBean, danmuPath: String) {
        val time = BigDecimal(sendDanmuBean.position.toDouble() / 1000)
            .setScale(2, BigDecimal.ROUND_HALF_UP)
            .toString()

        val mode = when {
            sendDanmuBean.isScroll -> BaseDanmaku.TYPE_SCROLL_RL
            sendDanmuBean.isTop -> BaseDanmaku.TYPE_FIX_TOP
            else -> BaseDanmaku.TYPE_FIX_BOTTOM
        }

        val unixTime = (System.currentTimeMillis() / 1000f).toInt().toString()
        val color = sendDanmuBean.color and 0x00FFFFFF

        val danmuText = "<d p=\"$time,$mode,25,$color,$unixTime,0,0,0\">${sendDanmuBean.text}</d>"

        DanmuUtils.appendDanmu(danmuPath, danmuText)
    }
}