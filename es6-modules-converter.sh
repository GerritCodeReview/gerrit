git checkout origin/master -- polygerrit-ui/app/package.json
cd polygerrit-ui/app
rm -rf modulizer_out
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit-ui-dependencies --force --exclude "elements/diff/gr-diff-builder/gr-diff-builder.html" --exclude "elements/diff/gr-diff-builder/gr-diff-builder.js"
cd ../../
git checkout origin/master -- polygerrit-ui/app/package.json



