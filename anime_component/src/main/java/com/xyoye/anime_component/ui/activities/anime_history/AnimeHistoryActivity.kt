package com.xyoye.anime_component.ui.activities.anime_history

import android.graphics.Bitmap
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.xyoye.anime_component.BR
import com.xyoye.anime_component.R
import com.xyoye.anime_component.databinding.ActivityAnimeHistoryBinding
import com.xyoye.anime_component.databinding.ItemAnimeBinding
import com.xyoye.anime_component.utils.PaletteBitmapTarget
import com.xyoye.common_component.adapter.addEmptyView
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.gridEmpty
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.toResColor
import com.xyoye.common_component.utils.FastClickFilter
import com.xyoye.common_component.utils.dp2px
import com.xyoye.common_component.utils.view.ItemDecorationDrawable
import com.xyoye.data_component.data.CloudHistoryData
import com.xyoye.data_component.data.CloudHistoryListData
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RouteTable.Anime.AnimeHistory)
@AndroidEntryPoint
class AnimeHistoryActivity : BaseActivity<AnimeHistoryViewModel, ActivityAnimeHistoryBinding>() {

    @Autowired
    @JvmField
    var historyData: CloudHistoryListData? = null

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            AnimeHistoryViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_anime_history

    override fun initView() {
        ARouter.getInstance().inject(this)

        title = "云端播放历史"

        initRv()

        if (historyData == null) {
            viewModel.getUserFollow()
        } else {
            dataBinding.historyRv.setData(historyData!!.playHistoryAnimes)
        }

        viewModel.historyLiveData.observe(this) {
            dataBinding.historyRv.setData(it.playHistoryAnimes)
        }
    }

    private fun initRv(){
        dataBinding.historyRv.apply {

            layoutManager = gridEmpty(3)

            adapter = buildAdapter {

                addEmptyView(R.layout.layout_empty)

                addItem<CloudHistoryData, ItemAnimeBinding>(R.layout.item_anime) {
                    initView { data, _, _ ->
                        itemBinding.apply {
                            Glide.with(coverIv)
                                .asBitmap()
                                .load(data.imageUrl ?: "")
                                .transition((BitmapTransitionOptions.withCrossFade()))
                                .into(object : PaletteBitmapTarget() {
                                    override fun onBitmapReady(bitmap: Bitmap, paletteColor: Int) {
                                        coverIv.setImageBitmap(bitmap)
                                        animeNameTv.setBackgroundColor(paletteColor)
                                    }
                                })

                            animeNameTv.text = data.animeTitle
                            itemLayout.setOnClickListener {
                                //防止快速点击
                                if(FastClickFilter.isNeedFilter()){
                                    return@setOnClickListener
                                }

                                ViewCompat.setTransitionName(coverIv, "cover_image")
                                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                                    this@AnimeHistoryActivity, coverIv, coverIv.transitionName
                                )

                                ARouter.getInstance()
                                    .build(RouteTable.Anime.AnimeDetail)
                                    .withInt("animeId", data.animeId)
                                    .withOptionsCompat(options)
                                    .navigation(this@AnimeHistoryActivity)
                            }
                        }
                    }
                }
            }

            val pxValue = dp2px(10)
            addItemDecoration(
                ItemDecorationDrawable(
                    pxValue,
                    pxValue,
                    R.color.item_bg_color.toResColor()
                )
            )
        }
    }
}