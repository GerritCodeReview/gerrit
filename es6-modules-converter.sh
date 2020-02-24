git checkout origin/master -- polygerrit-ui/app/package.json
cd polygerrit-ui/app
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit --force
cd ../../
git checkout origin/master -- polygerrit-ui/app/package.json



