package com.xyoye.common_component.source.factory


import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.config.DanmuConfig
import com.xyoye.common_component.config.SubtitleConfig
import com.xyoye.common_component.utils.DanmuUtilsModule
import com.xyoye.common_component.extension.toMd5String
import com.xyoye.common_component.source.base.VideoSourceFactory
import com.xyoye.common_component.source.media.LocalMediaSource
import com.xyoye.common_component.utils.PlayHistoryUtils
import com.xyoye.common_component.utils.SubtitleUtils
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.entity.VideoEntity
import com.xyoye.data_component.enums.MediaType


/**
 * Created by xyoye on 2022/1/12
 */
object LocalSourceFactory {


    suspend fun create(DanmuUtils: DanmuUtilsModule, builder: VideoSourceFactory.Builder): LocalMediaSource? {
        val videoSources = builder.videoSources.filterIsInstance<VideoEntity>()
        val video = videoSources.getOrNull(builder.index) ?: return null


        val uniqueKey = generateUniqueKey(videoSources[builder.index])
        val history = PlayHistoryUtils.getPlayHistory(uniqueKey, MediaType.LOCAL_STORAGE)

        val (episodeId, danmuPath) = getVideoDanmu(DanmuUtils, video, history)
        val subtitlePath = getVideoSubtitle(DanmuUtils, video, history)
        return LocalMediaSource(
            DanmuUtils,
            builder.index,
            videoSources,
            history?.videoPosition ?: 0,
            danmuPath,
            episodeId,
            subtitlePath,
            uniqueKey
        )
    }

    fun generateUniqueKey(entity: VideoEntity): String {
        return entity.filePath.toMd5String()
    }


    private fun getVideoDanmu(DanmuUtils: DanmuUtilsModule, video: VideoEntity, history: PlayHistoryEntity?): Pair<Int, String?> {
        //当前视频已绑定弹幕
        if (history?.danmuPath != null) {
            return Pair(history.episodeId, history.danmuPath)
        }
        //从本地找同名弹幕
        if (DanmuConfig.isAutoLoadSameNameDanmu()) {
            DanmuUtils.findLocalDanmuByVideo(video.filePath)?.let {
                return Pair(0, it)
            }
        }
        return Pair(0, null)
    }


    private suspend fun getVideoSubtitle(DanmuUtils: DanmuUtilsModule, video: VideoEntity, history: PlayHistoryEntity?): String? {
        //当前视频已绑定字幕
        if (history?.subtitlePath != null) {
            return history.subtitlePath
        }
        //自动加载本地同名字幕
        if (SubtitleConfig.isAutoLoadSameNameSubtitle()) {
            SubtitleUtils.findLocalSubtitleByVideo(video.filePath)?.let {
                return it
            }
        }

        if (SubtitleConfig.isAutoMatchSubtitle()) {
            SubtitleUtils.matchSubtitleSilence(DanmuUtils.Retrofit, video.filePath)?.let {
                return it
            }
        }
        return null
    }
}