cmake_minimum_required(VERSION 3.18.1)

project("EzConvert")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加native库
add_library(ezconvert SHARED
    native-lib.cpp
)

# 链接库
target_link_libraries(ezconvert
    android
    log
)