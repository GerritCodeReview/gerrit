#!/bin/sh

# Builds and deploys into Jetty; primarily for debugging

jetty=$1
fast=$2
if [ -z "$jetty" ]
then
	echo >&2 "usage: $0 jettydir [--fast]"
	exit 1
fi
if ! [ -f "$jetty/etc/jetty.xml" ]
then
	echo >&2 "error: $jetty is not a Jetty installation"
	exit 1
fi

ctx="$jetty/contexts/gerrit.xml" &&

if [ -f "$ctx" -a -n "$fast" ]
then
	echo >&2 "warning: Using fast mode to build only GWT..."
	echo >&2
	(cd appjar && mvn install) &&
	(cd appwar && mvn package) &&
	cp appwar/target/gerrit-*.war "$jetty/webapps/gerrit.war" &&
	touch "$ctx"
else
	(cd appdist && mvn clean install) &&
	out=$(cd appdist/target/gerrit-*-bin.dir/gerrit-* && pwd) &&

	cp $out/www/gerrit-*.war "$jetty/webapps/gerrit.war" &&
	if [ -f "$ctx" ]
	then
		touch "$ctx"
	else
		cp $out/jdbc/c3p0-*.jar "$jetty/lib/plus" &&
		cp $out/jdbc/postgresql-*jdbc*.jar "$jetty/lib/plus" &&

		rm -f "$jetty/contexts/test.xml" &&
		cp $out/www/jetty_gerrit.xml "$ctx" &&

		echo >&2 &&
		echo >&2 "You need to edit and configure $ctx"
	fi
fi
