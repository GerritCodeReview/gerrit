#!/bin/sh
#
# Launch Gerrit Code Review as a daemon process.

# To get the service to restart correctly on reboot, uncomment below (3 lines):
# ========================
# chkconfig: 3 99 99
# description: Gerrit Code Review
# processname: gerrit
# ========================

### BEGIN INIT INFO
# Provides:          gerrit
# Required-Start:    $named $remote_fs $syslog
# Required-Stop:     $named $remote_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Start/stop Gerrit Code Review
# Description:       Gerrit is a web based code review system, facilitating online code reviews
#                    for projects using the Git version control system.
### END INIT INFO

# Configuration files:
#
# /etc/default/gerritcodereview
#   If it exists, sourced at the start of this script. It may perform any
#   sequence of shell commands, like setting relevant environment variables.
#
# The files will be checked for existence before being sourced.

# Configuration variables.  These may be set in /etc/default/gerritcodereview.
#
# GERRIT_SITE
#   Path of the Gerrit site to run.  $GERRIT_SITE/etc/gerrit.config
#   will be used to configure the process.
#
# GERRIT_WAR
#   Location of the gerrit.war download that we will execute.  Defaults to
#   container.war property in $GERRIT_SITE/etc/gerrit.config.
#
# NO_START
#   If set to "1" disables Gerrit from starting.
#
# START_STOP_DAEMON
#   If set to "0" disables using start-stop-daemon.  This may need to
#   be set on SuSE systems.

usage() {
    me=`basename "$0"`
    echo >&2 "Usage: $me {start|stop|restart|check|status|run|supervise} [-d site]"
    exit 1
}

test $# -gt 0 || usage

##################################################
# Some utility functions
##################################################
running() {
  test -f $1 || return 1
  PID=`cat $1`
  ps -p $PID >/dev/null 2>/dev/null || return 1
  return 0
}

get_config() {
  if test -f "$GERRIT_CONFIG" ; then
    if test "x$1" = x--int ; then
      # Git might not be able to expand "8g" properly.  If it gives
      # us 0 back retry for the raw string and expand ourselves.
      #
      n=`git config --file "$GERRIT_CONFIG" --int "$2"`
      if test x0 = "x$n" ; then
        n=`git config --file "$GERRIT_CONFIG" --get "$2"`
        case "$n" in
        *g) n=`expr ${n%%g} \* 1024`m ;;
        *k) n=`expr ${n%%k} \* 1024` ;;
        *)  : ;;
        esac
      fi
      echo "$n"
    else
      git config --file "$GERRIT_CONFIG" $1 "$2"
    fi
  fi
}

##################################################
# Get the action and options
##################################################

ACTION=$1
shift

while test $# -gt 0 ; do
  case "$1" in
  -d|--site-path)
    shift
    GERRIT_SITE=$1
    shift
    ;;
  -d=*)
    GERRIT_SITE=${1##-d=}
    shift
    ;;
  --site-path=*)
    GERRIT_SITE=${1##--site-path=}
    shift
    ;;

  *)
    usage
  esac
done

test -z "$NO_START" && NO_START=0
test -z "$START_STOP_DAEMON" && START_STOP_DAEMON=1

##################################################
# See if there's a default configuration file
##################################################
if test -f /etc/default/gerritcodereview ; then
  . /etc/default/gerritcodereview
fi

##################################################
# Set tmp if not already set.
##################################################
if test -z "$TMP" ; then
  TMP=/tmp
fi
TMPJ=$TMP/j$$

##################################################
# Reasonable guess marker for a Gerrit site path.
##################################################
GERRIT_INSTALL_TRACE_FILE=etc/gerrit.config

##################################################
# No git in PATH? Needed for gerrit.config parsing
##################################################
if type git >/dev/null 2>&1 ; then
  : OK
else
  echo >&2 "** ERROR: Cannot find git in PATH"
  exit 1
fi

##################################################
# Try to determine GERRIT_SITE if not set
##################################################
if test -z "$GERRIT_SITE" ; then
  GERRIT_SITE_1=`dirname "$0"`/..
  if test -f "${GERRIT_SITE_1}/${GERRIT_INSTALL_TRACE_FILE}" ; then
    GERRIT_SITE=${GERRIT_SITE_1}
  fi
fi

##################################################
# No GERRIT_SITE yet? We're out of luck!
##################################################
if test -z "$GERRIT_SITE" ; then
    echo >&2 "** ERROR: GERRIT_SITE not set"
    exit 1
fi

