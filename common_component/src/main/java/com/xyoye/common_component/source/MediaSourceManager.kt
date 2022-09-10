package com.xyoye.common_component.source

/**
 * Created by xyoye on 2021/11/14.
 */

class MediaSourceManager private constructor() {

    companion object {
        @JvmStatic
        fun getInstance() = Holder.instance
    }

    private object Holder {
        val instance = MediaSourceManager()
    }

    private var playSource: MediaSource? = null

    fun setSource(source: MediaSource) {
        playSource = source
    }

    fun getSource() = playSource
}