#!/bin/sh

# Update all pom.xml with new build number
#
# TODO(sop) This should be converted to some sort of
# Java based Maven plugin so its fully portable.
#

POM_FILES=$(git ls-files | grep pom.xml)

case "$1" in
--snapshot=*)
	V=$(echo "$1" | perl -pe 's/^--snapshot=//')
	if [ -z "$V" ]
	then
		echo >&2 "usage: $0 --snapshot=0.n.0"
		exit 1
	fi
	case "$V" in
	*-SNAPSHOT) : ;;
	*) V=$V-SNAPSHOT ;;
	esac
	;;

--release)
	V=$(git describe HEAD) || exit
	;;

--reset)
	git checkout HEAD -- $POM_FILES
	exit $?
	;;

*)
	echo >&2 "usage: $0 {--snapshot=2.n | --release}"
	exit 1
esac

case "$V" in
v*) V=$(echo "$V" | perl -pe s/^v//) ;;
esac

perl -pi -e '
	if ($ARGV ne $old_argv) {
		$seen_version = 0;
		$old_argv = $ARGV;
	}
	if (!$seen_version) {
		$seen_version = 1 if
		s{(<version>).*(</version>)}{${1}'"$V"'${2}};
	}
	' $POM_FILES