INITIAL_DIR=`pwd`
if cd "$GERRIT_SITE" ; then
  GERRIT_SITE=`pwd`
else
  echo >&2 "** ERROR: Gerrit site $GERRIT_SITE not found"
  exit 1
fi

#####################################################
# Check that Gerrit is where we think it is
#####################################################
GERRIT_CONFIG="$GERRIT_SITE/$GERRIT_INSTALL_TRACE_FILE"
test -f "$GERRIT_CONFIG" || {
   echo "** ERROR: Gerrit is not initialized in $GERRIT_SITE"
   exit 1
}
test -r "$GERRIT_CONFIG" || {
   echo "** ERROR: $GERRIT_CONFIG is not readable!"
   exit 1
}

GERRIT_PID="$GERRIT_SITE/logs/gerrit.pid"
GERRIT_RUN="$GERRIT_SITE/logs/gerrit.run"
GERRIT_TMP="$GERRIT_SITE/tmp"
export GERRIT_TMP

##################################################
# Check for JAVA_HOME
##################################################
JAVA_HOME_OLD="$JAVA_HOME"
JAVA_HOME=`get_config --get container.javaHome`
if test -z "$JAVA_HOME" ; then
  JAVA_HOME="$JAVA_HOME_OLD"
fi
if test -z "$JAVA_HOME" ; then
    # If a java runtime is not defined, search the following
    # directories for a JVM and sort by version. Use the highest
    # version number.

    JAVA_LOCATIONS="\
        /usr/java \
        /usr/bin \
        /usr/local/bin \
        /usr/local/java \
        /usr/local/jdk \
        /usr/local/jre \
        /usr/lib/jvm \
        /opt/java \
        /opt/jdk \
        /opt/jre \
    "
    for N in java jdk jre ; do
      for L in $JAVA_LOCATIONS ; do
        test -d "$L" || continue
        find $L -name "$N" ! -type d | grep -v threads | while read J ; do
          test -x "$J" || continue
          VERSION=`eval "$J" -version 2>&1`
          test $? = 0 || continue
          VERSION=`expr "$VERSION" : '.*"\(1.[0-9\.]*\)["_]'`
          test -z "$VERSION" && continue
          expr "$VERSION" \< 1.2 >/dev/null && continue
          echo "$VERSION:$J"
        done
      done
    done | sort | tail -1 >"$TMPJ"
    JAVA=`cat "$TMPJ" | cut -d: -f2`
    JVERSION=`cat "$TMPJ" | cut -d: -f1`
    rm -f "$TMPJ"

    JAVA_HOME=`dirname "$JAVA"`
    while test -n "$JAVA_HOME" \
               -a "$JAVA_HOME" != "/" \
               -a ! -f "$JAVA_HOME/lib/tools.jar" ; do
      JAVA_HOME=`dirname "$JAVA_HOME"`
    done
    test -z "$JAVA_HOME" && JAVA_HOME=

    echo "** INFO: Using $JAVA"
fi

if test -z "$JAVA" \
     -a -n "$JAVA_HOME" \
     -a -x "$JAVA_HOME/bin/java" \
     -a ! -d "$JAVA_HOME/bin/java" ; then
  JAVA="$JAVA_HOME/bin/java"
fi

if test -z "$JAVA" ; then
  echo >&2 "Cannot find a JRE or JDK. Please set JAVA_HOME or"
  echo >&2 "container.javaHome in $GERRIT_SITE/etc/gerrit.config"
  echo >&2 "to a >=1.7 JRE"
  exit 1
fi

#####################################################
# Add Gerrit properties to Java VM options.
#####################################################

GERRIT_OPTIONS=`get_config --get-all container.javaOptions`
if test -n "$GERRIT_OPTIONS" ; then
  JAVA_OPTIONS="$JAVA_OPTIONS $GERRIT_OPTIONS"
fi

GERRIT_MEMORY=`get_config --get container.heapLimit`
if test -n "$GERRIT_MEMORY" ; then
  JAVA_OPTIONS="$JAVA_OPTIONS -Xmx$GERRIT_MEMORY"
fi

GERRIT_FDS=`get_config --int core.packedGitOpenFiles`
test -z "$GERRIT_FDS" && GERRIT_FDS=128
GERRIT_FDS=`expr $GERRIT_FDS + $GERRIT_FDS`
test $GERRIT_FDS -lt 1024 && GERRIT_FDS=1024

GERRIT_USER=`get_config --get container.user`

#####################################################
# Configure sane ulimits for a daemon of our size.
#####################################################

