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

CLASSPATH=
for j in `pwd`/lib/*.jar
do
	CLASSPATH=$CLASSPATH:$j
done
export CLASSPATH

(cd src &&
 mkdir -p ../bin &&
 mkdir -p ../bin/META-INF &&
 cp -f META-INF/MANIFEST.MF ../bin/META-INF &&
 find . -name \*.java | xargs javac -g -d ../bin) &&
(cd bin &&
 rm -f ../codereview_manager.jar &&
 jar cf ../codereview_manager.jar . ) || exit
