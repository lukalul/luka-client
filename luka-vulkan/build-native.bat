@echo off
set VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat
if not exist "%VCVARS%" for /f "usebackq delims=" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -property installationPath`) do set "VCVARS=%%i\VC\Auxiliary\Build\vcvars64.bat"
call "%VCVARS%" >nul || exit /b 1
if not defined VULKAN_SDK set VULKAN_SDK=C:\VulkanSDK\1.4.363.0
if not defined JDK21 set JDK21=C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot
set PATH=%VULKAN_SDK%\Bin;%PATH%
set SRC=%~dp0.
set OUT=%SRC%\build\native
cmake -G Ninja -S "%SRC%\native" -B "%OUT%" -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=cl -DCMAKE_CXX_COMPILER=cl "-DASTATINE_JAVA_HOME=%JDK21%" "-DASTATINE_OUTPUT_DIR=%SRC%\build\natives" || exit /b 2
cmake --build "%OUT%" --config Release --parallel || exit /b 3
