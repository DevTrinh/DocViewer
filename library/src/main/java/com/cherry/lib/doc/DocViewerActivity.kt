package com.cherry.lib.doc

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import coil.load
import com.cherry.lib.doc.bean.DocEngine
import com.cherry.lib.doc.bean.DocSourceType
import com.cherry.lib.doc.bean.FileType
import com.cherry.lib.doc.util.Constant
import com.cherry.lib.doc.util.FileUtils
import kotlinx.android.synthetic.main.activity_doc_viewer.*
import java.io.File

class DocViewerActivity : AppCompatActivity() {

    companion object {
        fun launchDocViewer(
            activity: AppCompatActivity, docSourceType: Int, path: String?,
            fileType: Int? = null
        ) {
            val intent = Intent(activity, DocViewerActivity::class.java)
            intent.putExtra(Constant.INTENT_SOURCE_KEY, docSourceType)
            intent.putExtra(Constant.INTENT_DATA_KEY, path)
            intent.putExtra(Constant.INTENT_TYPE_KEY, fileType)
            activity.startActivity(intent)
        }
    }

    var docSourceType = 0
    var fileType = -1
    var docUrl: String? = null//Địa chỉ tệp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doc_viewer)

        initView()
        initData(intent)
    }

    fun initView() {
    }

    fun initData(intent: Intent?) {
        docUrl = intent?.getStringExtra(Constant.INTENT_DATA_KEY)
        docSourceType = intent?.getIntExtra(Constant.INTENT_SOURCE_KEY, 0) ?: 0
        fileType = intent?.getIntExtra(Constant.INTENT_TYPE_KEY, -1) ?: -1

        mDocView.openDoc(this, docUrl, docSourceType, fileType)
        mDocView.exportPdf()
        Log.e(javaClass.simpleName, "initData-docUrl = $docUrl")
        Log.e(javaClass.simpleName, "initData-docSourceType = $docSourceType")
        Log.e(javaClass.simpleName, "initData-fileType = $fileType")
    }
}