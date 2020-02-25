if [[ `git status --porcelain` &&  "$1" -ne "--force" ]]; then
  echo "Please commit your changes before continue or run the tool with  --force flag"
  exit 1
fi

pattern=*.html
find polygerrit-ui/app/ -name $pattern -type f -not -path "*/node_modules/*" -exec sh -c 'git restore {}' \;
DIR=$(pwd)
temp_file=$(mktemp)
find $DIR/polygerrit-ui/app/ -name $pattern -type f -not -path "*/node_modules/*" -exec sh -c 'echo {} && echo {}' \n \; >$temp_file
bazel run tools/node_tools/polygerrit_app_preprocessor:links-updater-bin -- $temp_file $DIR/polygerrit-ui/app/partial-redirects.json

find ./ -name '*.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "s/href=\"\/bower_components\/polymer-resin\/standalone\/polymer-resin.html\"/href=\"\/node_modules\/polymer-bridges\/polymer-resin\/standalone\/polymer-resin.html\"/g" {}' \n \;

rm $temp_file
