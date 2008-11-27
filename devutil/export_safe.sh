#!/bin/sh

src=reviewdb
tmp=safedump
N="N.$$.sql"

dropdb $tmp
pg_dump $src >$N &&
createdb -E UTF-8 $tmp &&
psql -f $N $tmp &&
psql -c 'UPDATE accounts SET
  contact_address = NULL
 ,contact_country = NULL
 ,contact_phone_nbr = NULL
 ,contact_fax_nbr = NULL' $tmp &&
pg_dump $tmp | bzip2 -9 >gerrit2_dump.sql.bz2
dropdb $tmp
rm -f $N