ulimit -c 0            ; # core file size
ulimit -d unlimited    ; # data seg size
ulimit -f unlimited    ; # file size
ulimit -m >/dev/null 2>&1 && ulimit -m unlimited  ; # max memory size
ulimit -n $GERRIT_FDS  ; # open files
ulimit -t unlimited    ; # cpu time
ulimit -v unlimited    ; # virtual memory

ulimit -x >/dev/null 2>&1 && ulimit -x unlimited  ; # file locks

#####################################################
# This is how the Gerrit server will be started
#####################################################

if test -z "$GERRIT_WAR" ; then
  GERRIT_WAR=`get_config --get container.war`
fi
if test -z "$GERRIT_WAR" ; then
  GERRIT_WAR="$GERRIT_SITE/bin/gerrit.war"
  test -f "$GERRIT_WAR" || GERRIT_WAR=
fi
if test -z "$GERRIT_WAR" -a -n "$GERRIT_USER" ; then
  for homedirs in /home /Users ; do
    if test -d "$homedirs/$GERRIT_USER" ; then
      GERRIT_WAR="$homedirs/$GERRIT_USER/gerrit.war"
      if test -f "$GERRIT_WAR" ; then
        break
      else
        GERRIT_WAR=
      fi
    fi
  done
fi
if test -z "$GERRIT_WAR" ; then
  echo >&2 "** ERROR: Cannot find gerrit.war (try setting \$GERRIT_WAR)"
  exit 1
fi

test -z "$GERRIT_USER" && GERRIT_USER=`whoami`
RUN_ARGS="-jar $GERRIT_WAR daemon -d $GERRIT_SITE"
if test "`get_config --bool container.slave`" = "true" ; then
  RUN_ARGS="$RUN_ARGS --slave"
fi
if test -n "$JAVA_OPTIONS" ; then
  RUN_ARGS="$JAVA_OPTIONS $RUN_ARGS"
fi

if test -x /usr/bin/perl ; then
  # If possible, use Perl to mask the name of the process so its
  # something specific to us rather than the generic 'java' name.
  #
  export JAVA
  RUN_EXEC=/usr/bin/perl
  RUN_Arg1=-e
  RUN_Arg2='$x=$ENV{JAVA};exec $x @ARGV;die $!'
  RUN_Arg3='-- GerritCodeReview'
else
  RUN_EXEC=$JAVA
  RUN_Arg1=
  RUN_Arg2='-DGerritCodeReview=1'
  RUN_Arg3=
fi

