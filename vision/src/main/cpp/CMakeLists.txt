cmake_minimum_required(VERSION 3.22.1)
project(docscanner_vision LANGUAGES CXX)

add_library(docscanner_vision SHARED jni_bridge.cpp detector.cpp page_ops.cpp)
target_compile_features(docscanner_vision PRIVATE cxx_std_17)
target_compile_options(docscanner_vision PRIVATE -Wall -Wextra -Werror -fno-exceptions -fno-rtti)
target_link_libraries(docscanner_vision PRIVATE jnigraphics)
