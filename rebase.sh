set -e

opts=$(getopt --long onto:,src:,dir: -- opt "$@")

eval set -- "$opts"

SRC_COMMIT=""
ONTO_COMMIT=""
DIR=""
while true; do
  case "$1" in
    --onto)
      shift
      ONTO_COMMIT=$1
      ;;
    --src)
      shift
      SRC_COMMIT=$1
      ;;
    --dir)
      shift
      DIR=$1
      ;;
    --)
      shift
      break
  esac
  shift
done

if [[ $SRC_COMMIT == "" ]]; then
  echo "Please specify --src commit"
  exit 1
fi

if [[ $ONTO_COMMIT == "" ]]; then
  echo "Please specify --onto commit"
  exit 1
fi

if [[ $DIR == "" ]]; then
  echo "Please specify source code location with --dir"
  exit 1
fi

cd "$DIR"

if [[ `git status --porcelain` ]]; then
  echo "Please commit your changes before continue"
  exit 1
fi

echo "Getting original commit message"
# git checkout $SRC_COMMIT
SRC_COMMIT_MSG=`git show -s --format=%B $SRC_COMMIT`

echo "Checking out src"
git checkout --quiet $SRC_COMMIT

echo "Converting to es6 modules"
./es6-modules-converter.sh --force

CONVERTED_SHA=`git rev-parse --short HEAD`
echo "Converted SHA: $CONVERTED_SHA"

echo "Checking out new parent (--onto)"
git checkout --quiet $ONTO_COMMIT
echo "Merging with new parent"
set +e
git merge --quiet --no-commit $CONVERTED_SHA
merge_exit_status=$?
set -e
if [[ "$merge_exit_status" != "0" ]]; then
  echo "Resolving conflicts with our changes"
  # theirs here means CONVERTED_SHA
  git checkout --quiet --theirs .
fi
git add .

echo "Commit merged changes"
git commit --quiet -m "Temporary merge commit"
MERGED_SHA=`git rev-parse --short HEAD`
echo "Merged SHA: $MERGED_SHA"

echo "Checking out new parent (--onto)"
git checkout --quiet $ONTO_COMMIT

echo "Applying changes from merge commit"
git diff HEAD $MERGED_SHA | git apply

git add .
echo "Commiting changes"
git commit  --quiet -m "$SRC_COMMIT_MSG"

echo "Change commited. git log result:"
git log -n 1

echo "-----------------"
REBASED_SHA=`git rev-parse --short HEAD`

echo "Pushing new change"
git push origin HEAD:refs/for/master
echo "If you have more changes in the chain, run the script for the next change with the following parameters (add --src)"
echo "./rebase.sh --dir \"$DIR\" --onto $REBASED_SHA"


# CONVERTED SHA=451d961698
#/rebase.sh --src 038c44cfee89278215706803301fe89ede301f86 --onto 0e9f2cab388db0b6c516a35cf8e45c69bbee8b60
