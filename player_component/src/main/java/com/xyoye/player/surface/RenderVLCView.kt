package com.xyoye.player.surface

import android.content.Context
import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xyoye.data_component.enums.VideoScreenScale
import com.xyoye.player.kernel.impl.vlc.VlcVideoPlayer
import com.xyoye.player.kernel.inter.AbstractVideoPlayer
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Created by xyoye on 2021/4/12.
 */

class RenderVLCView(
    context: Context
) : InterSurfaceView {

    private lateinit var mVideoPlayer: VlcVideoPlayer

    private val vlcLayout = VLCVideoLayout(context)

    private val surfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        var hasSurfaceDestroyedBefore = false

        override fun surfaceCreated(holder: SurfaceHolder?) {
            if(hasSurfaceDestroyedBefore) {
                mVideoPlayer.attachRenderView(vlcLayout)
                hasSurfaceDestroyedBefore = false
                if(!mVideoPlayer.isPlaying()) {
                    mVideoPlayer.seekTo(mVideoPlayer.getCurrentPosition())
                }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            hasSurfaceDestroyedBefore = true
        }
    }

    override fun attachPlayer(player: AbstractVideoPlayer) {
        mVideoPlayer  = (player as VlcVideoPlayer)
        player.attachRenderView(vlcLayout)
        val surface = vlcLayout.findViewById<SurfaceView>(
            org.videolan.R.id.surface_video)
        surface?.holder?.addCallback(surfaceCallback)
    }

    override fun setVideoSize(videoWidth: Int, videoHeight: Int) {

    }

    override fun setVideoRotation(degree: Int) {

    }

    override fun setScaleType(screenScale: VideoScreenScale) {
        val scale = when(screenScale){
            VideoScreenScale.SCREEN_SCALE_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
            VideoScreenScale.SCREEN_SCALE_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
            VideoScreenScale.SCREEN_SCALE_CENTER_CROP -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            VideoScreenScale.SCREEN_SCALE_ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
            VideoScreenScale.SCREEN_SCALE_MATCH_PARENT -> MediaPlayer.ScaleType.SURFACE_FILL
            VideoScreenScale.SCREEN_SCALE_DEFAULT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
        mVideoPlayer.setScale(scale)
    }

    override fun setGravity(gravity: Int) {
        val videoSurface = vlcLayout.findViewById<View>(org.videolan.R.id.surface_video)
        val width = videoSurface.layoutParams.width
        val height =  videoSurface.layoutParams.height
        videoSurface.layoutParams = FrameLayout.LayoutParams(
            width,
            height,
            gravity
        )
    }

    override fun getView(): View {
        return vlcLayout
    }

    override fun doScreenShot(): Bitmap? {
        return null
    }

    override fun release() {

    }
}