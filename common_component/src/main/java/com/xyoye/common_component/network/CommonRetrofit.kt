package com.xyoye.common_component.network

import com.xyoye.common_component.network.service.*

data class RetrofitModule (
    val service: RetrofitService,
    val resService: ResRetrofitService,
     val extService: ExtRetrofitService,
     val torrentService: TorrentRetrofitService,
     val remoteService: RemoteService,
)