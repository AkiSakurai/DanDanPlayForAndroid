package com.xyoye.local_component.ui.activities.local_media

import android.net.Uri
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.config.DanmuConfig
import com.xyoye.common_component.config.SubtitleConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.extension.deduplication
import com.xyoye.common_component.extension.isInvalid
import com.xyoye.common_component.extension.toFile
import com.xyoye.common_component.network.RetrofitModule
import com.xyoye.common_component.resolver.MediaResolver
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.base.VideoSourceFactory
import com.xyoye.common_component.source.factory.LocalSourceFactory
import com.xyoye.common_component.utils.*
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.StorageFileBean
import com.xyoye.data_component.entity.VideoEntity
import com.xyoye.data_component.enums.FileSortType
import com.xyoye.data_component.enums.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@HiltViewModel
class LocalMediaViewModel @Inject constructor(
    val DanmuUtils : DanmuUtilsModule,
    val Retrofit: RetrofitModule
): BaseViewModel() {
    //当前是否在根目录
    val inRootFolder = ObservableBoolean()

    //当前是否为搜索状态
    val inSearchState = ObservableBoolean()

    //当前打开的目录名
    val currentFolderName = ObservableField<String>()

    //当前目录路径
    private val currentFolderPath = ObservableField<String>()

    val refreshLiveData = MutableLiveData<Boolean>()
    val refreshEnableLiveData = MutableLiveData<Boolean>()
    val fileLiveData = MutableLiveData<List<StorageFileBean>>()

    val sortedFileLiveData = MediatorLiveData<List<StorageFileBean>>()

    val sortLiveData = MutableLiveData<FileSortType>()


    val playLiveData = MutableLiveData<Any>()

    private val curDirectories = mutableListOf<StorageFileBean>()
    private val curDirectoryFiles = mutableListOf<VideoEntity>()

    private var searchJob: Job? = null

    init {
        sortLiveData.value = FileSortType.valueOf(AppConfig.getLocalFileSortType())

        val updateLiveDate = { videoData: List<StorageFileBean>?, sortType: FileSortType? ->
            val sorted = when(sortType) {
                FileSortType.DATE -> {
                    videoData?.sortedByDescending { getFileDate(it.filePath) }
                }
                else -> {
                    videoData?.sortedWith(FileComparator(
                        value = { getFileName(it.filePath) },
                        isDirectory = { false }
                    ))
                }
            }
            if(sorted != null) {
                sortedFileLiveData.postValue(sorted)
            }
        }

        sortedFileLiveData.addSource(fileLiveData) {
            updateLiveDate(it, sortLiveData.value)
        }

        sortedFileLiveData.addSource(sortLiveData) {
            updateLiveDate(fileLiveData.value, it)
        }

    }
    //记录最近一次本地播放的live data
    val lastPlayHistory = DatabaseManager.instance.getPlayHistoryDao()
        .gitLastPlayLiveData(MediaType.LOCAL_STORAGE)

    fun fastPlay() {
        viewModelScope.launch {
            val lastHistory = lastPlayHistory.value
            if (lastHistory == null) {
                ToastCenter.showError("无最近播放记录")
                return@launch
            }

            playItem(lastHistory.url)
        }
    }

    fun listRoot(deepRefresh: Boolean = false) {
        inRootFolder.set(true)
        inSearchState.set(false)

        if (deepRefresh.not() && curDirectories.isNotEmpty()) {
            val lastPlayFolder = lastPlayHistory.value?.url?.run {
                getDirPath(this)
            }
            curDirectories.forEach {
                it.isLastPlay = it.filePath == lastPlayFolder
            }
            fileLiveData.postValue(curDirectories)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            refreshEnableLiveData.postValue(true)
            //深度刷新所有视频数据
            val refreshSuccess = refreshSystemVideo()
            if (refreshSuccess) {
                val folderData = DatabaseManager.instance.getVideoDao().getFolderByFilter()

                val lastPlayFolder = lastPlayHistory.value?.url?.run {
                    getDirPath(this)
                }
                val directories = folderData.sortedWith(FileComparator(
                    value = { getFolderName(it.folderPath) },
                    isDirectory = { true }
                )).map {
                    StorageFileBean(
                        true,
                        it.folderPath,
                        getFolderName(it.folderPath),
                        childFileCount = it.fileCount,
                        isLastPlay = it.folderPath == lastPlayFolder
                    )
                }
                curDirectories.clear()
                curDirectories.addAll(directories)

                fileLiveData.postValue(curDirectories)
                refreshLiveData.postValue(true)
            } else {
                refreshLiveData.postValue(false)
            }
        }
    }

    fun openDirectory(folderPath: String) {
        inRootFolder.set(false)
        inSearchState.set(false)
        refreshEnableLiveData.postValue(false)
        currentFolderName.set(getFolderName(folderPath))
        currentFolderPath.set(folderPath)

        viewModelScope.launch(Dispatchers.IO) {
            val childFiles = DatabaseManager.instance
                .getVideoDao()
                .getVideoInFolder(folderPath)
            curDirectoryFiles.clear()
            curDirectoryFiles.addAll(childFiles)
            refreshDirectoryWithHistory()
        }
    }

    fun playItem(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val videoSources =
                DatabaseManager.instance.getVideoDao().getFolderVideoByFilePath(filePath)
            videoSources.sortWith(FileComparator(
                value = { getFileName(it.filePath) },
                isDirectory = { false }
            ))

            //如果视频地址对应的目录下找不到，可能视频已经被移除
            val index = videoSources.indexOfFirst { it.filePath == filePath }
            if (index == -1) {
                ToastCenter.showError("播放失败，找不到播放资源")
                return@launch
            }

            showLoading()
            val mediaSource = VideoSourceFactory.Builder()
                .setVideoSources(videoSources)
                .setIndex(index)
                .create(DanmuUtils, MediaType.LOCAL_STORAGE)
            hideLoading()

            if (mediaSource == null) {
                ToastCenter.showError("播放失败，找不到播放资源")
                return@launch
            }
            VideoSourceManager.getInstance().setSource(mediaSource)
            playLiveData.postValue(Any())
        }
    }

    fun refreshDirectoryWithHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val displayFiles = curDirectoryFiles
                .sortedWith(FileComparator(
                    value = { getFileNameNoExtension(it.filePath) },
                    isDirectory = { false }
                ))
                .map {
                    val uniqueKey = LocalSourceFactory.generateUniqueKey(it)
                    val history = DatabaseManager.instance
                        .getPlayHistoryDao()
                        .getPlayHistory(uniqueKey, MediaType.LOCAL_STORAGE)

                    //视频封面
                    var defaultImage: Uri? = null
                    if (it.fileId != 0L) {
                        defaultImage = IOUtils.getVideoUri(it.fileId)
                    }

                    //视频时长
                    val historyDuration = history?.videoDuration ?: 0L
                    val duration = if (historyDuration > 0) {
                        historyDuration
                    } else {
                        it.videoDuration
                    }

                    StorageFileBean(
                        false,
                        it.filePath,
                        getFileNameNoExtension(it.filePath),
                        history?.danmuPath,
                        history?.subtitlePath,
                        history?.videoPosition ?: 0,
                        duration,
                        uniqueKey,
                        lastPlayTime = history?.playTime,
                        fileCoverUrl = defaultImage?.toString(),
                        isLastPlay = it.filePath == lastPlayHistory.value?.url
                    )
                }
            fileLiveData.postValue(displayFiles)
        }
    }

    fun searchVideo(keyword: String) {
        inSearchState.set(true)
        refreshEnableLiveData.postValue(false)

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val searchWord = "%$keyword%"
            val searchResult = if (inRootFolder.get()) {
                DatabaseManager.instance.getVideoDao().searchVideo(searchWord)
            } else {
                DatabaseManager.instance.getVideoDao().searchVideoInFolder(
                    searchWord,
                    currentFolderPath.get()
                )
            }
            curDirectoryFiles.clear()
            curDirectoryFiles.addAll(searchResult)
            refreshDirectoryWithHistory()
        }
    }


    fun exitSearchVideo() {
        inSearchState.set(false)
        if (inRootFolder.get()) {
            listRoot()
        } else {
            openDirectory(currentFolderPath.get()!!)
        }

//        fileLiveData.addSource(databaseVideoLiveData!!) { videoData ->
//            updateLiveDate(videoData, sortLiveData.value!!)
//        }
//
//        fileLiveData.addSource(sortLiveData) { sortType ->
//            databaseVideoLiveData?.value?.let { videoData ->
//                updateLiveDate(videoData, sortType)
//            }
//        }
    }

    private suspend fun refreshSystemVideo(): Boolean {
        return withContext(Dispatchers.IO) {
            //1.从系统中读出所有视频数据
            val systemVideos = MediaResolver.queryVideo()

            //2.遍历扩展目录读出所有视频数据
            val extendFolderList = DatabaseManager.instance.getExtendFolderDao().getAll()
            extendFolderList.forEach {
                val extendVideos = MediaUtils.scanVideoFile(it.folderPath)

                //对扩展目录扫描出的视频去重
                extendVideos.iterator().deduplication(systemVideos) { extend, system ->
                    system.filePath == extend.filePath
                }

                //扩展目录视频数据也视为系统视频数据
                systemVideos.addAll(extendVideos)
            }

            //移除系统视频中无效的视频
            clearInvalidVideo(systemVideos)

            if (systemVideos.isEmpty())
                return@withContext false

            //3.从数据库中读出所有视频数据
            val databaseVideos = DatabaseManager.instance.getVideoDao().getAll()

            //4.数据库中无视频数据，直接将所有系统数据插入数据库
            if (databaseVideos.size == 0) {
                DatabaseManager.instance.getVideoDao().insert(*systemVideos.toTypedArray())
                return@withContext true
            }

            //5.遍历数据库数据
            for (databaseVideo in databaseVideos) {
                //数据库数据对应的视频是否已被删除
                var isDeleted = true
                //在系统数据中未找到数据库数据，说明视频已被删除
                val iterator = systemVideos.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().filePath == databaseVideo.filePath) {
                        //数据库文件能在系统数据中找到，未被删除
                        isDeleted = false
                        //未删除的视频，需要检查弹幕及字幕文件
                        checkSourceExist(databaseVideo)
                        //未删除的视频，不需要删除数据库数据
                        iterator.remove()
                    }
                }

                //已删除的视频，从数据库删除
                if (isDeleted) {
                    DatabaseManager.instance.getVideoDao()
                        .deleteByPath(databaseVideo.filePath)
                }
            }

            //6.将剩余不在数据库的数据，插入数据库
            if (systemVideos.isNotEmpty()) {
                DatabaseManager.instance.getVideoDao().insert(*systemVideos.toTypedArray())
            }


            return@withContext true
        }
    }

    private suspend fun checkSourceExist(videoEntity: VideoEntity) {
        val uniqueKey = LocalSourceFactory.generateUniqueKey(videoEntity)
        val history = PlayHistoryUtils.getPlayHistory(uniqueKey, MediaType.LOCAL_STORAGE)

        if (history?.danmuPath.toFile().isInvalid()) {
            DatabaseManager.instance.getPlayHistoryDao().updateDanmu(
                uniqueKey, MediaType.LOCAL_STORAGE, null, 0
            )
        }

        if (history?.subtitlePath.toFile().isInvalid()) {
            DatabaseManager.instance.getPlayHistoryDao().updateSubtitle(
                uniqueKey, MediaType.LOCAL_STORAGE, null
            )
        }
    }

    /**
     * 清除已删除的文件
     */
    private fun clearInvalidVideo(entities: MutableList<VideoEntity>) {
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val filePath = iterator.next().filePath
            if (filePath.toFile().isInvalid()) {
                iterator.remove()
            }
        }
    }

    fun sort(sortTyoe: FileSortType) {
        sortLiveData.value = sortTyoe
        AppConfig.putLocalFileSortType(sortTyoe.value)
    }
}