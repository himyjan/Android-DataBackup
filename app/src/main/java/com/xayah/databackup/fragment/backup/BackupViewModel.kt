package com.xayah.databackup.fragment.backup

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.airbnb.lottie.LottieAnimationView
import com.drakeet.multitype.MultiTypeAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xayah.databackup.App
import com.xayah.databackup.MainActivity
import com.xayah.databackup.R
import com.xayah.databackup.adapter.AppListAdapter
import com.xayah.databackup.data.AppEntity
import com.xayah.databackup.data.BackupInfo
import com.xayah.databackup.databinding.FragmentBackupBinding
import com.xayah.databackup.databinding.LayoutProcessingBinding
import com.xayah.databackup.util.*
import com.xayah.design.view.fastInitialize
import com.xayah.design.view.notifyDataSetChanged
import com.xayah.design.view.setWithResult
import kotlinx.coroutines.*
import java.text.Collator
import java.util.*

class BackupViewModel : ViewModel() {
    var binding: FragmentBackupBinding? = null

    var isProcessing: Boolean = false

    var appList: MutableList<AppEntity> = mutableListOf()

    var appListAll: MutableList<AppEntity> = mutableListOf()

    var isFiltering = false

    lateinit var mAdapter: MultiTypeAdapter

    var time: Long = 0
    var index = 0
    var total = 0

    var success = 0
    var failed = 0

    var currentAppName = MutableLiveData<String?>()
    var currentAppIcon = MutableLiveData<Drawable?>()

    var selectAllApp = false
    var selectAllData = false
    var selectAll = false

