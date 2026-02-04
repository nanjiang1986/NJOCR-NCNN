package com.wubugncnn.njocr

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.*
import java.io.File

class OCRService : Service() {
    private var server: NettyApplicationEngine? = null
    // 使用 SupervisorJob 防止一个协程崩溃导致整个 Scope 取消
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        init {
            // 1. 加载 Native 库
            System.loadLibrary("njocr")

            // 2. 【关键修复】强制设置 Netty 使用 JDK 自带日志
            // 你的日志里全是 Log4j 找不到的错误，这会导致 Ktor 启动失败
            System.setProperty("io.netty.noUnsafe", "true")
            System.setProperty("io.netty.logger.provider", "JDK")
        }
    }

    // 【关键修复】去掉 'private'！
    // JNI 方法如果是 private 的，有时候在 Native 层会找不到，导致 UnsatisfiedLinkError
    external fun initModels(assetManager: AssetManager): Boolean
    external fun nativeProcessBitmap(bitmap: Bitmap, type: Int): String

    override fun onCreate() {
        super.onCreate()
        Log.i("NJOCR", "Service Creating...")

        // 必须先启动前台通知，防止服务被系统杀掉
        startForegroundService()

        // 在 IO 线程初始化模型
        serviceScope.launch(Dispatchers.Default) {
            try {
                Log.i("NJOCR", "开始初始化模型...")
                val success = initModels(assets)
                if (success) {
                    Log.i("NJOCR", "模型初始化成功！")
                } else {
                    Log.e("NJOCR", "模型初始化失败，请检查 assets 文件！")
                }
            } catch (e: UnsatisfiedLinkError) {
                // 捕获链接错误，打印详细信息
                Log.e("NJOCR", "JNI 链接失败: ${e.message}")
            } catch (e: Exception) {
                Log.e("NJOCR", "模型初始化异常: ${e.message}")
            }
        }

        startKtorServer()
    }

    private fun startForegroundService() {
        val channelId = "ocr_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "NJOCR Service", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("NJOCR 服务端运行中")
            .setContentText("监听地址: 127.0.0.1:1666")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    private fun startKtorServer() {
        serviceScope.launch {
            try {
                server = embeddedServer(Netty, port = 1666, host = "127.0.0.1") {
                    install(ContentNegotiation) { gson { setPrettyPrinting() } }
                    routing {
                        post("/") {
                            try {
                                // 兼容 Form-Data 和 JSON
                                val params = call.receiveParameters()
                                var imagePath = params["path"]
                                var typeStr = params["type"]

                                // 如果 Form-Data 为空，尝试解析 JSON
                                if (imagePath == null) {
                                    try {
                                        val map = call.receive<Map<String, Any>>()
                                        imagePath = map["path"]?.toString()
                                        typeStr = map["type"]?.toString()
                                    } catch (e: Exception) {
                                        // 忽略 JSON 解析错误
                                    }
                                }

                                val recognitionType = typeStr?.toIntOrNull() ?: 1

                                if (imagePath.isNullOrEmpty()) {
                                    call.respondText("{\"status\": 400, \"data\": \"Error: path is required\"}")
                                    return@post
                                }

                                val file = File(imagePath)
                                if (!file.exists()) {
                                    call.respondText("{\"status\": 404, \"data\": \"Error: File not found at $imagePath\"}")
                                    return@post
                                }

                                val bitmap = BitmapFactory.decodeFile(imagePath)
                                if (bitmap == null) {
                                    call.respondText("{\"status\": 500, \"data\": \"Error: Failed to decode image\"}")
                                    return@post
                                }

                                // 调用 Native OCR
                                val result = nativeProcessBitmap(bitmap, recognitionType)
                                bitmap.recycle() // 立即回收内存

                                call.respondText(result)

                            } catch (e: Exception) {
                                e.printStackTrace()
                                call.respondText("{\"status\": 500, \"data\": \"Error: ${e.message}\"}")
                            }
                        }
                    }
                }.start(wait = false)
                Log.i("NJOCR", "Ktor 服务器已启动: 127.0.0.1:1666")
            } catch (e: Exception) {
                Log.e("NJOCR", "Ktor 启动失败: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            server?.stop(100, 100)
        } catch (e: Exception) {}
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}