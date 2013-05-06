#!/bin/sh

flags=

while [ $# -gt 0 ]
do
	case "$1" in
	--no-documentation|--without-documentation)
		flags="$flags -Dgerrit.documentation.skip=true"
		shift
		;;
	--no-plugins|--without-plugins)
		flags="$flags -Dgerrit.plugins.skip=true"
		shift
		;;
	--no-tests|--without-tests)
		flags="$flags -Dgerrit.acceptance-tests.skip=true"
		flags="$flags -Dmaven.tests.skip=true"
		shift
		;;
	*)
		echo >&2 "usage: $0 [--no-documentation] [--no-plugins] [--no-tests]"
		exit 1
	esac
done

git update-index -q --refresh

if test -n "$(git diff-index --name-only HEAD --)" \
|| test -n "$(git ls-files --others --exclude-standard)"
then
	echo >&2 "error: working directory is dirty, refusing to build"
	exit 1
fi

./tools/version.sh --release &&
mvn clean package verify $flags
rc=$?
./tools/version.sh --reset

if test 0 = $rc
then
	echo
	echo Built Gerrit Code Review `git describe`:
	ls gerrit-war/target/gerrit-*.war
	echo
fi
exit $rc
