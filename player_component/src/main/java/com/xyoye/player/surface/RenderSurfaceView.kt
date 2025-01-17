package com.xyoye.player.surface

import android.content.Context
import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import com.xyoye.data_component.enums.VideoScreenScale
import com.xyoye.player.kernel.inter.AbstractVideoPlayer
import com.xyoye.player.utils.RenderMeasureHelper

/**
 * Created by xyoye on 2020/11/3.
 */

class RenderSurfaceView(context: Context) : SurfaceView(context), InterSurfaceView {

    private val mMeasureHelper = RenderMeasureHelper()
    private lateinit var mVideoPlayer: AbstractVideoPlayer

    private val mSurfaceCallback = object : SurfaceHolder.Callback {
        var hasSurfaceDestroyedBefore = false

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (this@RenderSurfaceView::mVideoPlayer.isInitialized ) {
                hasSurfaceDestroyedBefore = true
                mVideoPlayer.setSurface(null)
            }
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            if (this@RenderSurfaceView::mVideoPlayer.isInitialized) {
                mVideoPlayer.setSurface(holder.surface)
                if(hasSurfaceDestroyedBefore) {
                    hasSurfaceDestroyedBefore = false
                    mVideoPlayer.seekTo(mVideoPlayer.getCurrentPosition())
                }
            }
        }
    }

    init {
        holder.addCallback(mSurfaceCallback)
    }

    override fun attachPlayer(player: AbstractVideoPlayer) {
        mVideoPlayer = player
    }

    override fun setVideoSize(videoWidth: Int, videoHeight: Int) {
        if (videoWidth > 0 && videoHeight > 0) {
            mMeasureHelper.mVideoWidth = videoWidth
            mMeasureHelper.mVideoHeight = videoHeight
            requestLayout()
        }
    }

    override fun setVideoRotation(degree: Int) {
        mMeasureHelper.mVideoDegree = degree
        if (rotation != degree.toFloat()) {
            rotation = degree.toFloat()
            requestLayout()
        }
    }

    override fun setScaleType(screenScale: VideoScreenScale) {
        mMeasureHelper.mScreenScale = screenScale
        requestLayout()
    }

    override fun setGravity(gravity: Int) {
        val width = layoutParams.width
        val height = layoutParams.height
        layoutParams = FrameLayout.LayoutParams(
            width,
            height,
            gravity
        )
    }

    override fun getView() = this

    override fun doScreenShot(): Bitmap? {
        return drawingCache
    }

    override fun release() {
        holder.removeCallback(mSurfaceCallback)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredSize = mMeasureHelper.doMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredSize[0], measuredSize[1])
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        holder.removeCallback(mSurfaceCallback)
    }
}