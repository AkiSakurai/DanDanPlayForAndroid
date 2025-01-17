package com.xyoye.common_component.source.factory

import android.text.TextUtils
import com.xyoye.common_component.config.DanmuConfig
import com.xyoye.common_component.config.SubtitleConfig
import com.xyoye.common_component.extension.toMd5String
import com.xyoye.common_component.source.base.VideoSourceFactory
import com.xyoye.common_component.source.media.RemoteMediaSource
import com.xyoye.common_component.utils.*
import com.xyoye.data_component.data.remote.RemoteVideoData
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.MediaType


/**
 * Created by xyoye on 2022/1/12
 */
object RemoteSourceFactory {

    suspend fun create(DanmuUtils: DanmuUtilsModule, builder: VideoSourceFactory.Builder): RemoteMediaSource? {
        val videoSources = builder.videoSources.filterIsInstance<RemoteVideoData>()
        val videoData = videoSources.getOrNull(builder.index) ?: return null

        val playUrl = RemoteHelper.getInstance().buildVideoUrl(videoData.Id)

        val uniqueKey = generateUniqueKey(videoSources[builder.index])
        val historyEntity = PlayHistoryUtils.getPlayHistory(uniqueKey, MediaType.REMOTE_STORAGE)

        val position = getHistoryPosition(historyEntity)

        val (episodeId, danmuPath) = getVideoDanmu(DanmuUtils, historyEntity, videoData)
        val subtitlePath = getVideoSubtitle(DanmuUtils, historyEntity, videoData)

        return RemoteMediaSource(
            DanmuUtils,
            builder.index,
            videoSources,
            playUrl,
            position,
            danmuPath,
            episodeId,
            subtitlePath,
            uniqueKey
        )
    }

    fun generateUniqueKey(videoData: RemoteVideoData): String {
        var uniqueKey = videoData.Hash
        if (TextUtils.isEmpty(uniqueKey))
            uniqueKey = videoData.absolutePath
        return uniqueKey.toMd5String()
    }

    private fun getHistoryPosition(entity: PlayHistoryEntity?): Long {
        return entity?.videoPosition ?: 0L
    }


    private suspend fun getVideoDanmu(
        DanmuUtils: DanmuUtilsModule,
        history: PlayHistoryEntity?,
        videoData: RemoteVideoData
    ): Pair<Int, String?> {
        //从播放记录读取弹幕
        if (TextUtils.isEmpty(history?.danmuPath).not()) {
            return Pair(history!!.episodeId, history.danmuPath)
        }

        //自动匹配同文件夹内同名弹幕
        if (DanmuConfig.isAutoLoadSameNameDanmu()) {
            try {
                val danmuResponseBody = DanmuUtils.Retrofit.remoteService.downloadDanmu(videoData.Hash)
                val videoName = videoData.getEpisodeName()
                val danmuFileName = getFileNameNoExtension(videoName) + ".xml"
                val danmuPath = DanmuUtils.saveDanmu(
                    danmuFileName,
                    danmuResponseBody.byteStream()
                )
                return Pair(0, danmuPath)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        return Pair(0, null)
    }


    private suspend fun getVideoSubtitle(
        DanmuUtils: DanmuUtilsModule,
        history: PlayHistoryEntity?,
        videoData: RemoteVideoData
    ): String? {
        //从播放记录读取弹幕
        if (TextUtils.isEmpty(history?.subtitlePath).not()) {
            return history!!.subtitlePath
        }

        //自动匹配同文件夹内同名字幕
        if (SubtitleConfig.isAutoLoadSameNameSubtitle()) {
            try {
                val subtitleData = DanmuUtils.Retrofit.remoteService.searchSubtitle(videoData.Id)
                if (subtitleData.subtitles.isNotEmpty()) {
                    val subtitleName = subtitleData.subtitles[0].fileName
                    val subtitleResponseBody =
                        DanmuUtils.Retrofit.remoteService.downloadSubtitle(videoData.Id, subtitleName)
                    return SubtitleUtils.saveSubtitle(
                        subtitleName,
                        subtitleResponseBody.byteStream()
                    )
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        return null
    }
}