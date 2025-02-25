#!/bin/bash -e

# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

OLD_H2_VERSION=1.3.176
NEW_H2_VERSION=2.3.232

usage() {
    me=`basename "$0"`
    echo >&2 "Usage: $me [--help] [--site SITE] [--output DST]"
    exit 1
}

while test $# -gt 0 ; do
  case "$1" in
  --help)
    usage
    ;;

  --site)
    shift
    SITE=$1
    shift
    ;;

  --output)
    shift
    DST=$1
    shift
    ;;
  *)
    break
  esac
done

test -z $SITE && usage
SRC=$SITE/cache

test -z $DST && DST=$SRC

mkdir -p $DST
rm -rf $DST/*-v2.mv.db

test -f h2-$NEW_H2_VERSION.jar || \
    wget https://repo1.maven.org/maven2/com/h2database/h2/$NEW_H2_VERSION/h2-$NEW_H2_VERSION.jar
test -f h2-$OLD_H2_VERSION.jar || \
    wget https://repo1.maven.org/maven2/com/h2database/h2/$OLD_H2_VERSION/h2-$OLD_H2_VERSION.jar

for filepath in $SRC/*.h2.db; do
    DB_NAME=$(basename "$filepath" .h2.db)

    echo "Exporting database $DB_NAME ..."
    cp $filepath $DST/${DB_NAME}_tmp.h2.db
    java -cp h2-$OLD_H2_VERSION.jar org.h2.tools.Shell -url jdbc:h2:$DST/${DB_NAME}_tmp -sql 'ALTER TABLE public.data DROP COLUMN IF EXISTS space;'
    java -cp h2-$OLD_H2_VERSION.jar org.h2.tools.Script -url jdbc:h2:$DST/${DB_NAME}_tmp -script backup-$DB_NAME.zip -options compression zip

    echo "Importing data of $DB_NAME..."
    java -cp h2-$NEW_H2_VERSION.jar org.h2.tools.RunScript -url jdbc:h2:$DST/$DB_NAME-v2 -script ./backup-$DB_NAME.zip -options compression zip FROM_1X
    java -cp h2-$NEW_H2_VERSION.jar org.h2.tools.Shell -url jdbc:h2:$DST/$DB_NAME-v2 -sql 'ALTER TABLE public.data ADD COLUMN IF NOT EXISTS space BIGINT AS OCTET_LENGTH(k) + OCTET_LENGTH(v);'

    rm -f backup-$DB_NAME.zip
    rm -rf $DST/${DB_NAME}_tmp.h2.db
    echo "$DB_NAME migrated succesfully"
done
