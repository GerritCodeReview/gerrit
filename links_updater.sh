DIR=$(pwd)
find $DIR/polygerrit-ui/app/ -name *.html -type f -not -path "*/node_modules/*" -exec sh -c 'echo {} && echo {}' \n \; >html_files
bazel run tools/node_tools/polygerrit_app_preprocessor:links-updater-bin -- $DIR/html_files $DIR/polygerrit-ui/app/redirects.json

