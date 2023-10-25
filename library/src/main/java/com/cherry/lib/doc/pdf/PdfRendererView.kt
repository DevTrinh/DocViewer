package com.cherry.lib.doc.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.cherry.lib.doc.R
import com.cherry.lib.doc.util.FileUtils
import com.cherry.lib.doc.util.ViewUtils.hide
import com.cherry.lib.doc.util.ViewUtils.show
import kotlinx.android.synthetic.main.pdf_rendererview.view.*
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.net.URLEncoder

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfRendererView
 * Author: Victor
 * Date: 2023/09/28 11:32
 * Description: 
 * -----------------------------------------------------------------
 */

class PdfRendererView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var mPlLoadProgress: ProgressBar
    private lateinit var pageNo: TextView
    private lateinit var pdfRendererCore: PdfRendererCore
    private lateinit var pdfViewAdapter: PdfViewAdapter
    private var quality = PdfQuality.NORMAL
    private var engine = PdfEngine.INTERNAL
    private var showDivider = true
    private var showPageNum = true
    private var divider: Drawable? = null
    private var runnable = Runnable {}
    var enableLoadingForPages: Boolean = true
    var pbDefaultHeight = 2
    var pbHeight: Int = pbDefaultHeight

    private var pdfRendererCoreInitialised = false
    var pageMargin: Rect = Rect(0,0,0,0)
    var statusListener: StatusCallBack? = null

    val totalPageCount: Int
        get() {
            return pdfRendererCore.getPageCount()
        }

    init {
        val v = LayoutInflater.from(context).inflate(R.layout.pdf_rendererview, this, false)
        addView(v)
        recyclerView = findViewById(R.id.recyclerView)
        mPlLoadProgress = findViewById(R.id.mPlLoadProgress)
        pageNo = findViewById(R.id.pageNumber)

    }
    interface StatusCallBack {
        fun onDownloadStart() {}
        fun onDownloadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long?) {}
        fun onDownloadSuccess() {}
        fun onError(error: Throwable) {}
        fun onPageChanged(currentPage: Int, totalPage: Int) {}
    }

    fun initWithUrl(
        url: String,
        pdfQuality: PdfQuality = this.quality,
        engine: PdfEngine = this.engine,
        lifecycleScope: LifecycleCoroutineScope = (context as AppCompatActivity).lifecycleScope
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || engine == PdfEngine.GOOGLE) {
            initUnderKitkat(url)
            statusListener?.onDownloadStart()
            return
        }

        PdfDownloader(url, object : PdfDownloader.StatusListener {
            override fun getContext(): Context = context
            override fun onDownloadStart() {
                statusListener?.onDownloadStart()
            }

            override fun onDownloadProgress(currentBytes: Long, totalBytes: Long) {
                var progress = (currentBytes.toFloat() / totalBytes.toFloat() * 100F).toInt()
                if (progress >= 100)
                    progress = 100
                statusListener?.onDownloadProgress(progress, currentBytes, totalBytes)

                mPlLoadProgress?.show()
                mPlLoadProgress?.progress = progress
            }

            override fun onDownloadSuccess(absolutePath: String) {
                initWithPath(absolutePath, pdfQuality)
                statusListener?.onDownloadSuccess()
                mPlLoadProgress?.hide()
            }

            override fun onError(error: Throwable) {
                error.printStackTrace()
                statusListener?.onError(error)
                mPlLoadProgress?.hide()
            }

            override fun getCoroutineScope(): CoroutineScope = lifecycleScope
        })
    }

    fun initWithPath(path: String, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")
        initWithFile(File(path), pdfQuality)
    }

    fun initWithFile(file: File, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")
        initView(file, pdfQuality)
    }

    fun initWithAssets(fileName: String, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")

        val file = FileUtils.fileFromAsset(context,fileName)
        initView(file, pdfQuality)
    }

    fun initWithUri(fileUri: String, pdfQuality: PdfQuality = this.quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw UnsupportedOperationException("should be over API 21")

        val file = FileUtils.fileFromUri(context,fileUri)
        initView(file, pdfQuality)
    }

    private fun initView(file: File, pdfQuality: PdfQuality) {
        pdfRendererCore = PdfRendererCore(context, file, pdfQuality)
        pdfRendererCoreInitialised = true
        pdfViewAdapter = PdfViewAdapter(pdfRendererCore, pageMargin, enableLoadingForPages)

        recyclerView.apply {
            adapter = pdfViewAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            itemAnimator = DefaultItemAnimator()
            if (showDivider) {
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                    divider?.let { setDrawable(it) }
                }.let { addItemDecoration(it) }
            }
            addOnScrollListener(scrollListener)
        }

        runnable = Runnable {
            pageNo.hide()
        }

    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            (recyclerView.layoutManager as LinearLayoutManager).run {
                var foundPosition : Int = findLastCompletelyVisibleItemPosition()

                pageNo.run {
                    if (foundPosition != NO_POSITION)
                        text = context.getString(R.string.pdfView_page_no,foundPosition + 1,totalPageCount)
                    if (showPageNum) {
                        pageNo.visibility = View.VISIBLE
                    }
                }

                if (foundPosition == 0)
                    pageNo.postDelayed({
                        pageNo.visibility = GONE
                    }, 3000)

                if (foundPosition != NO_POSITION) {
                    statusListener?.onPageChanged(foundPosition, totalPageCount)
                    return@run
                }
                foundPosition = findFirstVisibleItemPosition()
                if (foundPosition != NO_POSITION) {
                    statusListener?.onPageChanged(foundPosition, totalPageCount)
                    return@run
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                pageNo.postDelayed(runnable, 3000)
            } else {
                pageNo.removeCallbacks(runnable)
            }
        }

    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initUnderKitkat(url: String) {
        val v = LayoutInflater.from(context).inflate(R.layout.pdf_rendererview, this, false)
        addView(v)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = PdfWebViewClient(statusListener)
        webView.loadUrl(
            "https://drive.google.com/viewer/viewer?hl=en&embedded=true&url=${URLEncoder.encode(
                url,
                "UTF-8"
            )}"
        )
    }

    internal class PdfWebViewClient(private val statusListener: StatusCallBack?) : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            statusListener?.onDownloadSuccess()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            statusListener?.onError(Throwable("Web resource error"))
        }

        @Deprecated("Deprecated in Java")
        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            statusListener?.onError(Throwable("Web resource error"))
        }
    }

    init {
        getAttrs(attrs, defStyleAttr)
    }

    private fun getAttrs(attrs: AttributeSet?, defStyle: Int) {
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.PdfRendererView, defStyle, 0)
        setTypeArray(typedArray)
    }

    private fun setTypeArray(typedArray: TypedArray) {
        val ratio =
            typedArray.getInt(R.styleable.PdfRendererView_pdfView_quality, PdfQuality.NORMAL.ratio)
        quality = PdfQuality.values().first { it.ratio == ratio }
        val engineValue =
            typedArray.getInt(R.styleable.PdfRendererView_pdfView_engine, PdfEngine.INTERNAL.value)
        engine = PdfEngine.values().first { it.value == engineValue }
        showDivider = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_showDivider, true)
        showPageNum = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_show_page_num, true)
        divider = typedArray.getDrawable(R.styleable.PdfRendererView_pdfView_divider)
        enableLoadingForPages = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_enableLoadingForPages, enableLoadingForPages)
        pbHeight = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_pb_height, pbDefaultHeight)

        val marginDim = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_margin, 0)
        pageMargin = Rect(marginDim, marginDim, marginDim, marginDim).apply {
            top = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginTop, top)
            left = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginLeft, left)
            right = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginRight, right)
            bottom = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginBottom, bottom)
        }

        var layoutParams = mPlLoadProgress.layoutParams
        layoutParams.height = pbHeight
        mPlLoadProgress.layoutParams = layoutParams

        typedArray.recycle()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closePdfRender()
    }

    fun closePdfRender() {
        if (pdfRendererCoreInitialised) {
            pdfRendererCore.closePdfRender()
        }
    }

}