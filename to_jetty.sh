#!/bin/sh

# Builds and deploys into Jetty; primarily for debugging

jetty=$1
if [ -z "$jetty" ]
then
	echo >&2 "usage: $0 jettydir"
	exit 1
fi
if ! [ -f "$jetty/etc/jetty.xml" ]
then
	echo >&2 "error: $jetty is not a Jetty installation"
	exit 1
fi

out=$(cd appdist/target/gerrit-*-bin.dir/gerrit-* && pwd) &&
ctx="$jetty/contexts/gerrit.xml" &&

(cd appdist && mvn clean install) &&

cp $out/www/gerrit-*.war "$jetty/webapps/gerrit.war" &&

if [ -f "$ctx" ]
then
	touch "$ctx"
else
	cp $out/jdbc/c3p0-*.jar "$jetty/lib/plus" &&
	cp $out/jdbc/postgresql-*jdbc*.jar "$jetty/lib/plus" &&

	rm -f "$jetty/contexts/test.xml" &&
	cp $out/www/jetty_gerrit.xml "$ctx" &&
	echo "You need to edit and configure $ctx"
fi
