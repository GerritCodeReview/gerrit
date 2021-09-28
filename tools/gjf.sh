cmd="$(pwd)/$1"
cd "${BUILD_WORKING_DIRECTORY}"
shift
$cmd $@
