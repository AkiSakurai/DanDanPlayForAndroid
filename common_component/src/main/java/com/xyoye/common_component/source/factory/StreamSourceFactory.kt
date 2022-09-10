package com.xyoye.common_component.source.factory

import com.xyoye.common_component.extension.toMd5String
import com.xyoye.common_component.source.base.VideoSourceFactory
import com.xyoye.common_component.source.media.StreamMediaSource
import com.xyoye.common_component.utils.DanmuUtilsModule
import com.xyoye.common_component.utils.PlayHistoryUtils


/**
 * Created by xyoye on 2022/1/12
 */
object StreamSourceFactory {

    suspend fun create(DanmuUtils: DanmuUtilsModule, builder: VideoSourceFactory.Builder): StreamMediaSource? {
        val videoSources = builder.videoSources.filterIsInstance<String>()
        val videoUrl = videoSources.getOrNull(builder.index) ?: return null

        val uniqueKey = generateUniqueKey(videoUrl)
        val history = PlayHistoryUtils.getPlayHistory(uniqueKey, builder.mediaType)

        return StreamMediaSource(
            DanmuUtils,
            builder.index,
            videoSources,
            builder.httpHeaders,
            history?.videoPosition ?: 0L,
            history?.danmuPath,
            history?.episodeId ?: 0,
            history?.subtitlePath,
            uniqueKey
        )
    }

    fun generateUniqueKey(videoUrl: String): String {
        return videoUrl.toMd5String()
    }
}