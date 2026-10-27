#!/bin/sh
# 精简版 Gradle Wrapper 启动脚本
DIRNAME=$(dirname "$0")
exec java -cp "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
