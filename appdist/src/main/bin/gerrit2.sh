#!/bin/sh
#
# Copyright 2008 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if [ -z "$GERRIT2_HOME" ]
then
	GERRIT2_HOME=`which $0 2>/dev/null`
	GERRIT2_HOME=`dirname $GERRIT2_HOME`
	GERRIT2_HOME=`dirname $GERRIT2_HOME`
fi

if [ -z "$GERRIT2_HOME" ]
then
	echo >&2 "error: GERRIT2_HOME not set, cannot guess"
	exit 1
fi

if [ -z "$GERRIT2_JAVA" ]
then
	GERRIT2_JAVA=java
fi

config_dir=
case "$1" in
--config=*)
	config_dir=`echo "$1" | sed s/^--config=//`
	if ! [ -f "$config_dir" ]
	then
		echo >&2 "error: $config_dir not found"
		exit 1
	fi
	case "$config_dir" in
	*/GerritServer.properties)
		config_dir=`dirname "$config_dir"`
		shift
		;;
	*)
		echo >&2 "error: --config must point to GerritServer.properties"
		exit 1
		;;
	esac
	;;
esac

if [ $# = 0 ]
then
	echo >&2 "usage: $0 [--config=gs.prop] AppName [args]"
	exit 1
fi
app=$1
shift

if [ -n "$config_dir" ]
then
	CLASSPATH=$config_dir
	for j in $config_dir/jdbc-*.jar
	do
		[ -f "$j" ] && CLASSPATH=$CLASSPATH:$j
	done
else
	CLASSPATH=
fi

for j in $GERRIT2_HOME/lib/*.jar
do
	if [ -f "$j" ]
	then
        if [ -z "$CLASSPATH" ]
		then
			CLASSPATH=$j
		else
			CLASSPATH=$CLASSPATH:$j
        fi
	fi
done
export CLASSPATH

umask 0022
exec $GERRIT2_JAVA com.google.gerrit.pgm.$app "$@"
