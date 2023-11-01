package com.cherry.lib.doc.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cherry.lib.doc.R
import com.cherry.lib.doc.bean.DocEngine
import com.cherry.lib.doc.bean.DocSourceType
import com.cherry.lib.doc.bean.FileType
import com.cherry.lib.doc.interfaces.OnDownloadListener
import com.cherry.lib.doc.interfaces.OnDocPageChangeListener
import com.cherry.lib.doc.interfaces.OnWebLoadListener
import com.cherry.lib.doc.office.IOffice
import com.cherry.lib.doc.pdf.PdfDownloader
import com.cherry.lib.doc.pdf.PdfQuality
import com.cherry.lib.doc.pdf.PdfRendererCore
import com.cherry.lib.doc.pdf.PdfViewAdapter
import com.cherry.lib.doc.util.Constant
import com.cherry.lib.doc.util.FileUtils
import com.cherry.lib.doc.util.ViewUtils.hide
import com.cherry.lib.doc.util.ViewUtils.show
import kotlinx.android.synthetic.main.doc_view.view.*
import java.io.File
import java.net.URLEncoder

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfView
 * Author: Victor
 * Date: 2023/10/30 11:30
 * Description: 
 * -----------------------------------------------------------------
 */

class DocView : FrameLayout, OnDownloadListener, OnWebLoadListener {

    private val TAG = "PdfRendererView"

    var mActivity: Activity? = null
    var lifecycleScope: LifecycleCoroutineScope = (context as AppCompatActivity).lifecycleScope
    private var pdfRendererCore: PdfRendererCore? = null
    private var pdfViewAdapter: PdfViewAdapter? = null
    private var quality = PdfQuality.ENHANCED
    private var engine = DocEngine.INTERNAL
    private var showDivider = true
    private var showPageNum = true
    private var divider: Drawable? = null
    private var runnable = Runnable {}
    var enableLoadingForPages: Boolean = true
    var pbDefaultHeight = 2
    var pbHeight: Int = pbDefaultHeight
    var pbDefaultColor = Color.RED
    var pbColor: Int = pbDefaultColor

    private var pdfRendererCoreInitialised = false
    var pageMargin: Rect = Rect(0, 0, 0, 0)

    var totalPageCount = 0

