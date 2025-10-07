cmake_minimum_required(VERSION 3.18.1)

project("EzConvert")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加native库
add_library(ezconvert SHARED
    native-lib.cpp
    ffmpeg_wrapper.cpp
)

# 链接库 - 暂时移除FFmpeg依赖
target_link_libraries(ezconvert
    android
    log
)

# 临时添加宏定义来模拟FFmpeg功能
target_compile_definitions(ezconvert PRIVATE SIMULATE_FFMPEG=1)

message("Building without FFmpeg - using simulation mode")