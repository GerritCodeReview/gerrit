#!/bin/sh

# This script assumes the Gerrit1 import has already been
# renamed to 'gerrit1' schema:
#
#  psql -c 'ALTER SCHEMA public RENAME TO gerrit1' $src
#

src=android_codereview
tmp=safedump
N="N.$$.sql"

dropdb $tmp
createdb -E UTF-8 $tmp &&
pg_dump -O -Fc $src >$N &&
pg_restore -d $tmp $N &&
psql -c 'DROP TABLE gerrit1.delta_content' $tmp &&
psql -c 'UPDATE gerrit1.accounts SET
  mailing_address = NULL
 ,mailing_address_country = NULL
 ,phone_number = NULL
 ,fax_number = NULL
 ,cla_comments = NULL' $tmp &&
pg_dump -O -Fc $tmp | bzip2 -9 >gerrit1.dump.bz2
dropdb $tmp
rm -f $N
