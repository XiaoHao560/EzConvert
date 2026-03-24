cmake_minimum_required(VERSION 4.1.2)

project(ezlog)

add_library(
    ezlog
    SHARED
    ezlog_mmap.cpp
)

find_library(log-lib log)

target_link_libraries(
    ezlog
    ${log-lib}
)
