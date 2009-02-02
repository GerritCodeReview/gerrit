#!/bin/sh

this_war=`which "$0" 2>/dev/null`
test $? -gt 0 -a -f "$0" && this_war="$0"

java=java
if test -n "$JAVA_HOME"
then
    java="$JAVA_HOME/bin/java"
fi

case "`uname`" in
CYGWIN*)
    this_war=`cygpath --windows --mixed --path "$this_war"`
    ;;
esac

exec "$java" -jar "$this_war" "$@"
exit 1

##############################################################################
