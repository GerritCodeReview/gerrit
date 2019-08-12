DIR=$(pwd)

trap 'kill $(jobs -p)' EXIT

while getopts "p:i:f:t:b:s:h:d:" o;
do
  case "$o" in
    p)
      START_PORT=$(($OPTARG))
      ;;
    i)
      INSTANCES=$(($OPTARG))
      ;;
    f)
      FONTS_FILE="$DIR/$OPTARG"
      ;;
    t)
      TEST_COMPONENTS_FILE="$DIR/$OPTARG"
      ;;
    b)
      BALANCER_BINARY="$DIR/$OPTARG"
      ;;
    s)
      GERRIT_ADDON_SCRIPT=$DIR/$OPTARG
      ;;
    h)
      UPSTREAM_HOST=$OPTARG
      ;;
    d)
      POLYGERRIT_UI_DIR=$OPTARG
      ;;
  esac
done
POLYGERRIT_UI_DIR=$BUILD_WORKSPACE_DIRECTORY/polygerrit-ui
echo "--------"
echo $POLYGERRIT_UI_DIR
COMMON_ARGS="-s $GERRIT_ADDON_SCRIPT --set host=$UPSTREAM_HOST --set polygerrit_ui_dir=$POLYGERRIT_UI_DIR --set test_components_path=$TEST_COMPONENTS_FILE --set fonts_path=$FONTS_FILE --mode regular --no-http2 --listen-host 127.0.0.1"

if [ $INSTANCES -eq 1 ]
then
  echo "Only one instance. Run in non-quite mode"
  echo $TEST_COMPONENTS_FILE
  mitmdump $COMMON_ARGS --listen-port $START_PORT
else
  echo "Multiple instances. Run in quite mode with balancer"
  BALANCER_PORT=$START_PORT
  PROXY_START_PORT=$(($START_PORT + 1))
  PROXY_END_PORT=$(($PROXY_START_PORT + $INSTANCES))
  SERVERS=""
  CMD=""
  for i in `seq $PROXY_START_PORT $PROXY_END_PORT`
  do
    RUN_PROXY_ISNTANCE_COMMAND="mitmdump $COMMON_ARGS --listen-port $i"
    CMD="$CMD & $RUN_PROXY_ISNTANCE_COMMAND"
    SERVERS="$SERVERS,127.0.0.1:$i"
  done

  #Remove first comma from list of servers
  SERVERS=${SERVERS:1}

  CMD="$BALANCER_BINARY --port=$BALANCER_PORT --servers $SERVERS $CMD"

  eval $CMD
fi

