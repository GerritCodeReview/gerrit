DIR=$(pwd)
temp_file=$(mktemp)
find $DIR/polygerrit-ui/app/ -name *.html -type f -not -path "*/node_modules/*" -exec sh -c 'echo {} && echo {}' \n \; >$temp_file
bazel run tools/node_tools/polygerrit_app_preprocessor:links-updater-bin -- $temp_file $DIR/polygerrit-ui/app/redirects.json
rm $temp_file

