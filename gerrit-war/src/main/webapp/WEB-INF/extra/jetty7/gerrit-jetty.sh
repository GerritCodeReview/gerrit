#!/bin/sh

export JETTY_HOST=127.0.0.1
export JETTY_PORT=8081
export JETTY_USER=gerrit2
export JETTY_PID=/var/run/jetty$JETTY_PORT.pid
export JETTY_HOME=/home/$JETTY_USER/jetty
export JAVA_HOME=/usr/lib/jvm/java-6-sun-1.6.0.07/jre

JAVA_OPTIONS=""
JAVA_OPTIONS="$JAVA_OPTIONS -Djetty.host=$JETTY_HOST"
export JAVA_OPTIONS

JETTY_ARGS=""
JETTY_ARGS="$JETTY_ARGS OPTIONS=Server,plus,ext,rewrite"
export JETTY_ARGS

C="jetty-logging jetty"
[ -f "$JETTY_HOME/etc/jetty_sslproxy.xml" ] && C="$C jetty_sslproxy"

exec $JETTY_HOME/bin/jetty.sh "$@" $C
