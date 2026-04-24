#!/bin/bash
#
# FFmpeg Android 交叉编译脚本（学习参考）
#
# 前置条件：
#   1. Linux/macOS 环境（Windows 用 WSL 或 Git Bash）
#   2. Android NDK 已下载（推荐 r26+）
#   3. FFmpeg 源码已下载（git clone https://git.ffmpeg.org/ffmpeg.git）
#
# 使用方法：
#   export NDK_PATH=/path/to/android-ndk
#   export FFMPEG_SRC=/path/to/ffmpeg
#   bash build_ffmpeg.sh
#
# 产出目录：output/arm64-v8a/lib/*.so + output/arm64-v8a/include/*
#
# ============================================================================

set -e

# ===== 环境配置 =====
NDK_PATH=${NDK_PATH:-"$HOME/Android/Sdk/ndk/26.1.10909125"}
FFMPEG_SRC=${FFMPEG_SRC:-"$HOME/ffmpeg"}
OUTPUT_DIR="$(pwd)/output"
API_LEVEL=24  # minSdk=24，对应 Android 7.0

# NDK 工具链路径（统一 LLVM 工具链，NDK r19+ 不再区分 gcc/clang）
TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"

# ===== 编译目标 =====
# 可以循环编译多个架构，这里只编 arm64-v8a（主流手机都是）
ARCHS=(
    "arm64-v8a"
    # "armeabi-v7a"   # 32位 ARM，老设备
    # "x86_64"        # 模拟器
)

build_one() {
    local ARCH=$1
    local PREFIX="$OUTPUT_DIR/$ARCH"

    # 根据架构选择编译器和参数
    case $ARCH in
        arm64-v8a)
            local CC="$TOOLCHAIN/bin/aarch64-linux-android${API_LEVEL}-clang"
            local CXX="$TOOLCHAIN/bin/aarch64-linux-android${API_LEVEL}-clang++"
            local CROSS_PREFIX="$TOOLCHAIN/bin/llvm-"
            local ARCH_FLAGS="--arch=aarch64 --cpu=armv8-a"
            ;;
        armeabi-v7a)
            local CC="$TOOLCHAIN/bin/armv7a-linux-androideabi${API_LEVEL}-clang"
            local CXX="$TOOLCHAIN/bin/armv7a-linux-androideabi${API_LEVEL}-clang++"
            local CROSS_PREFIX="$TOOLCHAIN/bin/llvm-"
            local ARCH_FLAGS="--arch=arm --cpu=armv7-a"
            ;;
        x86_64)
            local CC="$TOOLCHAIN/bin/x86_64-linux-android${API_LEVEL}-clang"
            local CXX="$TOOLCHAIN/bin/x86_64-linux-android${API_LEVEL}-clang++"
            local CROSS_PREFIX="$TOOLCHAIN/bin/llvm-"
            local ARCH_FLAGS="--arch=x86_64"
            ;;
    esac

    echo "=========================================="
    echo "编译 FFmpeg for $ARCH"
    echo "=========================================="

    cd "$FFMPEG_SRC"
    make clean 2>/dev/null || true

    # ===== FFmpeg configure =====
    # 这是整个编译过程最关键的一步：选择启用/禁用哪些功能
    ./configure \
        --prefix="$PREFIX" \
        --target-os=android \
        $ARCH_FLAGS \
        --cc="$CC" \
        --cxx="$CXX" \
        --cross-prefix="$CROSS_PREFIX" \
        --enable-cross-compile \
        \
        --enable-shared \          # 生成 .so 动态库（Android 标准）
        --disable-static \         # 不生成 .a 静态库
        --disable-programs \       # 不编译 ffmpeg/ffprobe 可执行文件
        --disable-doc \            # 不生成文档
        --enable-small \           # 优化产出体积（-Os）
        \
        --enable-gpl \             # 启用 GPL 授权的功能
        --enable-nonfree \         # 启用非自由授权的功能
        \
        # ===== 按需启用解码器 =====
        # 全部禁用后逐个启用，最大化裁剪体积
        --disable-everything \
        \
        # 解封装器（读取容器格式）
        --enable-demuxer=mp4,mov,matroska,flv,mpegts,avi,concat,wav,mp3,aac \
        # 封装器（写入容器格式）
        --enable-muxer=mp4,mov,mpegts,wav,mp3,gif,image2 \
        \
        # 视频解码器
        --enable-decoder=h264,hevc,vp9,av1,mpeg4,mjpeg,png,gif \
        # 视频编码器
        --enable-encoder=libx264,mjpeg,png,gif \
        # 音频解码器
        --enable-decoder=aac,mp3,opus,vorbis,pcm_s16le,pcm_s16be \
        # 音频编码器
        --enable-encoder=aac,libmp3lame,pcm_s16le \
        \
        # 滤镜
        --enable-filter=scale,crop,overlay,drawtext,colorchannelmixer \
        --enable-filter=eq,setpts,fps,transpose,hflip,vflip \
        --enable-filter=volume,afade,atempo,amix,aresample \
        --enable-filter=palettegen,paletteuse,split \
        \
        # 协议
        --enable-protocol=file,pipe,concat \
        \
        # 其他
        --enable-parser=h264,hevc,aac,mp3 \
        --enable-bsf=h264_mp4toannexb,hevc_mp4toannexb,aac_adtstoasc \
        --enable-swresample \      # 音频重采样
        --enable-swscale           # 图像缩放

    # ===== 编译 =====
    make -j$(nproc)
    make install

    echo "✓ $ARCH 编译完成 → $PREFIX"
    echo ""
    echo "产出文件："
    ls -lh "$PREFIX/lib/"*.so 2>/dev/null
    echo ""
    echo "总体积："
    du -sh "$PREFIX/lib/"
}

# ===== 主流程 =====
mkdir -p "$OUTPUT_DIR"

for arch in "${ARCHS[@]}"; do
    build_one "$arch"
done

echo ""
echo "=========================================="
echo "全部编译完成！"
echo "=========================================="
echo ""
echo "下一步："
echo "  1. 将 $OUTPUT_DIR/<arch>/lib/*.so 拷贝到 app/src/main/jniLibs/<arch>/"
echo "  2. 将 $OUTPUT_DIR/<arch>/include/ 拷贝到 app/src/main/cpp/include/"
echo "  3. 在 CMakeLists.txt 中链接这些库"
echo ""
echo "或者在 CMakeLists.txt 中直接引用 $OUTPUT_DIR 路径"
