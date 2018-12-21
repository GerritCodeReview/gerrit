#!/bin/sh

if [[ "$OSTYPE" != "darwin"* ]]; then
    echo Only works on OSX.
    exit 1
fi

/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222 --user-data-dir=${HOME}/devchrome --proxy-server="127.0.0.1:8888"
