cmake_minimum_required(VERSION 3.22)
project(espeak_jni LANGUAGES C CXX)

include(FetchContent)

# Fetch espeak-ng (cmake fork widely used by sherpa-onnx)
set(espeak_ng_URL "https://github.com/csukuangfj/espeak-ng/archive/f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip")
set(espeak_ng_HASH "SHA256=70cbf4050e7a014aae19140b05e57249da4720f56128459fbe3a93beaf971ae6")

# Disable unused components - we only want text-to-phoneme
set(BUILD_ESPEAK_NG_EXE OFF CACHE BOOL "" FORCE)
set(BUILD_ESPEAK_NG_TESTS OFF CACHE BOOL "" FORCE)
set(USE_ASYNC OFF CACHE BOOL "" FORCE)
set(USE_MBROLA OFF CACHE BOOL "" FORCE)
set(USE_LIBSONIC OFF CACHE BOOL "" FORCE)
set(USE_LIBPCAUDIO OFF CACHE BOOL "" FORCE)
set(USE_KLATT OFF CACHE BOOL "" FORCE)
set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    espeak_ng
    URL ${espeak_ng_URL}
    URL_HASH ${espeak_ng_HASH}
)
FetchContent_MakeAvailable(espeak_ng)

add_library(espeak_jni SHARED
    espeak_jni.cpp
)

target_include_directories(espeak_jni PRIVATE
    ${espeak_ng_SOURCE_DIR}/src/include
)

target_link_libraries(espeak_jni PRIVATE
    espeak-ng
    log
)
