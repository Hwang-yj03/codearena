cmake_minimum_required(VERSION 3.16)
project(codearena_sandbox CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_compile_options(-O2 -Wall -Wextra)

add_executable(runner src/runner.cpp)
