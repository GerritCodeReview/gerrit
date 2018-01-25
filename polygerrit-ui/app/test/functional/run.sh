#!/bin/sh
echo Starting local server..
cp /app/polygerrit_ui.zip .
unzip -q polygerrit_ui.zip
nohup http-server polygerrit_ui > /tmp/http-server.log 2>&1 &

echo Starting Webdriver..
nohup /opt/bin/entry_point.sh > /tmp/webdriver.log 2>&1 &

# Wait for servers to start
sleep 5

cp $@ .
jasmine $(basename $@)
