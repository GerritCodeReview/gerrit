git restore -- package.json
git restore polygerrit-ui/app/

bazel run @nodejs//:yarn add polymer-modulizer
rm -rf polygerrit-ui/app/node_modules
bazel fetch @ui_npm//:node_modules
DIR=$(pwd)

cd polygerrit-ui/app
git clean -f
rm -rf bower_components
rm -rf modulizer_out

tmp_html_files=$(mktemp)

sed -i '/<link rel="import" href="\/node_modules\/polymer-bridges\/polymer-resin\/standalone\/polymer-resin.html"/i <link rel="import" href="\/bower_components\/polymer\/polymer.html">' test/common-test-setup.html
find ./ -name '*.html' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$tmp_html_files
bazel run //tools/node_tools/polygerrit_app_preprocessor:script-tag-replacer-bin -- $DIR/polygerrit-ui/app $tmp_html_files
bower install

mkdir -p components/web-component-tester/data
cp bower_components/web-component-tester/data/a11ySuite.js components/web-component-tester/data
mkdir -p components/wct-browser-legacy
echo "" >components/wct-browser-legacy/browser.js
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit-ui-dependencies --force --import-style name --package-type application
echo "<script src='./gr-app.js' type='module'></script>" >modulizer_out/elements/gr-app.html

find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "s/<script src=\"\/node_modules\/%40webcomponents/<script src=\"\/node_modules\/@webcomponents/g" {}' \n \;
find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "s/<script src=\"\/node_modules\/@webcomponents\/webcomponentsjs\/webcomponents-bundle.js\"/<script src=\"\/node_modules\/@webcomponents\/webcomponentsjs\/webcomponents-lite.js\"/g" {}' \n \;

find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "/await readyToTest();/d" {}' \n \;
find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -E -i "s/suite\(('.*'), async/suite(\1,/g" {}' \n \;

cd modulizer_out
rm -rf node_modules
rm -rf polymer
rm -rf polymer-resin
cd ..

rm -rf components
rm -rf bower_components
cd $DIR
git restore polygerrit-ui/app/
temp_file=$(mktemp)
(cd $DIR/polygerrit-ui/app/modulizer_out && find ./ -name '*.js' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$temp_file)

bazel run //tools/node_tools/polygerrit_app_preprocessor:modulizer-postprocessor-bin -- $DIR/polygerrit-ui/app $temp_file
cp polygerrit-ui/app/modulizer_out/elements/gr-app.html polygerrit-ui/app/elements/gr-app.html

rm -rf polygerrit-ui/app/modulizer_out

git restore -- polygerrit-ui/app/package.json
git restore -- package.json

#rm -rf polygerrit-ui/app/node_modules
#bazel fetch @ui_npm//:node_modules
