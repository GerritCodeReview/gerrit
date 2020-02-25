#git restore -- package.json
#git restore -- polygerrit-ui/app/package.json
#bazel run @nodejs//:yarn add polymer-modulizer
#rm -rf polygerrit-ui/app/node_modules
#bazel fetch @ui_npm//:node_modules
DIR=$(pwd)

cd polygerrit-ui/app
rm -rf bower_components
rm -rf modulizer_out

temp_file_original_js=$(mktemp)
find ./ -name '*.js' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$temp_file_original_js

tmp_html_files=$(mktemp)
find ./ -name '*.html' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$tmp_html_files
bazel run //tools/node_tools/polygerrit_app_preprocessor:script-tag-replacer-bin -- $DIR/polygerrit-ui/app $tmp_html_files


exit 0
bower install

mkdir -p components/web-component-tester/data
cp bower_components/web-component-tester/data/a11ySuite.js components/web-component-tester/data
mkdir -p components/wct-browser-legacy
echo "" >components/wct-browser-legacy/browser.js
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit-ui-dependencies --force --import-style name --package-type application
echo "<script src='./gr-app.js' type='module'></script>" >modulizer_out/elements/gr-app.html

rm -rf components
rm -rf bower_components
rm elements/shared/gr-linked-text/link-text-parser.html
git restore -- elements/shared/gr-linked-text/gr-linked-text.html
cd $DIR
temp_file=$(mktemp)
(cd $DIR/polygerrit-ui/app/modulizer_out && find ./ -name '*.js' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$temp_file)

bazel run //tools/node_tools/polygerrit_app_preprocessor:modulizer-postprocessor-bin -- $DIR/polygerrit-ui/app $temp_file $temp_file_original_js

git restore -- polygerrit-ui/app/package.json
git restore -- package.json
git restore -- polygerrit-ui/app/elements/shared/gr-linked-text/gr-linked-text.html

rm -rf polygerrit-ui/app/node_modules
bazel fetch @ui_npm//:node_modules
