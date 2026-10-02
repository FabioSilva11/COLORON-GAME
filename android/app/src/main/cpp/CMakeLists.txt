cmake_minimum_required(VERSION 3.22.1)
project(coloron3d LANGUAGES CXX)

include(FetchContent)
# GLM: biblioteca de matemática 3D (header-only)
FetchContent_Declare(glm
    GIT_REPOSITORY https://github.com/g-truc/glm.git
    GIT_TAG 1.0.1)
FetchContent_MakeAvailable(glm)

add_library(coloron3d SHARED
    jni_bridge.cpp
    GameEngine.cpp
    Renderer.cpp)

target_compile_definitions(coloron3d PRIVATE GLM_FORCE_RADIANS)
target_link_libraries(coloron3d glm::glm GLESv3 EGL android log)