    var mOnDocPageChangeListener: OnDocPageChangeListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        initView(attrs, defStyle)
    }

    fun initView(attrs: AttributeSet?, defStyle: Int) {
        inflate(context, R.layout.doc_view, this)

        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.DocView, defStyle, 0)
        val ratio =
            typedArray.getInt(R.styleable.DocView_dv_quality, PdfQuality.NORMAL.ratio)
        quality = PdfQuality.values().first { it.ratio == ratio }
        val engineValue =
            typedArray.getInt(R.styleable.DocView_dv_engine, DocEngine.INTERNAL.value)
        engine = DocEngine.values().first { it.value == engineValue }
        showDivider = typedArray.getBoolean(R.styleable.DocView_dv_showDivider, true)
        showPageNum = typedArray.getBoolean(R.styleable.DocView_dv_show_page_num, true)
        divider = typedArray.getDrawable(R.styleable.DocView_dv_divider)
        enableLoadingForPages = typedArray.getBoolean(
            R.styleable.DocView_dv_enableLoadingForPages,
            enableLoadingForPages
        )
        pbHeight =
            typedArray.getDimensionPixelSize(R.styleable.DocView_dv_page_pb_height, pbDefaultHeight)
        pbColor = typedArray.getColor(R.styleable.DocView_dv_page_pb_color, pbDefaultColor)

        val marginDim = typedArray.getDimensionPixelSize(R.styleable.DocView_dv_page_margin, 0)
        pageMargin = Rect(marginDim, marginDim, marginDim, marginDim).apply {
            top = typedArray.getDimensionPixelSize(R.styleable.DocView_dv_page_marginTop, top)
            left = typedArray.getDimensionPixelSize(R.styleable.DocView_dv_page_marginLeft, left)
            right = typedArray.getDimensionPixelSize(R.styleable.DocView_dv_page_marginRight, right)
            bottom =
                typedArray.getDimensionPixelSize(R.styleable.DocView_dv_page_marginBottom, bottom)
        }

        val layoutParams = mPlLoadProgress.layoutParams
        layoutParams.height = pbHeight
        mPlLoadProgress.layoutParams = layoutParams

        mPlLoadProgress.progressTintList = ColorStateList.valueOf(pbColor)

        typedArray.recycle()


        runnable = Runnable {
            mPdfPageNo.hide()
        }
    }


    fun openDoc(
        activity: Activity, docUrl: String?, docSourceType: Int,
        engine: DocEngine = this.engine
    ) {
        mActivity = activity
        openDoc(activity, docUrl, docSourceType, -1, engine)
    }

    fun openDoc(
        activity: Activity?, docUrl: String?,
        docSourceType: Int, fileType: Int,
        engine: DocEngine = this.engine
    ) {
        mActivity = activity
        Log.e(TAG, "openDoc()......fileType = $fileType")
        if (docSourceType == DocSourceType.URL && fileType != FileType.IMAGE) {
            if (engine == DocEngine.MICROSOFT || engine == DocEngine.XDOC || engine == DocEngine.GOOGLE
            ) {
                showByWeb(docUrl ?: "", engine)
                return
            }
            downloadFile(docUrl ?: "")
            return
        }

        var type = FileUtils.getFileTypeForUrl(docUrl)
        if (fileType > 0) {
            type = fileType
        }
        when (type) {
            FileType.PDF -> {
                Log.e(TAG, "openDoc()......PDF")
                mDocWeb.hide()
                mFlDocContainer.hide()
                mRvPdf.show()
                mIvImage.hide()

                showPdf(docSourceType, docUrl)
            }

            FileType.IMAGE -> {
                if (showPageNum) {
                    showPageNum = false
                }
                Log.e(TAG, "openDoc()......")
                mDocWeb.hide()
                mFlDocContainer.hide()
                mRvPdf.hide()
                mIvImage.show()
                if (docSourceType == DocSourceType.PATH) {
                    Log.e(TAG, "openDoc()......PATH")
                    mIvImage.load(File(docUrl))
                } else {
                    Log.e(TAG, "openDoc()......URL")
                    mIvImage.load(docUrl)
                }
            }

            FileType.NOT_SUPPORT -> {
                if (showPageNum) {
                    showPageNum = false
                }
                Log.e(TAG, "openDoc()......NOT_SUPPORT")
                mDocWeb.show()
                mFlDocContainer.hide()
                mRvPdf.hide()
                mIvImage.hide()
                showByWeb(docUrl ?: "", this.engine)
            }

            else -> {
                Log.e(TAG, "openDoc()......ELSE")
                if (showPageNum) {
                    showPageNum = false
                }
                mDocWeb.hide()
                mFlDocContainer.show()
                mRvPdf.hide()
                mIvImage.hide()
                activity?.let { showDoc(it, mFlDocContainer, docUrl, docSourceType, fileType) }
            }
        }
    }

    private fun showDoc(
        activity: Activity,
        mDocContainer: ViewGroup?,
        url: String?,
        docSourceType: Int,
        fileType: Int
    ) {
        Log.e(TAG, "showDoc()......+$url+")
        val iOffice: IOffice = object : IOffice() {
            override fun getActivity(): Activity {
                return activity
            }

            override fun openFileFinish() {
                mDocContainer?.postDelayed({
                    mDocContainer.removeAllViews()
                    mDocContainer.addView(
                        view,
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }, 200)
            }

            override fun getAppName(): String {
                return "Loading..."
            }

            override fun getTemporaryDirectory(): File {
                val file = activity.cacheDir

                if (file != null) {
                    Log.d("ASJAJSAS: ", file.path)
                }
                return file ?: activity.filesDir
            }

            override fun fullScreen(fullscreen: Boolean) {
            }

        }
        Log.d(
            "AAAAA", url + "\n" + docSourceType + "\n" + fileType.toString()
        )
        iOffice.openFile(
            url, docSourceType, fileType.toString()
        )
    }

    fun showPdf(docSourceType: Int, url: String?) {
        Log.e(TAG, "showPdf()......")
        when (docSourceType) {
            DocSourceType.URL -> {
                Log.e(TAG, "showPdf()......URL")
                initWithUrl(url = url ?: "", pdfQuality = PdfQuality.FAST)
            }

            DocSourceType.URI -> {
                Log.e(TAG, "showPdf()......URI")
                initWithUri(fileUri = url ?: "", pdfQuality = PdfQuality.FAST)
            }

            DocSourceType.PATH -> {
                Log.e(TAG, "showPdf()......PATH")
                initWithPath(path = url ?: "", pdfQuality = PdfQuality.FAST)
            }

            DocSourceType.ASSETS -> {
                Log.e(TAG, "showPdf()......ASSETS")
                initWithAssets(fileName = url ?: "", pdfQuality = PdfQuality.FAST)
            }
        }
    }

    private fun showPdf(file: File, pdfQuality: PdfQuality) {
        Log.e(javaClass.simpleName, "initView-exists = ${file.exists()}")
        pdfRendererCore = PdfRendererCore(context, file, pdfQuality)
        totalPageCount = pdfRendererCore?.getPageCount() ?: 0
        pdfRendererCoreInitialised = true
        pdfViewAdapter = PdfViewAdapter(pdfRendererCore, pageMargin, enableLoadingForPages)

        mRvPdf.adapter = pdfViewAdapter
        mRvPdf.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        mRvPdf.itemAnimator = DefaultItemAnimator()
        mRvPdf.addOnScrollListener(scrollListener)

        if (showDivider) {
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                divider?.let { setDrawable(it) }
            }.let { mRvPdf.addItemDecoration(it) }
        }
    }

    fun initWithUrl(
        url: String,
        pdfQuality: PdfQuality = this.quality,
        engine: DocEngine = this.engine,
        lifecycleScope: LifecycleCoroutineScope = (context as AppCompatActivity).lifecycleScope
    ) {
        this.lifecycleScope = lifecycleScope
        downloadFile(url, pdfQuality, lifecycleScope)
    }

    fun initWithPath(path: String, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")
        initWithFile(File(path), pdfQuality)
    }

    fun initWithFile(file: File, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")
        showPdf(file, pdfQuality)
    }

    fun initWithAssets(fileName: String, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")

        val file = FileUtils.fileFromAsset(context, fileName)
        showPdf(file, pdfQuality)
    }

    fun initWithUri(fileUri: String, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")

        val file = FileUtils.fileFromUri(context, fileUri)
        showPdf(file, pdfQuality)
    }

    fun downloadFile(
        url: String, pdfQuality: PdfQuality = this.quality,
        lifecycleScope: LifecycleCoroutineScope = (context as AppCompatActivity).lifecycleScope
    ) {
        PdfDownloader(url, this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showByWeb(url: String, engine: DocEngine = this.engine) {
        mDocWeb.mOnWebLoadListener = this

        var engineUrl = "engine"
        when (engine) {
            DocEngine.MICROSOFT -> {
                engineUrl = Constant.MICROSOFT_URL
            }

            DocEngine.XDOC -> {
                engineUrl = Constant.XDOC_VIEW_URL
            }

            DocEngine.GOOGLE -> {
                engineUrl = Constant.GOOGLE_URL
            }
        }
        mDocWeb.loadUrl("$engineUrl${URLEncoder.encode(url, "UTF-8")}")
    }

    override fun getDownloadContext() = context

    override fun onDownloadStart() {
        Log.e(TAG, "initWithUrl-onDownloadStart()......")
    }

    override fun onDownloadProgress(currentBytes: Long, totalBytes: Long) {
        var progress = (currentBytes.toFloat() / totalBytes.toFloat() * 100F).toInt()
        if (progress >= 100) {
            progress = 100
        }
        Log.e(TAG, "initWithUrl-onDownloadProgress()......progress = $progress")
        showLoadingProgress(progress)
    }

    override fun onDownloadSuccess(absolutePath: String) {
        Log.e(TAG, "initWithUrl-onDownloadSuccess()......")
        showLoadingProgress(100)
        openDoc(mActivity, absolutePath, DocSourceType.PATH, -1)
    }

    override fun onError(error: Throwable) {
        error.printStackTrace()
        Log.e(TAG, "initWithUrl-onError()......${error.localizedMessage}")
        showLoadingProgress(100)
    }

    override fun getCoroutineScope() = lifecycleScope

    override fun OnWebLoadProgress(progress: Int) {
        showLoadingProgress(progress)
    }

    override fun onTitle(title: String?) {
    }

    fun showLoadingProgress(progress: Int) {
        if (progress == 100) {
            mPlLoadProgress.hide()
        } else {
            mPlLoadProgress?.show()
            mPlLoadProgress?.progress = progress
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            (recyclerView.layoutManager as LinearLayoutManager).run {
                var foundPosition: Int = findLastCompletelyVisibleItemPosition()

                if (foundPosition != RecyclerView.NO_POSITION)
                    mPdfPageNo.text = context.getString(
                        R.string.pdfView_page_no,
                        foundPosition + 1,
                        totalPageCount
                    )

                if (showPageNum) {
                    mPdfPageNo.visibility = View.VISIBLE
                }

                if (foundPosition == 0)
                    mPdfPageNo.postDelayed({
                        mPdfPageNo.visibility = GONE
                    }, 3000)

                if (foundPosition != RecyclerView.NO_POSITION) {
                    mOnDocPageChangeListener?.OnPageChanged(foundPosition, totalPageCount)
                    return@run
                }
                foundPosition = findFirstVisibleItemPosition()
                if (foundPosition != RecyclerView.NO_POSITION) {
                    mOnDocPageChangeListener?.OnPageChanged(foundPosition, totalPageCount)
                    return@run
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                mPdfPageNo.postDelayed(runnable, 3000)
            } else {
                mPdfPageNo.removeCallbacks(runnable)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closePdfRender()
    }

    fun closePdfRender() {
        if (pdfRendererCoreInitialised) {
            pdfRendererCore?.closePdfRender()
        }
    }

    fun exportPdf() {

    }

}