package com.xyoye.data_component.bean

import com.xyoye.data_component.enums.VideoScreenScale

/**
 * Created by xyoye on 2020/11/16.
 */

data class VideoGravityBean(
    val screenGravity: Int,

    val gravityName: String,

    var isChecked: Boolean = false
)