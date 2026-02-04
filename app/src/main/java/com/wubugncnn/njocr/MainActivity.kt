package com.wubugncnn.njocr

import android.Manifest
import android.app.ActivityManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    // UI 控件
    private lateinit var tvTitle: TextView
    private lateinit var statusDot: View
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnAuth: Button

    private val tabs = arrayOf("接口文档", "软件更新", "打赏支持")
    private val subTabs = arrayOf("参数说明", "按键精灵", "Auto.js", "懒人精灵", "EC", "AutoGo")

    // 配色方案
    private val COLOR_PRIMARY = Color.parseColor("#2C3E50")
    private val COLOR_ACCENT = Color.parseColor("#3498DB")
    private val COLOR_BTN_START = Color.parseColor("#27AE60") // 绿色
    private val COLOR_BTN_STOP = Color.parseColor("#E74C3C")  // 红色
    private val COLOR_BTN_AUTH = Color.parseColor("#F39C12")  // 橙色
    private val COLOR_BTN_BG = Color.parseColor("#34495E")
    private val COLOR_CODE_BG = Color.parseColor("#2B2B2B")
    private val COLOR_DISABLED = Color.parseColor("#BDC3C7")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.elevation = 0f
        window.statusBarColor = COLOR_PRIMARY
        setContentView(createRootLayout())
        updateStatusUI()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 100)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                requestStoragePermission()
            }
        }
    }

    // --- 根布局 ---
    private fun createRootLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F7F6"))
            fitsSystemWindows = true
        }

        // 1. 顶部 Header
        root.addView(createSingleRowHeader())

        // 2. 主 Tab (高度压缩)
        val tabLayout = TabLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setSelectedTabIndicatorColor(COLOR_ACCENT)
            tabMode = TabLayout.MODE_FIXED
            layoutParams = LinearLayout.LayoutParams(-1, 110)
        }
        root.addView(tabLayout)

        // 3. ViewPager
        val viewPager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            adapter = MainTabAdapter()
        }
        root.addView(viewPager)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabs[position]
        }.attach()

        return root
    }

    // --- 单行 Header 构建 (按钮顺序已调整) ---
    private fun createSingleRowHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(25, 20, 25, 20)
            setBackgroundColor(COLOR_PRIMARY)
            gravity = Gravity.CENTER_VERTICAL
        }

        // 左侧：状态点 + 标题
        val leftLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(22, 22).apply { rightMargin = 15 }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
        }
        leftLayout.addView(statusDot)

        tvTitle = TextView(this).apply {
            text = "南江 NJOCR NCNN 服务端"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        leftLayout.addView(tvTitle)
        header.addView(leftLayout)

        // 右侧：三个按钮 (顺序：启动 -> 停止 -> 权限)

        // 1. 启动
        btnStart = createMiniBtn("启动", COLOR_BTN_START) { startOcrService() }
        header.addView(btnStart)

        // 间隔
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(15, 1) })

        // 2. 停止
        btnStop = createMiniBtn("停止", COLOR_BTN_STOP) { stopOcrService() }
        header.addView(btnStop)

        // 间隔
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(15, 1) })

        // 3. 文件权限 (独立)
        btnAuth = createMiniBtn("授予文件权限", COLOR_BTN_AUTH) { requestStoragePermission() }
        header.addView(btnAuth)

        return header
    }

    private fun createMiniBtn(text: String, color: Int, l: View.OnClickListener): Button {
        return Button(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            // 根据文字长度自动调整宽度，稍微宽一点好看
            val w = if(text.length > 4) 200 else 110
            layoutParams = LinearLayout.LayoutParams(w, 75)
            setPadding(0, 0, 0, 0)
            tag = color
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 10f
            }
            setOnClickListener(l)
        }
    }

    // --- 状态与按钮控制 ---
    private fun updateStatusUI() {
        val isRunning = isServiceRunning(OCRService::class.java)

        if (isRunning) {
            statusDot.background = (statusDot.background as GradientDrawable).apply { setColor(Color.GREEN) }
            // 运行中：启动不可点，停止可点
            setBtnState(btnStart, false)
            setBtnState(btnStop, true)
        } else {
            statusDot.background = (statusDot.background as GradientDrawable).apply { setColor(Color.RED) }
            // 停止中：启动可点，停止不可点
            setBtnState(btnStart, true)
            setBtnState(btnStop, false)
        }
        // 权限按钮永远保持高亮可点，不参与互斥逻辑
        setBtnState(btnAuth, true)
    }

    private fun setBtnState(btn: Button, enable: Boolean) {
        btn.isEnabled = enable
        val bg = btn.background as GradientDrawable
        if (enable) {
            bg.setColor(btn.tag as Int)
            btn.setTextColor(Color.WHITE)
            btn.alpha = 1.0f
        } else {
            bg.setColor(COLOR_DISABLED)
            btn.setTextColor(Color.parseColor("#7F8C8D"))
            btn.alpha = 1.0f // 保持不透明，只变灰
        }
    }

    private fun startOcrService() {
        val intent = Intent(this, OCRService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // 延时刷新 UI
        tvTitle.postDelayed({ updateStatusUI() }, 500)
    }

    private fun stopOcrService() {
        stopService(Intent(this, OCRService::class.java))
        tvTitle.postDelayed({ updateStatusUI() }, 500)
    }

    // --- 主 Adapter ---
    private inner class MainTabAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): RecyclerView.ViewHolder {
            val frame = FrameLayout(p.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
            }
            return object : RecyclerView.ViewHolder(frame) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            val container = h.itemView as ViewGroup
            container.removeAllViews()
            when (pos) {
                0 -> buildDocPage(container)
                1 -> buildUpdatePage(container)
                2 -> buildDonatePage(container)
            }
        }
        override fun getItemCount(): Int = tabs.size
    }

    // --- 接口文档页 ---
    private fun buildDocPage(parent: ViewGroup) {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        // 子 Tab
        val subTabLayout = TabLayout(parent.context).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            setBackgroundColor(Color.parseColor("#ECF0F1"))
            setSelectedTabIndicatorColor(COLOR_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(-1, 90)
        }
        layout.addView(subTabLayout)

        val subViewPager = ViewPager2(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            adapter = SubTabAdapter()
        }
        layout.addView(subViewPager)

        TabLayoutMediator(subTabLayout, subViewPager) { tab, position ->
            tab.text = subTabs[position]
        }.attach()

        parent.addView(layout)
    }

    private inner class SubTabAdapter : RecyclerView.Adapter<SubTabAdapter.ViewHolder>() {
        inner class ViewHolder(val scrollView: ScrollView) : RecyclerView.ViewHolder(scrollView)

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): ViewHolder {
            val sv = ScrollView(p.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                isFillViewport = true
            }
            return ViewHolder(sv)
        }

        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val content = LinearLayout(h.itemView.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 20, 30, 100)
            }

            when (pos) {
                0 -> showIntroPage(content) // 参数说明
                1 -> showAnJianCode(content)
                2 -> showAutoJsCode(content)
                3 -> showLazyCode(content)
                4 -> showEcCode(content)
                5 -> showAutoGoCode(content)
            }

            h.scrollView.removeAllViews()
            h.scrollView.addView(content)
        }
        override fun getItemCount(): Int = subTabs.size
    }

    // 1. 参数说明
    private fun showIntroPage(layout: ViewGroup) {
        addText(layout, "内核: NCNN + PP-OCRv5", 14, true)

        addSection(layout, "基本信息")
        addText(layout, "URL: http://127.0.0.1:1666", 13, false)
        addText(layout, "Method: POST", 13, false)

        addSection(layout, "参数详解")
        addText(layout, "path (必填):", 13, true)
        addText(layout, "  图片的绝对路径，例如 /sdcard/1.png", 12, false)

        addText(layout, "type (选填，默认1):", 13, true)
        addText(layout, "  1 : 纯文本 (换行符分隔)", 12, false)
        addText(layout, "  2 : Json数组 (文字 + 中心坐标)", 12, false)
        addText(layout, "  3 : 详细Json (文字+置信度+矩形)", 12, false)
    }

    // 2. 按键精灵 (已替换为你提供的代码)
    private fun showAnJianCode(layout: ViewGroup) {
        addSection(layout, "显式调用 (需安装APP)")
        // 这里完全使用你提供的代码文本
        val code1 = """
'NJOCR  基于PaddleOCR v5 ncnn
'南江软件开发   v: nj666188   Q: 1192023693
Import "ShanHai.lua"
'后台服务初始化
TracePrint ShanHai.Execute("am start-foreground-service com.wubugncnn.njocr/.OCRService")
Delay 2000'第一次等待2秒 加载
Dim path = "/sdcard/test.png"
SnapShot path, 0, 0, 0, 0 
Dim urls= "http://127.0.0.1:1666"
Dim postData = "path=" & path & "&type=1"
Dim requestTable
requestTable = { _
    "url": urls, _
    "data": postData, _
    "timeout": 20 _
}
dim t1=TickCount()
Dim result = Url.HttpPost(requestTable)
TracePrint "识别耗时",(TickCount()-t1),"毫秒"
TracePrint "返回: " & result
//dim tb1=Encode.JsonToTable(result)
//If CInt(tb1["status"]) = 200 Then 
//    TracePrint tb1["data"]
//End If
        """.trimIndent()
        addCode(layout, code1)

        addSection(layout, "隐式调用 (无图标版)")
        addCode(layout, "' 待更新...")
    }

    // 3. Auto.js
    private fun showAutoJsCode(layout: ViewGroup) {
        addSection(layout, "显式调用")
        val code1 = """
if (!requestScreenCapture()) {
    toast("无权限"); exit();
}
sleep(1000);

var path = "/sdcard/test.png";
captureScreen(path);

var url = "http://127.0.0.1:1666";
var res = http.post(url, {
    "path": path, "type": "1" 
});

if (res.statusCode == 200) {
    var json = JSON.parse(res.body.string());
    if (json.status == 200) {
        log("结果: " + json.data);
        toast(json.data);
    } else {
        log("错误: " + json.data);
    }
}
        """.trimIndent()
        addCode(layout, code1)
    }

    private fun showLazyCode(layout: ViewGroup) {
        addSection(layout, "懒人精灵")
        addCode(layout, "-- 占位...")
    }
    private fun showEcCode(layout: ViewGroup) {
        addSection(layout, "EasyClick")
        addCode(layout, "// 占位...")
    }
    private fun showAutoGoCode(layout: ViewGroup) {
        addSection(layout, "AutoGo")
        addCode(layout, "// 占位...")
    }

    // --- 更新日志页面 ---
    private fun buildUpdatePage(parent: ViewGroup) {
        val sv = ScrollView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            isFillViewport = true
        }
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
        }
        sv.addView(layout)

        addText(layout, "版本: v1.0.0 (NCNN Release)", 16, true)
        addText(layout, "作者：南江 (nj666188)\n邮箱：1192023693@qq.com\n基于 NCNN 开发 | 永久免费本地识别", 13, false)

        addSection(layout, "官方交流群 (点击加入)")
        val grid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val groups = arrayOf(
            arrayOf("一群: 628241843", "628241843"),
            arrayOf("二群: 939905851", "939905851"),
            arrayOf("三群: 1005046778", "1005046778"),
            arrayOf("四群: 159022400", "159022400")
        )

        for (g in groups) {
            val b = Button(this).apply {
                text = g[0]
                textSize = 11f
                setTextColor(Color.WHITE)
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(COLOR_BTN_BG)
                    cornerRadius = 15f
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 120
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(10, 15, 10, 15)
                }
                setOnClickListener { joinQQ(g[1]) }
            }
            grid.addView(b)
        }
        layout.addView(grid)
        parent.addView(sv)
    }

    // --- 打赏页面 ---
    // --- 打赏页面 (关键修改：横向排列) ---
    private fun buildDonatePage(parent: ViewGroup) {
        val sv = ScrollView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            isFillViewport = true
        }
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        sv.addView(layout)

        addText(layout, "如果您觉得本工具对您有帮助，欢迎打赏支持！\n点击图片可查看大图。", 13, true)

        // ★★★ 核心修改：改为 HORIZONTAL，实现左右并排 ★★★
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL // 改为横向
            setPadding(0, 50, 0, 0)
            gravity = Gravity.CENTER // 整体居中
        }
        row.addView(createQRItem("微信支付", "pay_wx"))

        // 中间加个间隔
        row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(40, 1) })

        row.addView(createQRItem("支付宝支付", "pay_ali"))
        layout.addView(row)

        parent.addView(sv)
    }

    private fun createQRItem(name: String, resName: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // 调整 Padding 让它在横排时看起来更协调
            setPadding(10, 10, 10, 10)
        }
        val img = ImageView(this).apply {
            // 稍微调小一点尺寸，确保小屏幕也能并排显示两个
            layoutParams = LinearLayout.LayoutParams(320, 320)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }

        var id = resources.getIdentifier(resName, "drawable", packageName)
        if (id == 0) id = resources.getIdentifier(resName, "mipmap", packageName)

        if (id != 0) {
            img.setImageResource(id)
            img.setOnClickListener { showLargeImage(id) }
        } else {
            img.setBackgroundColor(Color.LTGRAY)
        }

        box.addView(img)
        val l = TextView(this).apply {
            text = name
            setPadding(0, 15, 0, 0)
            setTextColor(COLOR_PRIMARY)
            textSize = 12f
        }
        box.addView(l)
        return box
    }



    private fun showLargeImage(resId: Int) {
        if (resId == 0) return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val iv = ImageView(this).apply {
            setImageResource(resId)
            adjustViewBounds = true
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.BLACK)
            setOnClickListener { dialog.dismiss() }
        }

        dialog.setContentView(iv)
        dialog.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.parseColor("#E6000000")))
            it.setLayout(-1, -1)
        }
        dialog.show()
    }

    // --- UI 工具 ---
    private fun addText(l: ViewGroup, t: String, s: Int, b: Boolean) {
        val v = TextView(this).apply {
            text = t
            textSize = s.toFloat()
            if (b) setTypeface(null, Typeface.BOLD)
            setTextColor(COLOR_PRIMARY)
            setPadding(0, 10, 0, 5)
        }
        l.addView(v)
    }

    private fun addSection(l: ViewGroup, t: String) {
        val v = TextView(this).apply {
            text = t
            setPadding(0, 40, 0, 15)
            setTextColor(COLOR_ACCENT)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        l.addView(v)
    }

    private fun addCode(l: ViewGroup, c: String) {
        val f = FrameLayout(this).apply {
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                setColor(COLOR_CODE_BG)
                cornerRadius = 12f
            }
        }
        val v = TextView(this).apply {
            text = c
            setTextColor(Color.parseColor("#A9B7C6"))
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setLineSpacing(0f, 1.2f)
            setTextIsSelectable(true)
        }
        f.addView(v)
        val b = Button(this).apply {
            text = "复制"
            textSize = 10f
            setTextColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(120, 70, Gravity.TOP or Gravity.END)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DDDDDD"))
                cornerRadius = 8f
                alpha = 200
            }
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("code", c))
                Toast.makeText(this@MainActivity, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
        f.addView(b)
        l.addView(f)
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("需要文件访问权限")
                    .setMessage("请开启“所有文件访问权限”以读取截图。")
                    .setPositiveButton("去开启") { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
    }

    private fun isServiceRunning(cls: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (cls.name == service.service.className) return true
        }
        return false
    }

    private fun joinQQ(uin: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$uin&card_type=group&source=qrcode")))
        } catch (e: Exception) {
            Toast.makeText(this, "跳转QQ失败，请手动添加", Toast.LENGTH_SHORT).show()
        }
    }
}