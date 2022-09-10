package com.xyoye.local_component.ui.activities.local_media

import android.text.TextUtils
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatSpinner
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.*
import com.xyoye.common_component.utils.*
import com.xyoye.common_component.weight.StorageAdapter
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.enums.FileSortType
import com.xyoye.data_component.enums.MediaType
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityLocalMediaBinding

import dagger.hilt.android.AndroidEntryPoint

@Route(path = RouteTable.Local.LocalMediaStorage)
@AndroidEntryPoint
class LocalMediaActivity : BaseActivity<LocalMediaViewModel, ActivityLocalMediaBinding>() {

    private var mSearchView: SearchView? = null
    private var mSearchEt: SearchView.SearchAutoComplete? = null

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            LocalMediaViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_local_media

    override fun initView() {
        ARouter.getInstance().inject(this)

        title = "本地媒体库"

        initRv()

        initListener()

        dataBinding.refreshLayout.setColorSchemeResources(R.color.text_theme)
        dataBinding.refreshLayout.isRefreshing = true
        viewModel.listRoot(true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDirectoryWithHistory()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK && interceptBack())
            true
        else
            super.onKeyDown(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_local_media, menu)

        val spinner = menu?.findItem(R.id.app_bar_sort)?.actionView as AppCompatSpinner
        spinner.apply {
            adapter = ArrayAdapter(this@LocalMediaActivity,
                android.R.layout.simple_dropdown_item_1line,
                Enum.localizedValues<FileSortType>(this@LocalMediaActivity,
                    com.xyoye.common_component.R.array.enum_sort_type))
            setSelection(FileSortType.valueOf(AppConfig.getLocalFileSortType()).ordinal)
            onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    viewModel.sort(FileSortType.values()[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
        }

        mSearchView = menu.findItem(R.id.item_search_file)?.actionView as SearchView
        mSearchView?.apply {
            onActionViewExpanded()
            isIconified = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH

            findViewById<View>(R.id.search_plate).background = null
            findViewById<View>(R.id.submit_area).background = null

            mSearchEt = findViewById(R.id.search_src_text)
            mSearchEt?.textSize = 16f
        }

        initSearchListener()

        return super.onCreateOptionsMenu(menu)
    }

    private fun initRv() {

        dataBinding.mediaRv.apply {
            layoutManager = vertical()

            adapter = StorageAdapter.newInstance(
                this@LocalMediaActivity,
                MediaType.LOCAL_STORAGE,
                refreshDirectory = {
                    viewModel.refreshDirectoryWithHistory()
                },
                openFile = {
                    mSearchView?.clearFocus()
                    viewModel.playItem(it.filePath)
                },
                openDirectory = {
                    mSearchView?.clearFocus()
                    viewModel.openDirectory(it.filePath)
                },
                moreAction = {
                    mSearchView?.clearFocus()
                    false
                }
            )
        }
    }

    private fun initListener() {
        dataBinding.fastPlayBt.setOnClickListener {
            viewModel.fastPlay()
        }

        dataBinding.refreshLayout.setOnRefreshListener {
            viewModel.listRoot(true)
        }

        mToolbar?.setNavigationOnClickListener {
            if (interceptBack().not()) finish()
        }

        viewModel.sortedFileLiveData.observe(this) {
            updateExtViewVisible(viewModel.inSearchState.get().not())
            dataBinding.mediaRv.setData(it)
        }

        viewModel.refreshEnableLiveData.observe(this) {
            dataBinding.refreshLayout.isEnabled = it
        }

        viewModel.refreshLiveData.observe(this) { isSuccess ->
            if (dataBinding.refreshLayout.isRefreshing) {
                dataBinding.refreshLayout.isRefreshing = false
            }
            if (!isSuccess) {
                ToastCenter.showError("未找到视频文件")
            }
        }

        viewModel.lastPlayHistory.observe(this) {
            //ignore
        }

        viewModel.playLiveData.observe(this) {
            ARouter.getInstance()
                .build(RouteTable.Player.Player)
                .navigation()
        }
    }

    private fun initSearchListener() {
        mSearchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                mSearchView?.clearFocus()
                return false
            }

            override fun onQueryTextChange(keyword: String): Boolean {
                if (TextUtils.isEmpty(keyword)) {
                    viewModel.exitSearchVideo()
                } else {
                    viewModel.searchVideo(keyword)
                }
                return false
            }
        })
    }

    private fun updateExtViewVisible(visible: Boolean) {
        dataBinding.pathLl.isVisible = visible
        dataBinding.fastPlayBt.isVisible = visible
    }

    private fun interceptBack(): Boolean {
        if (mSearchEt?.isShown == true) {
            mSearchEt?.setText("")
            mSearchView?.isIconified = true
            return true
        }

        if (!viewModel.inRootFolder.get()) {
            viewModel.listRoot()
            return true
        }

        return false
    }
}