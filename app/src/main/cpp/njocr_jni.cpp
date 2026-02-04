#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <algorithm>
#include <android/log.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>

// NCNN
#include "net.h"
#include "cpu.h"

// OpenCV
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#define LOG_TAG "NJOCR_NATIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --- 修复 OpenMP 链接错误 ---
extern "C" {
void __kmpc_dispatch_deinit(void* loc, int gtid) {}
}

// 全局变量
static ncnn::Net det_net;
static ncnn::Net rec_net;
static std::vector<std::string> ocr_keys;
static bool is_loaded = false;

struct OCRResult {
    std::string text;
    float score;
    cv::Rect box;
};

// JSON 转义 (防止乱码导致JSON解析失败)
std::string json_escape(const std::string& s) {
    std::ostringstream o;
    for (auto c = s.cbegin(); c != s.cend(); c++) {
        switch (*c) {
            case '"': o << "\\\""; break;
            case '\\': o << "\\\\"; break;
            case '\b': o << "\\b"; break;
            case '\f': o << "\\f"; break;
            case '\n': o << "\\n"; break;
            case '\r': o << "\\r"; break;
            case '\t': o << "\\t"; break;
            default:
                if ('\x00' <= *c && *c <= '\x1f') {} else { o << *c; }
        }
    }
    return o.str();
}

cv::Mat bitmap_to_mat(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    void* pixels = 0;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return cv::Mat();
    }
    cv::Mat img_rgba(info.height, info.width, CV_8UC4, pixels);
    cv::Mat img_bgr;
    cv::cvtColor(img_rgba, img_bgr, cv::COLOR_RGBA2BGR);
    AndroidBitmap_unlockPixels(env, bitmap);
    return img_bgr.clone();
}

