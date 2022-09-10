package com.xyoye.player_component.ui.activities.player

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.bridge.PlayTaskBridge
import com.xyoye.common_component.config.DanmuConfig
import com.xyoye.common_component.config.PlayerConfig
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.config.SubtitleConfig
import com.xyoye.common_component.receiver.HeadsetBroadcastReceiver
import com.xyoye.common_component.receiver.PlayerReceiverListener
import com.xyoye.common_component.receiver.ScreenBroadcastReceiver
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.inter.ExtraSource
import com.xyoye.common_component.source.inter.GroupSource
import com.xyoye.common_component.source.inter.VideoSource
import com.xyoye.common_component.source.media.TorrentMediaSource
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.common_component.weight.dialog.CommonDialog
import com.xyoye.data_component.enums.*
import com.xyoye.player.controller.VideoController
import com.xyoye.player.info.PlayerInitializer
import com.xyoye.player_component.BR
import com.xyoye.player_component.R
import com.xyoye.player_component.databinding.ActivityPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import com.xyoye.player_component.utils.BatteryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Route(path = RouteTable.Player.PlayerCenter)
@AndroidEntryPoint
class PlayerActivity : BaseActivity<PlayerViewModel, ActivityPlayerBinding>(),
    PlayerReceiverListener {

    //锁屏广播
    private lateinit var screenLockReceiver: ScreenBroadcastReceiver

    //耳机广播
    private lateinit var headsetReceiver: HeadsetBroadcastReceiver

    private lateinit var videoController: VideoController

    private var videoSource: VideoSource? = null

    //电量管理
    private var batteryHelper = BatteryHelper()

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            PlayerViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_player

    override fun initStatusBar() {
        ImmersionBar.with(this)
            .fullScreen(true)
            .hideBar(BarHide.FLAG_HIDE_BAR)
            .init()
    }

    override fun initView() {
        ARouter.getInstance().inject(this)

        registerReceiver()

        initPlayer()

        initListener()

        initPlayerConfig()

        applyPlaySource(VideoSourceManager.getInstance().getSource())
    }

    override fun onPause() {
        dataBinding.danDanPlayer.pause()
        super.onPause()
    }

    override fun onDestroy() {
        beforePlayExit()
        unregisterReceiver()
        dataBinding.danDanPlayer.release()
        batteryHelper.release()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (dataBinding.danDanPlayer.onBackPressed()) {
            return
        }
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return dataBinding.danDanPlayer.onKeyDown(keyCode, event) or super.onKeyDown(keyCode, event)
    }

    override fun onScreenLocked() {

    }

    override fun onHeadsetRemoved() {
        dataBinding.danDanPlayer.pause()
    }

    private fun checkPlayParams(source: VideoSource?): Boolean {
        if (source == null || source.getVideoUrl().isEmpty()) {
            CommonDialog.Builder().run {
                content = "解析播放参数失败"
                addPositive("退出重试") {
                    it.dismiss()
                    finish()
                }
                build()
            }.show(this)
            return false
        }

        return true
    }

    private fun initListener() {

    }

    private fun initPlayer() {
        videoController = VideoController(this)
        dataBinding.danDanPlayer.setController(videoController)

        videoController.apply {
            setBatteryHelper(batteryHelper)

            //播放错误
            observerPlayError {
                showPlayErrorDialog()
            }
            //退出播放
            observerPlayExit {
                finish()
            }

            observerRestart {
                setResult(Activity.RESULT_OK)
                finish()
                ARouter.getInstance()
                    .build(RouteTable.Player.PlayerCenter)
                    .navigation()
                finish()
            }

            //弹幕屏蔽
            observerDanmuBlock(
                cloudBlock = viewModel.cloudDanmuBlockLiveData,
                add = { keyword, isRegex -> viewModel.addDanmuBlock(keyword, isRegex) },
                remove = { id -> viewModel.removeDanmuBlock(id) },
                queryAll = { viewModel.localDanmuBlockLiveData }
            )
        }
    }

    private fun applyPlaySource(newSource: VideoSource?) {
        dataBinding.danDanPlayer.pause()
        dataBinding.danDanPlayer.release()
        videoController.release()

        videoSource = newSource
        if (checkPlayParams(videoSource).not()) {
            return
        }
        updatePlayer(videoSource!!)
        afterInitPlayer()
    }

    private fun updatePlayer(source: VideoSource) {

        videoController.apply {
            setVideoTitle(source.getVideoTitle())
            setLastPosition(source.getCurrentPosition())
        }

        dataBinding.danDanPlayer.apply {
            setProgressObserver { position, duration ->
                viewModel.addPlayHistory(videoSource, position, duration)
            }
            setVideoSource(source)
            start()
        }

        if (source is ExtraSource) {
            videoController.setDanmuPath(source.getDanmuPath())
            // TODO: 2021/11/16 逻辑有问题，应该在Player实例化之前就可以执行
            videoController.setSubtitlePath(source.getSubtitlePath())
            //绑定资源
            videoController.observerBindSource { sourcePath, isSubtitle ->
                if (isSubtitle) {
                    source.setSubtitlePath(sourcePath)
                } else {
                    source.setDanmuPath(sourcePath)
                }
                viewModel.bindSource(sourcePath, source.getVideoUrl(), isSubtitle)
            }
            //发送弹幕
            videoController.observerSendDanmu {
                viewModel.sendDanmu(source.getEpisodeId(), source.getDanmuPath(), it)
            }
        }

        if (source is GroupSource) {
            videoController.setSwitchVideoSourceBlock {
                switchVideoSource(it)
            }
        }
    }

    private fun afterInitPlayer() {
        videoSource ?: return

        //设置本地视频文件的父文件夹，用于选取弹、字幕
        if (videoSource!!.getMediaType() == MediaType.LOCAL_STORAGE) {
            File(videoSource!!.getVideoUrl()).parentFile?.absolutePath?.let {
                PlayerInitializer.selectSourceDirectory = it
            }
        }
    }

    private fun registerReceiver() {
        screenLockReceiver = ScreenBroadcastReceiver(this)
        headsetReceiver = HeadsetBroadcastReceiver(this)
        registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(headsetReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        batteryHelper.registerReceiver(this)
    }

    private fun unregisterReceiver() {
        if (this::screenLockReceiver.isInitialized) {
            unregisterReceiver(screenLockReceiver)
        }
        if (this::headsetReceiver.isInitialized) {
            unregisterReceiver(headsetReceiver)
        }
        batteryHelper.unregisterReceiver(this)
    }

    private fun initPlayerConfig() {
        //播放器类型
        PlayerInitializer.playerType = PlayerType.valueOf(PlayerConfig.getUsePlayerType())
        //IJKPlayer像素格式
        PlayerInitializer.Player.pixelFormat =
            PixelFormat.valueOf(PlayerConfig.getUsePixelFormat())
        //IJKPlayer硬解码
        PlayerInitializer.Player.isMediaCodeCEnabled = PlayerConfig.isUseMediaCodeC()
        //IJKPlayer H265硬解码
        PlayerInitializer.Player.isMediaCodeCH265Enabled = PlayerConfig.isUseMediaCodeCH265()
        //IJKPlayer OpenSlEs
        PlayerInitializer.Player.isOpenSLESEnabled = PlayerConfig.isUseOpenSlEs()
        //是否使用SurfaceView
        PlayerInitializer.surfaceType =
            if (PlayerConfig.isUseSurfaceView()) SurfaceType.VIEW_SURFACE else SurfaceType.VIEW_TEXTURE
        //视频速度
        PlayerInitializer.Player.videoSpeed = PlayerConfig.getVideoSpeed()

        //VLCPlayer像素格式
        PlayerInitializer.Player.vlcPixelFormat =
            VLCPixelFormat.valueOf(PlayerConfig.getUseVLCPixelFormat())
        PlayerInitializer.Player.vlcHWDecode =
            VLCHWDecode.valueOf(PlayerConfig.getUseVLCHWDecoder())

        //弹幕配置
        PlayerInitializer.Danmu.size = DanmuConfig.getDanmuSize()
        PlayerInitializer.Danmu.speed = DanmuConfig.getDanmuSpeed()
        PlayerInitializer.Danmu.alpha = DanmuConfig.getDanmuAlpha()
        PlayerInitializer.Danmu.stoke = DanmuConfig.getDanmuStoke()
        PlayerInitializer.Danmu.topDanmu = DanmuConfig.isShowTopDanmu()
        PlayerInitializer.Danmu.mobileDanmu = DanmuConfig.isShowMobileDanmu()
        PlayerInitializer.Danmu.bottomDanmu = DanmuConfig.isShowBottomDanmu()
        PlayerInitializer.Danmu.maxLine = DanmuConfig.getDanmuMaxLine()
        PlayerInitializer.Danmu.maxNum = DanmuConfig.getDanmuMaxCount()
        PlayerInitializer.Danmu.cloudBlock = DanmuConfig.isCloudDanmuBlock()
        PlayerInitializer.Danmu.updateInChoreographer = DanmuConfig.isDanmuUpdateInChoreographer()

        //字幕配置
        PlayerInitializer.Subtitle.textSize = (40f * SubtitleConfig.getTextSize() / 100f).toInt()
        PlayerInitializer.Subtitle.strokeWidth =
            (10f * SubtitleConfig.getStrokeWidth() / 100f).toInt()
        PlayerInitializer.Subtitle.textColor = SubtitleConfig.getTextColor()
        PlayerInitializer.Subtitle.strokeColor = SubtitleConfig.getStrokeColor()
    }

    private fun showPlayErrorDialog() {
        val source = videoSource

        val tips = if (source is TorrentMediaSource) {
            val taskLog = PlayTaskBridge.getTaskLog(source.getPlayTaskId())
            "播放失败，资源已失效或暂时无法访问，请尝试切换资源$taskLog"
        } else {
            "播放失败，请尝试更改播放器设置，或者切换其它播放内核"
        }

        val builder = AlertDialog.Builder(this@PlayerActivity)
            .setTitle("错误")
            .setCancelable(false)
            .setMessage(tips)
            .setNegativeButton("退出播放") { dialog, _ ->
                dialog.dismiss()
                this@PlayerActivity.finish()
            }

        if (source is TorrentMediaSource) {
            builder.setPositiveButton("播放器设置") { dialog, _ ->
                dialog.dismiss()
                ARouter.getInstance()
                    .build(RouteTable.User.SettingPlayer)
                    .navigation()
                this@PlayerActivity.finish()
            }
        }

        builder.create().show()
    }

    private fun beforePlayExit() {
        val source = videoSource ?: return
        if (source is TorrentMediaSource) {
            PlayTaskBridge.sendTaskRemoveMsg(source.getPlayTaskId())
        }
    }

    private fun switchVideoSource(index: Int) {
        showLoading()
        dataBinding.danDanPlayer.pause()
        lifecycleScope.launch(Dispatchers.IO) {
            val source = videoSource
            var targetSource: VideoSource? = null
            if (source is GroupSource) {
                targetSource = source.indexSource(index)
            }
            if (targetSource == null) {
                ToastCenter.showOriginalToast("播放资源不存在")
                return@launch
            }
            withContext(Dispatchers.Main) {
                hideLoading()
                applyPlaySource(targetSource)
            }
        }
    }
}