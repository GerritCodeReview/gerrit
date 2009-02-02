#!/bin/sh

# To get a gerrit1.dump:
#    psql -c 'ALTER SCHEMA public RENAME TO gerrit1' $src
#    pg_dump -O -Fc $src >gerrit1.dump
#
# Your config.sql should look like:
#   UPDATE system_config
#   SET
#    site_path='/home/gerrit2/site_dir'
#   ,git_base_path='/srv/git'
#   ;

v1data="$1"
cfgsql="$2"
nobuild="$3"
if [ -z "$v1data" -o -z "$cfgsql" ]
then
	echo >&2 "usage: $0 gerrit1.dump config.sql [-n]"
	exit 1
fi

dstdb=reviewdb
user=gerrit2

if [ -z "$nobuild" ]
then
	mvn package || exit
fi

out=target/gerrit-*.war
g2="java -jar $out"

dropdb $dstdb
createdb -E UTF-8 -O $user $dstdb || exit
pg_restore -O -d $dstdb $v1data || exit
$g2 CreateSchema || exit
psql -f gerrit1_import/import_gerrit1_a.sql $dstdb || exit
psql -f $cfgsql $dstdb || exit
$g2 ImportGerrit1 || exit
psql -f gerrit1_import/import_gerrit1_b.sql $dstdb || exit

echo >&2
echo >&2 "Creating secondary indexes..."
echo >&2 "  ignore failures unless on a production system"
$g2 --cat sql/query_index.sql | psql $dstdb
echo >&2

psql -c 'DROP SCHEMA gerrit1 CASCADE' $dstdb
psql -c 'VACUUM FULL VERBOSE ANALYZE' $dstdb
echo "CONVERT Gerrit 1 -> Gerrit 2 SUCCESSFUL"

