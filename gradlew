#!/bin/sh
# Gradle Wrapper script
DIRNAME=$(dirname "$0")
APP_HOME=$(cd "$DIRNAME" || exit; pwd)

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