    fun initialize(context: Context, room: Room?, onInitialized: () -> Unit) {
        isFiltering = false

        val mContext = context as FragmentActivity

        // 设置底部栏菜单按钮
        binding?.bottomAppBar?.menu?.let { setSearchView(mContext, it) }
        binding?.bottomAppBar?.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.backup_reverse -> {
                    setReverse(mContext, room)
                }
            }
            true
        }

        // 确定事件
        binding?.floatingActionButton?.setOnClickListener {
            setConfirm(mContext) {
                binding?.coordinatorLayout?.removeView(binding?.bottomAppBar)
                binding?.coordinatorLayout?.removeView(binding?.floatingActionButton)
            }
        }

        // 加载进度
        val linearProgressIndicator = LinearProgressIndicator(mContext).apply { fastInitialize() }
        binding?.relativeLayout?.addView(linearProgressIndicator)
        if (!isProcessing) {
            // 没有Processing
            mAdapter = MultiTypeAdapter().apply {
                register(AppListAdapter(room, mContext))
                CoroutineScope(Dispatchers.IO).launch {
                    // 按照字母表排序
                    val mAppList = Command.getAppList(mContext, room).apply {
                        sortWith { appEntity1, appEntity2 ->
                            val collator = Collator.getInstance(Locale.CHINA)
                            collator.getCollationKey((appEntity1 as AppEntity).appName)
                                .compareTo(collator.getCollationKey((appEntity2 as AppEntity).appName))
                        }
                    }
                    appList = mAppList
                    appListAll = mAppList
                    items = appList
                    withContext(Dispatchers.Main) {
                        binding?.recyclerView?.notifyDataSetChanged()
                        if (appList.isEmpty()) {
                            binding?.linearLayout?.visibility = View.VISIBLE
                        } else {
                            binding?.linearLayout?.visibility = View.GONE
                        }
                        linearProgressIndicator.visibility = View.GONE
                        binding?.recyclerView?.visibility = View.VISIBLE
                        onInitialized()
                    }
                }
            }
        } else {
            // 恢复状态
            // 设置Processing布局
            onProcessing(mContext)
            onInitialized()
        }

        binding?.recyclerView?.apply {
            adapter = mAdapter
            fastInitialize()
        }
    }

    private fun setSearchView(context: Context, menu: Menu) {
        val searchView = SearchView(context).apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let {
                        appList =
                            appListAll.filter {
                                it.appName.lowercase().contains(newText.lowercase())
                            }
                                .toMutableList()
                        mAdapter.items = appList
                        binding?.recyclerView?.notifyDataSetChanged()
                    }
                    return false
                }
            })
            queryHint = this.context.getString(R.string.please_type_key_word)
            isQueryRefinementEnabled = true
        }
        val item = menu.findItem(R.id.backup_search).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW or MenuItem.SHOW_AS_ACTION_IF_ROOM)
            actionView = searchView
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
                    isFiltering = true
                    return true
                }

                override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
                    isFiltering = false
                    return true
                }
            })
        }
        searchView.setOnQueryTextFocusChangeListener { _, queryTextFocused ->
            if (!queryTextFocused) {
                item.collapseActionView()
                searchView.setQuery("", false)
            }
        }
    }

    private fun setReverse(context: FragmentActivity, room: Room?) {
        PopupMenu(context, context.findViewById(R.id.bottomAppBar)).apply {
            menuInflater.inflate(R.menu.select, menu)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.select_all -> {
                        for ((index, _) in appList.withIndex()) {
                            appList[index].backupApp = selectAll
                            appList[index].backupData = selectAll
                        }
                        if (!isFiltering)
                            CoroutineScope(Dispatchers.IO).launch {
                                room?.selectAllApp(selectAll)
                                room?.selectAllData(selectAll)
                            }
                        binding?.recyclerView?.notifyDataSetChanged()
                        selectAll = !selectAll
                    }
                    R.id.select_all_app -> {
                        for ((index, _) in appList.withIndex()) {
                            appList[index].backupApp = selectAllApp
                        }
                        if (!isFiltering)
                            CoroutineScope(Dispatchers.IO).launch {
                                room?.selectAllApp(selectAllApp)
                            }
                        binding?.recyclerView?.notifyDataSetChanged()
                        selectAllApp = !selectAllApp
                    }
                    R.id.select_all_data -> {
                        for ((index, _) in appList.withIndex()) {
                            appList[index].backupData = selectAllData
                        }
                        if (!isFiltering)
                            CoroutineScope(Dispatchers.IO).launch {
                                room?.selectAllData(selectAllData)
                            }
                        binding?.recyclerView?.notifyDataSetChanged()
                        selectAllData = !selectAllData
                    }
                }
                true
            }
            show()
        }
    }

    private fun setConfirm(context: FragmentActivity, onCallback: () -> Unit) {
        if (isFiltering) {
            // 检查是否处于搜索模式
            Toast.makeText(
                context,
                context.getString(R.string.please_exit_search_mode),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // 确认清单
            var contents = "${context.getString(R.string.selected)}\n"
            if (App.globalContext.readIsBackupItself()) {
                // 备份自身
                contents += context.getString(R.string.app_name) + "\n"
            }
            for (i in appListAll) {
                if (i.backupApp || i.backupData) {
                    contents += i.appName + "\n"
                }
            }
            if (App.globalContext.readIsCustomDirectoryPath()) {
                // 自定义备份目录
                contents += App.globalContext.readCustomDirectoryPath().split("\n")
                    .joinToString(separator = "\n")
            }

            MaterialAlertDialogBuilder(context).apply {
                setTitle(context.getString(R.string.tips))
                setCancelable(true)
                setMessage(contents)
                setNegativeButton(context.getString(R.string.cancel)) { _, _ -> }
                setPositiveButton(context.getString(R.string.confirm)) { _, _ ->
                    // 设置Processing布局
                    val layoutProcessingBinding = onProcessing(context)
                    // 清空日志
                    App.log.clear()
                    // 初始化数据
                    time = 0
                    index = 0
                    success = 0
                    failed = 0
                    isProcessing = true
                    // 标题栏计数
                    CoroutineScope(Dispatchers.IO).launch {
                        while (isProcessing) {
                            delay(1000)
                            time += 1
                            val s = String.format("%02d", time % 60)
                            val m = String.format("%02d", time / 60 % 60)
                            val h = String.format("%02d", time / 3600 % 24)
                            withContext(Dispatchers.Main) {
                                (context as MainActivity).binding.toolbar.subtitle = "$h:$m:$s"
                                context.binding.toolbar.title =
                                    "${context.getString(R.string.backup_processing)}: ${index}/${total}"
                            }
                        }
                        withContext(Dispatchers.Main) {
                            (context as MainActivity).binding.toolbar.subtitle =
                                context.viewModel.versionName
                            context.binding.toolbar.title =
                                context.getString(R.string.backup_success)
                        }
                    }
                    onCallback()

                    // 重建appList
                    appList = mutableListOf()
                    appList.addAll(appListAll)
                    mAdapter.items = appList
                    for (i in appListAll) {
                        if (!i.backupApp && !i.backupData) {
                            appList.remove(i)
                        } else {
                            appList[appList.indexOf(i)].isProcessing = true
                        }
                    }

                    // 设置用户
                    val userId = context.readUser()

                    // 获取任务总个数
                    total = appList.size
                    if (App.globalContext.readIsCustomDirectoryPath()) {
                        // 自定义备份目录
                        total += App.globalContext.readCustomDirectoryPath().split("\n").size
                    }
                    if (App.globalContext.readIsBackupItself()) {
                        // 备份自身
                        total += 1
                        val appName = context.getString(R.string.app_name)
                        val icon = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
                        val packageName = "com.xayah.databackup"
                        val outPut = context.readBackupSavePath()

                        currentAppName.postValue(appName)
                        currentAppIcon.postValue(icon)
                        App.log.add("----------------------------")
                        App.log.add("${context.getString(R.string.backup_processing)}: $packageName")
                        var state = true // 该任务是否成功完成
                        App.log.add(context.getString(R.string.backup_apk_processing))
                        Command.backupItself(packageName, outPut, userId)
                            .apply {
                                if (!this)
                                    state = false
                            }
                        App.log.add(context.getString(R.string.success))
                        if (state)
                            success += 1
                        else
                            failed += 1
                        index++
                    }

                    // 生成备份信息
                    val backupInfo = BackupInfo(context.getString(R.string.backup_version))
                    Command.object2JSONFile(
                        backupInfo,
                        "${context.readBackupSavePath()}/$userId/info"
                    ).apply {
                        if (!this) {
                            App.log.add(context.getString(R.string.generate_backup_info_failed))
                            isProcessing = false
                            return@setPositiveButton
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        for (i in appList) {
                            // 推送数据
                            currentAppName.postValue(i.appName)
                            currentAppIcon.postValue(i.icon)
                            App.log.add("----------------------------")
                            App.log.add("${context.getString(R.string.backup_processing)}: ${i.packageName}")
                            var state = true // 该任务是否成功完成

                            // 设置任务参数
                            val compressionType = context.readCompressionType()
                            val packageName = i.packageName
                            val outPut = "${context.readBackupSavePath()}/$userId/${packageName}"

                            if (i.backupApp) {
                                // 选中备份应用
                                App.log.add(context.getString(R.string.backup_apk_processing))
                                Command.compressAPK(compressionType, packageName, outPut, userId)
                                    .apply {
                                        if (!this)
                                            state = false
                                    }
                                App.log.add(context.getString(R.string.success))
                            }
                            if (i.backupData) {
                                // 选中备份数据
                                App.log.add("${context.getString(R.string.backup_processing)}user")
                                // 备份user数据
                                Command.compress(
                                    compressionType,
                                    "user",
                                    packageName,
                                    outPut,
                                    "/data/user/$userId"
                                )
                                    .apply {
                                        if (!this)
                                            state = false
                                    }
                                App.log.add("${context.getString(R.string.backup_processing)}data")
                                // 备份data数据
                                Command.compress(
                                    compressionType,
                                    "data",
                                    packageName,
                                    outPut,
                                    "/data/media/$userId/Android/data"
                                )
                                    .apply {
                                        if (!this)
                                            state = false
                                    }
                                App.log.add("${context.getString(R.string.backup_processing)}obb")
                                // 备份obb数据
                                Command.compress(
                                    compressionType,
                                    "obb",
                                    packageName,
                                    outPut,
                                    "/data/media/$userId/Android/obb"
                                )
                                    .apply {
                                        if (!this)
                                            state = false
                                    }
                            }
                            // 生成应用信息
                            Command.generateAppInfo(i.appName, i.packageName, outPut).apply {
                                if (!this)
                                    state = false
                            }
                            // 检验
                            Command.testArchiveForEach(outPut).apply {
                                if (!this)
                                    state = false
                            }
                            if (state)
                                success += 1
                            else
                                failed += 1

                            index++
                        }
                        if (App.globalContext.readIsCustomDirectoryPath()) {
                            // 移除图标
                            withContext(Dispatchers.Main) {
                                layoutProcessingBinding.linearLayout.removeView(
                                    layoutProcessingBinding.shapeableImageViewAppIcon
                                )
                            }

                            // 备份自定义目录
                            val outPut = "${context.readBackupSavePath()}/$userId/media"
                            for (i in App.globalContext.readCustomDirectoryPath().split("\n")) {
                                // 推送数据
                                currentAppName.postValue(i)
                                App.log.add("----------------------------")
                                var state = true // 该任务是否成功完成

                                // 备份目录
                                Command.compress(
                                    App.globalContext.readCompressionType(),
                                    "media",
                                    "media",
                                    outPut,
                                    i
                                ).apply {
                                    if (!this)
                                        state = false
                                }
                                // 检验
                                Command.testArchiveForEach(outPut).apply {
                                    if (!this)
                                        state = false
                                }
                                if (state)
                                    success += 1
                                else
                                    failed += 1
                                index++
                            }
                        }
                        withContext(Dispatchers.Main) {
                            // 展示完成页面
                            binding?.relativeLayout?.removeAllViews()
                            val showResult = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.backup_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                BottomSheetDialog(context).apply {
                                    setWithResult(
                                        App.log.toString(),
                                        success,
                                        failed
                                    )
                                }
                            }
                            if (binding == null) {
                                showResult()
                            } else {
                                val lottieAnimationView = LottieAnimationView(context)
                                lottieAnimationView.apply {
                                    layoutParams =
                                        RelativeLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        ).apply {
                                            addRule(RelativeLayout.CENTER_IN_PARENT)
                                        }
                                    setAnimation(R.raw.success)
                                    playAnimation()
                                    addAnimatorUpdateListener { animation ->
                                        if (animation.animatedFraction == 1.0F) {
                                            showResult()
                                        }
                                    }
                                }
                                binding?.relativeLayout?.addView(lottieAnimationView)
                            }
                            isProcessing = false
                        }
                    }
                }
                show()
            }
        }
    }

    private fun onProcessing(context: FragmentActivity): LayoutProcessingBinding {
        // 移除底部栏和悬浮按钮
        binding?.coordinatorLayout?.removeView(binding?.bottomAppBar)
        binding?.coordinatorLayout?.removeView(binding?.floatingActionButton)
        // 载入Processing布局
        val layoutProcessingBinding =
            LayoutProcessingBinding.inflate(LayoutInflater.from(context)).apply {
                root.apply {
                    layoutParams =
                        RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                            .apply {
                                addRule(RelativeLayout.CENTER_IN_PARENT)
                            }
                }
            }
        binding?.relativeLayout?.removeAllViews()
        binding?.relativeLayout?.addView(layoutProcessingBinding.root)
        // 绑定可变数据
        currentAppName.observe(context) {
            it?.apply {
                layoutProcessingBinding.materialTextViewAppName.text = this
            }
        }
        currentAppIcon.observe(context) {
            it?.apply {
                layoutProcessingBinding.shapeableImageViewAppIcon.setImageDrawable(this)
            }
        }
        App.log.onObserveLast(context) {
            it?.apply {
                layoutProcessingBinding.materialTextViewLog.text = this
            }
        }
        return layoutProcessingBinding
    }
}