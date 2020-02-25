#git restore -- package.json
#git restore -- polygerrit-ui/app/package.json
#bazel run @nodejs//:yarn add polymer-modulizer
cd polygerrit-ui/app
#rm -rf bower_components
bower install
mkdir -p components/web-component-tester/data
cp bower_components/web-component-tester/data/a11ySuite.js components/web-component-tester/data
mkdir -p components/wct-browser-legacy
echo "" >components/wct-browser-legacy/browser.js
rm -rf modulizer_out
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit-ui-dependencies --force --exclude "elements/diff/gr-diff-builder/gr-diff-builder.html" --exclude "elements/diff/gr-diff-builder/gr-diff-builder.js" --import-style name
echo "<script src='./gr-app.js' type='module'></script>" >modulizer_out/elements/gr-app.html
rm -rf components
#rm -rf bower_components
cd ../../
#git restore -- polygerrit-ui/app/package.json
#git restore -- package.json
