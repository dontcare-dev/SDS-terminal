cmake_minimum_required(VERSION 3.22.1)
project(ptyjni)

add_library(ptyjni SHARED ptyjni.c)

find_library(log-lib log)

target_link_libraries(ptyjni ${log-lib})