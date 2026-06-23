#!/bin/sh

APP_HOME=$(cd "${0%/*}" && pwd -P) || exit
APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"
MAX_FD=maximum
WARN=
QUIET=false

die() {
    echo "$*"
    exit 1
}

cygwin=false
msys=false
darwin=false
nonstop=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true ;;
  MSYS* | MINGW*) msys=true ;;
  NONSTOP*) nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [ "$cygwin" = false ] && [ "$darwin" = false ] && [ "$nonstop" = false ]; then
    case $MAX_FD in
      max*) MAX_FD=$(ulimit -H -n) || WARN="Could not query maximum file descriptor limit" ;;
    esac
    case $MAX_FD in
      '' | soft) ;;
      *) ulimit -n "$MAX_FD" || WARN="Could not set maximum file descriptor limit to $MAX_FD" ;;
    esac
fi

[ -n "$WARN" ] && echo "$WARN"

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname="$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
