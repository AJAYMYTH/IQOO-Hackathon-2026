cmake_minimum_required(VERSION 3.22.1)
project("llama_bridge")

# Path to llama.cpp root (git submodule)
set(LLAMA_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../../llama.cpp")

# Build llama.cpp
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_BINARY_DIR}/llama.cpp)

# Build JNI bridge
add_library(llama_bridge SHARED llama_bridge.cpp)

target_include_directories(llama_bridge PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/common
)

target_link_libraries(llama_bridge
    llama
    common
    ggml
    log
    android
)
