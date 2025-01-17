package com.xyoye.stream_component.ui.activities.smb_file

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.extension.filterHiddenFile
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.base.VideoSourceFactory
import com.xyoye.common_component.source.factory.SmbSourceFactory
import com.xyoye.common_component.utils.*
import com.xyoye.common_component.utils.server.SMBPlayServer
import com.xyoye.common_component.utils.smb.SMBException
import com.xyoye.common_component.utils.smb.SMBFile
import com.xyoye.common_component.utils.smb.v2.SMBJManager
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.FilePathBean
import com.xyoye.data_component.bean.StorageFileBean
import com.xyoye.data_component.entity.MediaLibraryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmbFileViewModel @Inject constructor(
    val DanmuUtils : DanmuUtilsModule,
)  : BaseViewModel() {
    private val showHiddenFile = AppConfig.isShowHiddenFile()


    val fileLiveData = MutableLiveData<List<StorageFileBean>>()
    val pathLiveData = MutableLiveData<MutableList<FilePathBean>>()
    val playLiveData = MutableLiveData<Any>()

    private var openedDirectoryList = mutableListOf<String>()
    private val curDirectoryFiles = mutableListOf<SMBFile>()

    /**
     * 初始化SMB，展开根目录
     */
    fun initFtp(serverData: MediaLibraryEntity) {
        showLoading()
        SMBJManager.getInstance().initConfig(
            serverData.url,
            serverData.account,
            serverData.password,
            serverData.isAnonymous
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SMBJManager.getInstance().connect()
                hideLoading()
            } catch (e: SMBException) {
                e.printStackTrace()
                hideLoading()
            }

            val rootPath = serverData.smbSharePath ?: ""

            //打开根目录
            openChildDirectory(rootPath)
        }
    }

    /**
     * 打开子目录
     */
    fun openChildDirectory(dirName: String) {
        //新增当前目录
        openedDirectoryList.add(dirName)
        //合并为目录地址
        val dirPath = getOpenedDirPath()
        updatePath()
        //获取目录文件
        showLoading()
        listDirectory(dirPath)
    }

    /**
     * 打开第position层已展开的目录
     */
    fun openPositionDirectory(position: Int) {
        //仅支持打开当前已打开目录的父目录
        if (position >= openedDirectoryList.size)
            return

        //获取目标位置目录列表，目标位置 == +1
        openedDirectoryList = openedDirectoryList.subList(0, position + 1)
        //合并为目录地址
        val dirPath = getOpenedDirPath()
        updatePath()

        //获取目录文件
        showLoading()
        listDirectory(dirPath)
    }

    /**
     * 刷新当前目录
     */
    fun refreshDirectory() {
        //合并为目录地址
        val dirPath = getOpenedDirPath()

        //获取目录文件
        listDirectory(dirPath)
    }

    fun refreshDirectoryWithHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val smbFiles = curDirectoryFiles
                .filter { it.isDirectory || isVideoFile(it.name) }
                .map {
                    if (it.isDirectory) {
                        return@map StorageFileBean(true, it.name, it.name)
                    }
                    val uniqueKey = SmbSourceFactory.generateUniqueKey(getOpenedDirPath(), it)
                    val history = DatabaseManager.instance
                        .getPlayHistoryDao()
                        .getPlayHistory(uniqueKey, MediaType.SMB_SERVER)
                    StorageFileBean(
                        false,
                        it.name,
                        it.name,
                        history?.danmuPath,
                        history?.subtitlePath,
                        history?.videoPosition ?: 0L,
                        history?.videoDuration ?: 0L,
                        uniqueKey,
                        lastPlayTime = history?.playTime
                    )
                }
            fileLiveData.postValue(smbFiles)
        }
    }

    /**
     * 打开父目录
     */
    fun openParentDirectory(): Boolean {
        //当前已在根目录，无法打开父目录
        if (openedDirectoryList.size <= 1)
            return false

        //移除当前目录
        openedDirectoryList.removeLast()

        //合并为目录地址
        val dirPath = getOpenedDirPath()
        updatePath()

        //获取目录文件
        showLoading()
        listDirectory(dirPath)
        return true
    }

    /**
     * 打开视频文件
     */
    fun openVideoFile(uniqueKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playServer = SMBPlayServer.getInstance()
                if (!playServer.isAlive) playServer.start()
            } catch (e: Exception) {
                ToastCenter.showError("启动SMB播放服务失败，请重试\n${e.message}")
                return@launch
            }

            val videoSources = curDirectoryFiles.filter { isVideoFile(it.name) }
            val index = videoSources.indexOfFirst {
                SmbSourceFactory.generateUniqueKey(getOpenedDirPath(), it) == uniqueKey
            }
            if (videoSources.isNullOrEmpty() || index < 0) {
                ToastCenter.showError("播放失败，不支持播放的资源")
                return@launch
            }

            //同文件夹内的弹幕和字幕资源
            val extSources =
                curDirectoryFiles.filter { isDanmuFile(it.name) || isSubtitleFile(it.name) }

            showLoading()

            val mediaSource = VideoSourceFactory.Builder()
                .setVideoSources(videoSources)
                .setExtraSource(extSources)
                .setRootPath(getOpenedDirPath())
                .setIndex(index)
                .create(DanmuUtils, MediaType.SMB_SERVER)
            hideLoading()

            if (mediaSource == null) {
                ToastCenter.showError("播放失败，找不到播放资源")
                return@launch
            }
            VideoSourceManager.getInstance().setSource(mediaSource)
            playLiveData.postValue(Any())
        }
    }

    /**
     * 关闭播放文件流
     */
    fun closeStream() {
        viewModelScope.launch(Dispatchers.IO) {
            SMBPlayServer.getInstance().stop()
            SMBJManager.getInstance().closeStream()
        }
    }

    /**
     * 关闭SMB连接
     */
    fun closeSMB() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                SMBPlayServer.getInstance().closeIO()
                SMBJManager.getInstance().disConnect()
            } catch (e: SMBException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 获取文件夹内所有文件
     */
    private fun listDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val childFiles = SMBJManager.getInstance().listFiles(path)
                    .filterHiddenFile { it.name }
                    .sortedWith(FileComparator(
                        value = { it.name },
                        isDirectory = { it.isDirectory }
                    ))
                curDirectoryFiles.clear()
                curDirectoryFiles.addAll(childFiles)

                refreshDirectoryWithHistory()
                hideLoading()
            } catch (e: SMBException) {
                curDirectoryFiles.clear()
                fileLiveData.postValue(mutableListOf())
                e.printStackTrace()
                hideLoading()
                ToastCenter.showError("获取文件列表失败：${e.message}")
                return@launch
            }
        }
    }

    /**
     * 更新界面上的目录列表
     */
    private fun updatePath() {
        val pathList = mutableListOf<FilePathBean>()
        openedDirectoryList.forEach {
            pathList.add(FilePathBean(it, "", false))
        }
        pathList.first().name = "根目录"
        pathList.last().isOpened = true
        pathLiveData.postValue(pathList)
    }

    /**
     * 获取当前展开目录的路径
     */
    private fun getOpenedDirPath(): String {
        //合并为目录地址
        val dirPath = StringBuilder()
        for ((index, name) in openedDirectoryList.withIndex()) {
            if (index == 0 && name == "") {
                continue
            }
            dirPath.append("\\").append(name)
        }
        return dirPath.toString()
    }
}