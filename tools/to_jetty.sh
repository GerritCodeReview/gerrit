#!/bin/sh

# Builds and deploys into Jetty; primarily for debugging

jetty=$1
if [ -z "$jetty" ]
then
        if [ -z "$JETTY_HOME" ]
        then
                echo >&2 "usage: $0 jettydir"
                exit 1
        fi
        jetty=$JETTY_HOME
fi
if ! [ -f "$jetty/etc/jetty.xml" ]
then
	echo >&2 "error: $jetty is not a Jetty installation"
	exit 1
fi

ctx="$jetty/contexts/gerrit.xml" &&

mvn clean package &&
war=gerrit-war/target/gerrit-*.war &&
extra=gerrit-war/src/main/webapp/WEB-INF/extra/ &&

cp $war "$jetty/webapps/gerrit.war" &&
if [ -f "$ctx" ]
then
	touch "$ctx"
else
	rm -f "$jetty/contexts/test.xml" &&
	cp "$extra/jetty6/gerrit.xml" "$ctx" &&

	echo >&2
	echo >&2 "You need to copy JDBC drivers to $jetty/lib/plus"
	echo >&2 "You need to edit and configure $ctx"
fi