##################################################
# Do the action
##################################################
case "$ACTION" in
  start)
    printf '%s' "Starting Gerrit Code Review: "

    if test 1 = "$NO_START" ; then
      echo "Not starting gerrit - NO_START=1 in /etc/default/gerritcodereview"
      exit 0
    fi

    test -z "$UID" && UID=`id | sed -e 's/^[^=]*=\([0-9]*\).*/\1/'`

    RUN_ID=`date +%s`.$$
    RUN_ARGS="$RUN_ARGS --run-id=$RUN_ID"

    if test 1 = "$START_STOP_DAEMON" && type start-stop-daemon >/dev/null 2>&1
    then
      test $UID = 0 && CH_USER="-c $GERRIT_USER"
      if start-stop-daemon -S -b $CH_USER \
         -p "$GERRIT_PID" -m \
         -d "$GERRIT_SITE" \
         -a "$RUN_EXEC" -- $RUN_Arg1 "$RUN_Arg2" $RUN_Arg3 $RUN_ARGS
      then
        : OK
      else
        rc=$?
        if test $rc = 127; then
          echo >&2 "fatal: start-stop-daemon failed"
          rc=1
        fi
        exit $rc
      fi
    else
      if test -f "$GERRIT_PID" ; then
        if running "$GERRIT_PID" ; then
          echo "Already Running!!"
          exit 0
        else
          rm -f "$GERRIT_PID" "$GERRIT_RUN"
        fi
      fi

      if test $UID = 0 -a -n "$GERRIT_USER" ; then
        touch "$GERRIT_PID"
        chown $GERRIT_USER "$GERRIT_PID"
        su - $GERRIT_USER -s /bin/sh -c "
          JAVA='$JAVA' ; export JAVA ;
          $RUN_EXEC $RUN_Arg1 '$RUN_Arg2' $RUN_Arg3 $RUN_ARGS </dev/null >/dev/null 2>&1 &
          PID=\$! ;
          disown ;
          echo \$PID >\"$GERRIT_PID\""
      else
        $RUN_EXEC $RUN_Arg1 "$RUN_Arg2" $RUN_Arg3 $RUN_ARGS </dev/null >/dev/null 2>&1 &
        PID=$!
        type disown >/dev/null 2>&1 && disown
        echo $PID >"$GERRIT_PID"
      fi
    fi

    if test $UID = 0; then
        PID=`cat "$GERRIT_PID"`
        if test -f "/proc/${PID}/oom_score_adj" ; then
            echo -1000 > "/proc/${PID}/oom_score_adj"
        else
            if test -f "/proc/${PID}/oom_adj" ; then
                echo -16 > "/proc/${PID}/oom_adj"
            fi
        fi
    fi

    TIMEOUT=90  # seconds
    sleep 1
    while running "$GERRIT_PID" && test $TIMEOUT -gt 0 ; do
      if test "x$RUN_ID" = "x`cat $GERRIT_RUN 2>/dev/null`" ; then
        echo OK
        exit 0
      fi

      sleep 2
      TIMEOUT=`expr $TIMEOUT - 2`
    done

    echo FAILED
    exit 1
  ;;

  stop)
    printf '%s' "Stopping Gerrit Code Review: "

    if test 1 = "$START_STOP_DAEMON" && type start-stop-daemon >/dev/null 2>&1
    then
      start-stop-daemon -K -p "$GERRIT_PID" -s HUP
      sleep 1
      if running "$GERRIT_PID" ; then
        sleep 3
        if running "$GERRIT_PID" ; then
          sleep 30
          if running "$GERRIT_PID" ; then
            start-stop-daemon -K -p "$GERRIT_PID" -s KILL
          fi
        fi
      fi
      rm -f "$GERRIT_PID" "$GERRIT_RUN"
      echo OK
    else
      PID=`cat "$GERRIT_PID" 2>/dev/null`
      TIMEOUT=30
      while running "$GERRIT_PID" && test $TIMEOUT -gt 0 ; do
        kill $PID 2>/dev/null
        sleep 1
        TIMEOUT=`expr $TIMEOUT - 1`
      done
      test $TIMEOUT -gt 0 || kill -9 $PID 2>/dev/null
      rm -f "$GERRIT_PID" "$GERRIT_RUN"
      echo OK
    fi
  ;;

  restart)
    GERRIT_SH=$0
    if test -f "$GERRIT_SH" ; then
      : OK
    else
      GERRIT_SH="$INITIAL_DIR/$GERRIT_SH"
      if test -f "$GERRIT_SH" ; then
        : OK
      else
        echo >&2 "** ERROR: Cannot locate gerrit.sh"
        exit 1
      fi
    fi
    $GERRIT_SH stop $*
    sleep 5
    $GERRIT_SH start $*
  ;;

  supervise)
    #
    # Under control of daemontools supervise monitor which
    # handles restarts and shutdowns via the svc program.
    #
    exec "$RUN_EXEC" $RUN_Arg1 "$RUN_Arg2" $RUN_Arg3 $RUN_ARGS
    ;;

  run|daemon)
    echo "Running Gerrit Code Review:"

    if test -f "$GERRIT_PID" ; then
        if running "$GERRIT_PID" ; then
          echo "Already Running!!"
          exit 0
        else
          rm -f "$GERRIT_PID"
        fi
    fi

    exec "$RUN_EXEC" $RUN_Arg1 "$RUN_Arg2" $RUN_Arg3 $RUN_ARGS --console-log
  ;;

  check|status)
    echo "Checking arguments to Gerrit Code Review:"
    echo "  GERRIT_SITE     =  $GERRIT_SITE"
    echo "  GERRIT_CONFIG   =  $GERRIT_CONFIG"
    echo "  GERRIT_PID      =  $GERRIT_PID"
    echo "  GERRIT_TMP      =  $GERRIT_TMP"
    echo "  GERRIT_WAR      =  $GERRIT_WAR"
    echo "  GERRIT_FDS      =  $GERRIT_FDS"
    echo "  GERRIT_USER     =  $GERRIT_USER"
    echo "  JAVA            =  $JAVA"
    echo "  JAVA_OPTIONS    =  $JAVA_OPTIONS"
    echo "  RUN_EXEC        =  $RUN_EXEC $RUN_Arg1 '$RUN_Arg2' $RUN_Arg3"
    echo "  RUN_ARGS        =  $RUN_ARGS"
    echo

    if test -f "$GERRIT_PID" ; then
        if running "$GERRIT_PID" ; then
            echo "Gerrit running pid="`cat "$GERRIT_PID"`
            exit 0
        fi
    fi
    exit 3
  ;;

  *)
    usage
  ;;
esac

exit 0