void load_keys(AAssetManager* mgr) {
    AAsset* asset = AAssetManager_open(mgr, "keys.txt", AASSET_MODE_BUFFER);
    if (!asset) return;
    off_t size = AAsset_getLength(asset);
    std::vector<char> buf(size);
    AAsset_read(asset, buf.data(), size);
    AAsset_close(asset);
    std::string content(buf.begin(), buf.end());
    std::istringstream iss(content);
    std::string line;
    ocr_keys.clear();
    ocr_keys.emplace_back("#");
    while (std::getline(iss, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        ocr_keys.push_back(line);
    }
}

std::string decode_ctc(const ncnn::Mat& out, float& avg_score) {
    std::string text;
    float score_sum = 0.f;
    int score_count = 0;
    int w = out.w;
    int h = out.h;
    int last_idx = -1;
    for (int i = 0; i < h; i++) {
        const float* row = out.row(i);
        int max_idx = 0;
        float max_val = row[0];
        for (int j = 1; j < w; j++) {
            if (row[j] > max_val) {
                max_val = row[j];
                max_idx = j;
            }
        }
        if (max_idx != last_idx && max_idx < ocr_keys.size()) {
            if (max_idx > 0 && max_idx < ocr_keys.size()) {
                text += ocr_keys[max_idx];
                score_sum += max_val;
                score_count++;
            }
        }
        last_idx = max_idx;
    }
    avg_score = (score_count > 0) ? (score_sum / score_count) : 0.f;
    return text;
}

// ================= JNI 接口 =================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wubugncnn_njocr_OCRService_initModels(JNIEnv* env, jobject thiz, jobject assetManager) {
    if (is_loaded) return JNI_TRUE;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;

    // ★★★ 智能判断架构 ★★★
#if defined(__i386__) || defined(__x86_64__)
    // ---> 模拟器环境 (x86)
    LOGI("NJOCR: Detected x86 Emulator. Forcing CPU FP32 for stability.");
    // 模拟器上强制关闭 GPU 和 FP16，解决乱码和崩溃问题
    // x86 CPU 跑 FP32 速度其实很快
    opt.use_vulkan_compute = false;
    opt.use_fp16_packed = false;
    opt.use_fp16_storage = false;
    opt.use_fp16_arithmetic = false;
#else
    // ---> 真机环境 (ARM)
        // 检查 GPU
        int gpu_count = ncnn::get_gpu_count();
        if (gpu_count > 0) {
            LOGI("NJOCR: Detected ARM Real Device with GPU. Enhancing speed.");
            opt.use_vulkan_compute = true;  // 开启 GPU
            opt.use_fp16_packed = true;     // 开启 FP16
            opt.use_fp16_storage = true;
            opt.use_fp16_arithmetic = true;
        } else {
            LOGI("NJOCR: ARM CPU Mode.");
            opt.use_vulkan_compute = false;
            // ARM CPU 通常支持 FP16，可以开启以提速，如果不行请改为 false
            opt.use_fp16_packed = true;
        }
#endif

    det_net.opt = opt;
    rec_net.opt = opt;

    int ret1 = det_net.load_param(mgr, "det.param");
    int ret2 = det_net.load_model(mgr, "det.bin");
    int ret3 = rec_net.load_param(mgr, "rec.param");
    int ret4 = rec_net.load_model(mgr, "rec.bin");

    if (ret1 != 0 || ret2 != 0 || ret3 != 0 || ret4 != 0) {
        LOGE("Failed to load models!");
        return JNI_FALSE;
    }

    load_keys(mgr);
    is_loaded = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wubugncnn_njocr_OCRService_nativeProcessBitmap(JNIEnv* env, jobject thiz, jobject bitmap, jint type) {
    if (!is_loaded) return env->NewStringUTF("{\"status\":500,\"msg\":\"Models not loaded\"}");

    cv::Mat src = bitmap_to_mat(env, bitmap);
    if (src.empty()) return env->NewStringUTF("{\"status\":500,\"msg\":\"Bitmap lock failed\"}");

    int img_w = src.cols;
    int img_h = src.rows;

    // ================= DET 预处理 (核心优化点) =================

    // 默认配置（针对手机 ARM）
    int max_side = 960;
    float box_thresh = 0.3f;
    float unclip_ratio = 1.6f; // 稍微扩大一点文字框

    // ★★★ 针对模拟器 (x86) 的画质增强 ★★★
#if defined(__i386__) || defined(__x86_64__)
    // 电脑 CPU 很强，我们可以把图片放得更大，看得更清
    // 建议设为 1600 或 1920。如果你的模拟器是 2K 分辨率，可以设为 2000
    max_side = 1920;

    // 电脑屏幕截图通常很干净，可以适当降低阈值，捞回更淡的字
    box_thresh = 0.25f;

    // 电脑字体通常比较方正，稍微加大外扩比例，防止边缘缺失
    unclip_ratio = 1.8f;

    LOGI("NJOCR: x86 HD Mode Activated. MaxSide=%d", max_side);
#endif

    // 计算缩放比例
    float scale = 1.f;
    if (std::max(img_w, img_h) > max_side) {
        scale = (float)max_side / std::max(img_w, img_h);
    }

    int w_target = (int)(img_w * scale);
    int h_target = (int)(img_h * scale);

    // 32倍数对齐
    w_target = (w_target + 31) / 32 * 32;
    h_target = (h_target + 31) / 32 * 32;

    float scale_x = (float)w_target / img_w;
    float scale_y = (float)h_target / img_h;

    ncnn::Mat in_det = ncnn::Mat::from_pixels_resize(src.data, ncnn::Mat::PIXEL_BGR2RGB, img_w, img_h, w_target, h_target);
    const float mean_vals[3] = {123.675f, 116.28f, 103.53f};
    const float norm_vals[3] = {1.0f / 58.395f, 1.0f / 57.12f, 1.0f / 57.375f};
    in_det.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = det_net.create_extractor();
    ex.input(det_net.input_names()[0], in_det);
    ncnn::Mat out_det;
    ex.extract(det_net.output_names()[0], out_det);

    cv::Mat pred_map(out_det.h, out_det.w, CV_32F, (float*)out_det.data);
    cv::Mat binary_map;
    // 使用动态配置的阈值
    cv::threshold(pred_map, binary_map, box_thresh, 255, cv::THRESH_BINARY);
    binary_map.convertTo(binary_map, CV_8UC1);

    // 膨胀操作：让断裂的字连起来
    // 模拟器图大，核可以稍微大一点点 (3x3 依然通用)
    cv::Mat dilation_kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3));
    cv::dilate(binary_map, binary_map, dilation_kernel);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(binary_map, contours, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

    std::vector<OCRResult> results;

    // ================= REC 流程 (识别) =================
    for (const auto& cnt : contours) {
        if (cv::contourArea(cnt) < 16) continue;
        cv::Rect box = cv::boundingRect(cnt);

        int x = (int)(box.x / scale_x);
        int y = (int)(box.y / scale_y);
        int w = (int)(box.width / scale_x);
        int h = (int)(box.height / scale_y);

        // 使用动态配置的 unclip_ratio 进行扩框
        // 比如 w * 0.1 变成 w * 0.15 左右
        int pad_w = (int)(w * (unclip_ratio - 1.0f) * 0.5f);
        int pad_h = (int)(h * (unclip_ratio - 1.0f) * 0.5f);

        // 稍微做个限制，防止 pad 太大
        pad_w = std::max(pad_w, 2);
        pad_h = std::max(pad_h, 2);

        x = std::max(0, x - pad_w);
        y = std::max(0, y - pad_h);
        w = std::min(img_w - x, w + pad_w * 2);
        h = std::min(img_h - y, h + pad_h * 2);

        cv::Mat roi = src(cv::Rect(x, y, w, h)).clone();

        float ratio = (float)roi.cols / (float)roi.rows;
        int rec_w = (int)(48 * ratio);

        // 模拟器上，允许更长的文本条，防止长句被压缩过度
        // 手机限制 960，模拟器可以放宽到 1280
#if defined(__i386__) || defined(__x86_64__)
        rec_w = std::min(rec_w, 1280);
#else
        rec_w = std::min(rec_w, 960);
#endif
        rec_w = std::max(rec_w, 48);

        ncnn::Mat in_rec = ncnn::Mat::from_pixels_resize(roi.data, ncnn::Mat::PIXEL_BGR2RGB, roi.cols, roi.rows, rec_w, 48);
        const float mean_rec[3] = {127.5f, 127.5f, 127.5f};
        const float norm_rec[3] = {1.0f / 127.5f, 1.0f / 127.5f, 1.0f / 127.5f};
        in_rec.substract_mean_normalize(mean_rec, norm_rec);

        ncnn::Extractor ex_rec = rec_net.create_extractor();
        // 模拟器 CPU 强，可以关闭 light_mode 换取微弱的精度提升
#if defined(__i386__) || defined(__x86_64__)
        ex_rec.set_light_mode(false);
#else
        ex_rec.set_light_mode(true);
#endif

        ex_rec.input(rec_net.input_names()[0], in_rec);
        ncnn::Mat out_rec;
        ex_rec.extract(rec_net.output_names()[0], out_rec);

        float score = 0;
        std::string text = decode_ctc(out_rec, score);
        if (!text.empty() && score > 0.5f) {
            results.push_back({text, score, cv::Rect(x, y, w, h)});
        }
    }

    std::sort(results.begin(), results.end(), [](const OCRResult& a, const OCRResult& b) {
        if (std::abs(a.box.y - b.box.y) < 20) return a.box.x < b.box.x;
        return a.box.y < b.box.y;
    });

    // ================= JSON 封装 =================
    std::stringstream json;

    if (type == 1) {
        json << "{\"status\":200,\"data\":\"";
        for (size_t i = 0; i < results.size(); ++i) {
            json << json_escape(results[i].text);
            if (i < results.size() - 1) json << "\\n";
        }
        json << "\"}";
    }
    else if (type == 2) {
        json << "{\"status\":200,\"data\":[";
        for (size_t i = 0; i < results.size(); ++i) {
            const auto& res = results[i];
            int cx = res.box.x + res.box.width / 2;
            int cy = res.box.y + res.box.height / 2;
            json << "{\"text\":\"" << json_escape(res.text) << "\",";
            json << "\"x\":" << cx << ",";
            json << "\"y\":" << cy << "}";
            if (i < results.size() - 1) json << ",";
        }
        json << "]}";
    }
    else {
        json << "{\"status\":200,\"data\":[";
        for (size_t i = 0; i < results.size(); ++i) {
            const auto& res = results[i];
            json << "{\"text\":\"" << json_escape(res.text) << "\",";
            json << "\"score\":" << res.score << ",";
            json << "\"x\":" << res.box.x << ",";
            json << "\"y\":" << res.box.y << ",";
            json << "\"w\":" << res.box.width << ",";
            json << "\"h\":" << res.box.height << "}";
            if (i < results.size() - 1) json << ",";
        }
        json << "]}";
    }

    return env->NewStringUTF(json.str().c_str());
}