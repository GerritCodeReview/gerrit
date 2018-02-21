#!/bin/bash -e

echo Waiting for PolyGerrit UI build ...
while [ ! -f /app/polygerrit_ui.zip ]
do
  sleep 1
done

cp /app/polygerrit_ui.zip .
unzip -q polygerrit_ui.zip

echo Starting local server..
nohup http-server --cors -p 8081 polygerrit_ui > /tmp/http-server.log 2>&1 &

echo Starting Webdriver..
nohup /opt/bin/entry_point.sh > /tmp/webdriver.log 2>&1 &

# Wait for servers to start
sleep 5

cp /tests/*test.js .
jasmine *test.js
