package com.xyoye.player_component.source

import com.xyoye.common_component.source.base.BaseVideoSource
import com.xyoye.common_component.source.inter.ExtraSource
import com.xyoye.common_component.source.inter.GroupSource
import com.xyoye.common_component.source.inter.VideoSource

class RestartSource(val source: BaseVideoSource, private val overriddenPosition: Long)
    : BaseVideoSource(source.getGroupSize(), listOf<BaseVideoSource>()),
        GroupSource by source, VideoSource by source, ExtraSource by source {

    override fun getCurrentPosition(): Long {
        return overriddenPosition
    }

    companion object {
        fun getRestartSource(source: BaseVideoSource, currentPosition: Long): RestartSource {
            val baseSource = if(source is RestartSource) source.source else source
            return RestartSource(baseSource, currentPosition)
        }
    }
}