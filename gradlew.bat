@echo off
REM 精简版 Gradle Wrapper 启动脚本（官方 gradlew.bat 的核心逻辑）
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar
java -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
