# CMakeLists.txt — Native 层构建配置
#
# 本文件定义了 JNI 桥接层的编译规则和 FFmpeg 库的链接方式
#
# 构建链路：
#   Kotlin 代码 → JNI 调用 → native-lib.so → FFmpeg .so 库
#
# FFmpeg 库的两种接入方式：
#   方式A（推荐）: 预编译的 .so 放在 jniLibs/ 目录
#   方式B: 通过 ExternalNativeBuild 在编译时从源码构建

cmake_minimum_required(VERSION 3.22.1)
project("clipforge_native")

# ===== JNI 桥接库 =====
# 我们自己写的 C 代码，编译为 libclipforge_native.so
add_library(clipforge_native SHARED
    native-lib.c        # FFmpeg C API 调用入口
    ffmpeg_cmd.c        # 命令行模式封装
)

# 链接 Android 日志库（用于 __android_log_print）
find_library(log-lib log)
target_link_libraries(clipforge_native ${log-lib})

# ===== FFmpeg 预编译库链接（方式A） =====
# 如果已经编译好 FFmpeg 并放到了 jniLibs 目录，取消下面的注释
#
# FFmpeg 库说明：
#   libavformat.so  — 解封装/封装（读写 MP4/MKV 等容器）
#   libavcodec.so   — 编解码（H.264/AAC 等）
#   libavutil.so    — 通用工具
#   libavfilter.so  — 滤镜框架
#   libswresample.so— 音频重采样
#   libswscale.so   — 图像缩放

# set(FFMPEG_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
# set(FFMPEG_INCLUDE "${CMAKE_SOURCE_DIR}/include")
#
# include_directories(${FFMPEG_INCLUDE})
#
# # 逐个声明预编译库
# add_library(avformat SHARED IMPORTED)
# set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/libavformat.so)
#
# add_library(avcodec SHARED IMPORTED)
# set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/libavcodec.so)
#
# add_library(avutil SHARED IMPORTED)
# set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/libavutil.so)
#
# add_library(avfilter SHARED IMPORTED)
# set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/libavfilter.so)
#
# add_library(swresample SHARED IMPORTED)
# set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/libswresample.so)
#
# add_library(swscale SHARED IMPORTED)
# set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/libswscale.so)
#
# # 链接所有 FFmpeg 库到我们的 JNI 库
# target_link_libraries(clipforge_native
#     avformat avcodec avutil avfilter swresample swscale
#     ${log-lib}
# )
