# Temporary file - needed only for es6 module conversion

# The script updates some links according to the
# polygerrit-ui/app/partial-redirects.json rules

set -e

if [[ `git status --porcelain` &&  "$1" != "--force" ]]; then
  echo "Please commit your changes before continue or run the polygerrit-links-updater.sh with  --force flag"
  exit 1
fi

DIR=$(pwd)
html_files_list=$(mktemp)
find $DIR/polygerrit-ui/app/ -name *.html -type f -not -path "*/node_modules/*" -exec sh -c 'echo {} && echo {}' \n \; >$html_files_list
bazel run tools/node_tools/polygerrit_app_preprocessor:links-updater-bin -- $html_files_list $DIR/polygerrit-ui/app/partial-redirects.json

find polygerrit-ui/app/ -name '*.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "s/href=\"\/bower_components\/polymer-resin\/standalone\/polymer-resin.html\"/href=\"\/node_modules\/polymer-bridges\/polymer-resin\/standalone\/polymer-resin.html\"/g" {}' \n \;
sed -i '/<link rel="import" href="\/node_modules\/polymer-bridges\/polymer-resin\/standalone\/polymer-resin.html"/i <link rel="import" href="\/bower_components\/polymer\/polymer.html">' polygerrit-ui/app/test/common-test-setup.html

rm $html_files_list
