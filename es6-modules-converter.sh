git restore -- package.json
git restore -- polygerrit-ui/app/package.json
bazel run @nodejs//:yarn add polymer-modulizer
rm -rf polygerrit-ui/app/node_modules
bazel fetch @ui_npm//:node_modules
DIR=$(pwd)

cd polygerrit-ui/app
rm -rf bower_components
bower install

echo '<script src="link-text-parser.js"></script>' >elements/shared/gr-linked-text/link-text-parser.html
sed -i 's/<script src="link-text-parser.js"><\/script>/<link rel="import" href=".\/link-text-parser.html">/g' elements/shared/gr-linked-text/gr-linked-text.html
mkdir -p components/web-component-tester/data
cp bower_components/web-component-tester/data/a11ySuite.js components/web-component-tester/data
mkdir -p components/wct-browser-legacy
echo "" >components/wct-browser-legacy/browser.js
rm -rf modulizer_out
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit-ui-dependencies --force --import-style name
echo "<script src='./gr-app.js' type='module'></script>" >modulizer_out/elements/gr-app.html

rm -rf components
rm -rf bower_components

cd $DIR

temp_file=$(mktemp)
(cd $DIR/polygerrit-ui/app/modulizer_out && find ./ -name '*.js' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$temp_file)

bazel run tools/node_tools/polygerrit_app_preprocessor:modulizer-postprocessor-bin -- $DIR/polygerrit-ui/app $temp_file

git restore -- polygerrit-ui/app/package.json
git restore -- package.json
git restore -- polygerrit-ui/app/elements/shared/gr-linked-text/gr-linked-text.html

rm -rf polygerrit-ui/app/node_modules
bazel fetch @ui_npm//:node_modules


