git checkout origin/master -- polygerrit-ui/app/package.json
cd polygerrit-ui/app
mkdir -p components/web-component-tester/data
cp bower_components/web-component-tester/data/a11ySuite.js components/web-component-tester/data
rm -rf modulizer_out
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit-ui-dependencies --force --exclude "elements/diff/gr-diff-builder/gr-diff-builder.html" --exclude "elements/diff/gr-diff-builder/gr-diff-builder.js" --import-style name
echo "<script src='./gr-app.js' type='module'></script>" >elements/gr-app.html
rm -rf components
cd ../../
git checkout origin/master -- polygerrit-ui/app/package.json



