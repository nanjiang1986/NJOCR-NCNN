cmake_minimum_required(VERSION 3.18.1)

project("njocr")

# 1. 开启 OpenMP 并强制静态链接 (防止 libomp.so not found 导致闪退)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fopenmp")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fopenmp")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fopenmp -static-openmp")

# 2. 配置 NCNN
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn_sdk/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# 3. 手动配置 OpenCV 核心库
set(OPENCV_LIB_DIR ${CMAKE_SOURCE_DIR}/opencv_sdk/libs/${ANDROID_ABI})

add_library(libopencv_imgproc STATIC IMPORTED)
set_target_properties(libopencv_imgproc PROPERTIES IMPORTED_LOCATION ${OPENCV_LIB_DIR}/libopencv_imgproc.a)

add_library(libopencv_core STATIC IMPORTED)
set_target_properties(libopencv_core PROPERTIES IMPORTED_LOCATION ${OPENCV_LIB_DIR}/libopencv_core.a)

# 4. 创建我们的 JNI 库
add_library(njocr SHARED njocr_jni.cpp)

# 5. 头文件路径
target_include_directories(njocr PRIVATE 
    ${CMAKE_SOURCE_DIR}/ncnn_sdk/${ANDROID_ABI}/include/ncnn
    ${CMAKE_SOURCE_DIR}/ncnn_sdk/${ANDROID_ABI}/include
    ${CMAKE_SOURCE_DIR}/opencv_sdk/include
)

find_library(log-lib log)

# 6. 链接所有库 (不再显式链接 omp，改用 flags 里的 -static-openmp)
target_link_libraries(njocr
    ncnn
    libopencv_imgproc
    libopencv_core
    ${log-lib}
    jnigraphics
    dl
    z
)
