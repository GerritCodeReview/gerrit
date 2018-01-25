#!/bin/sh
echo Starting Webdriver..
nohup /opt/bin/entry_point.sh > /tmp/webdriver.log 2>&1 &
sleep 5
cp $@ .
jasmine $(basename $@)
