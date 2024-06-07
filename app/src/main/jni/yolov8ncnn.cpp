// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "yolo.h"

#include "ndkcamera.h"
#include "yolo-pose.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/highgui/highgui.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}
JavaVM *jvm = nullptr;
jobject jobj = nullptr;
std::mutex	mMutexMethod;
static jmethodID reqCall(JNIEnv **env, const char *name, const char *args)
{
    JNIEnv *jniEnv = nullptr;
    jclass javaClass = nullptr;
    jmethodID javaCallback = nullptr;

    mMutexMethod.lock();

    if(jvm->AttachCurrentThread(&jniEnv, nullptr) != JNI_OK || nullptr == jniEnv) {
        mMutexMethod.unlock();
        return nullptr;
    }

    javaClass = jniEnv->GetObjectClass(jobj);
    if(nullptr == javaClass){
        jvm->DetachCurrentThread();
        mMutexMethod.unlock();
        return nullptr;
    }

    javaCallback = jniEnv->GetMethodID(javaClass, name, args);
    if(nullptr == javaCallback){
        jvm->DetachCurrentThread();
        mMutexMethod.unlock();
        return nullptr;
    }
    *env = jniEnv;

    return javaCallback;
}

static void resCall()
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "resCall start");
//    jvm->DetachCurrentThread();
    mMutexMethod.unlock();
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "resCall end");
}

static Yolo_pose* g_yolo = 0;
static ncnn::Mutex lock;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // nanodet
    {
        ncnn::MutexLockGuard g(lock);
        if (g_yolo)
        {
            std::vector<Object_pose> objects;
            g_yolo->detect(rgb, objects);

            int** array = g_yolo->draw(rgb, objects);
            JNIEnv *env = nullptr;
            jmethodID javaCallback = nullptr;
            //  对应 Java --> private void onJniNoticeA(float[] f1, float[] f2)
            if((javaCallback = reqCall(&env, "onData", "([I)V"))){
                jintArray jf1 = env->NewIntArray(51);
                env->SetIntArrayRegion(jf1, 0, 51, array[0]);
                env->CallVoidMethod(jobj, javaCallback, jf1);
                resCall();
            }

        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolo;
        g_yolo = 0;
    }

    delete g_camera;
    g_camera = 0;
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_example_onviftest_Yolov8Ncnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint modelid, jint cpugpu)
{
    env->GetJavaVM(&jvm);
    jobj = env->NewGlobalRef(thiz);

    if (modelid < 0 || modelid > 6 || cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* modeltypes[] =
    {
        "best-sim-opt",
        "n",
        "s",
    };

    const int target_sizes[] =
    {
        320,
        320,
    };

    const float mean_vals[][3] =
    {
        {103.53f, 116.28f, 123.675f},
        {103.53f, 116.28f, 123.675f},
    };

    const float norm_vals[][3] =
    {
        { 1 / 255.f, 1 / 255.f, 1 / 255.f },
        { 1 / 255.f, 1 / 255.f, 1 / 255.f },
    };

    const char* modeltype = modeltypes[(int)modelid];
    int target_size = target_sizes[(int)modelid];
    bool use_gpu = (int)cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            // no gpu
            delete g_yolo;
            g_yolo = 0;
        }
        else
        {
            if (!g_yolo)
                g_yolo = new Yolo_pose;
            g_yolo->load(mgr, modeltype, target_size, mean_vals[(int)modelid], norm_vals[(int)modelid], use_gpu);
        }
    }

    return JNI_TRUE;
}
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_onviftest_Yolov8Ncnn_checkImagePose(JNIEnv *env, jobject thiz, jstring path) {
    // TODO: implement checkImagePose()
    const char *imgPath = env->GetStringUTFChars(path, 0);
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "cv::imread path: %s",imgPath);
//    const char *imgPath = "/storage/emulated/0/person.jpg";
    cv::Mat m = cv::imread(imgPath, 1);
    if (m.empty())
    {
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "cv::imread is null:%s",imgPath);
        return;
    }
    ncnn::MutexLockGuard g(lock);
    if (g_yolo)
    {
        std::vector<Object_pose> objects;
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "detect start");
        g_yolo->detect(m, objects);
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "detect end");

        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getDirectPoints start");
        int** array = g_yolo->getDirectPoints(objects);
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getDirectPoints end");
        JNIEnv *env = nullptr;
        jmethodID javaCallback = nullptr;
        //  对应 Java --> private void onJniNoticeA(float[] f1, float[] f2)
        if((javaCallback = reqCall(&env, "onData", "([[I)V"))){
            jclass intArrayClass = env->FindClass("[I");
            jobjectArray matrixJava = env->NewObjectArray(objects.size(), intArrayClass, NULL);
            for (int i = 0; i < objects.size(); ++i) {
                jintArray row = env->NewIntArray(55);
                env->SetIntArrayRegion(row, 0, 55, array[i]);
                env->SetObjectArrayElement(matrixJava, i, row);
                env->DeleteLocalRef(row);
            }

//            jintArray jf1 = env->NewIntArray(55);
//            env->SetIntArrayRegion(jf1, 0, 55, array[0]);
            env->CallVoidMethod(jobj, javaCallback, matrixJava);
            resCall();
        }
        delete[] array;
    }
}