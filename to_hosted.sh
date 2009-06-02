#!/bin/sh

mvn --offline war:inplace &&
rm -f src/main/webapp/WEB-INF/lib/gerrit-*.jar
