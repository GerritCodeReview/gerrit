# Temporary file - needed only for es6 module conversion

clean_temporary_changes() {
  git restore -- package.json
  git restore -- yarn.lock
  git restore --staged polygerrit-ui/app/
  git restore polygerrit-ui/app/
  git clean -f polygerrit-ui/app
  rm -rf polygerrit-ui/app/node_modules
  rm -rf polygerrit-ui/app/bower_components
  rm -rf polygerrit-ui/app/modulizer_out
  rm -rf polygerrit-ui/app/components
  bazel fetch @ui_npm//:node_modules
}

if [[ "$1" == "--clean-only" ]]; then
  clean_temporary_changes
  exit 0
fi

if [[ `git status --porcelain` &&  "$1" != "--force" ]]; then
  echo "Please commit your changes before continue or run the tool with  --force flag"
  exit 1
fi

set -e

# Revert all changes made by this script, so script can be run with --force multiple times
clean_temporary_changes

# Temporary install the polymer-modulizer tool
bazel run @nodejs//:yarn add polymer-modulizer

# Update some links before run polymer-modulizer (--force to ignore already changed files)
./polygerrit-links-updater.sh --force

# Updates inside polygerrit-ui/app directory
DIR=$(pwd)
cd polygerrit-ui/app

# Update link in tests

# Update html files - replace <script src=...> with <link rel="..."> and generate
# .html files with a single tag <script src="...">
# Without this update polymer-modulizer merges some .js files in other files.
html_files=$(mktemp)
generated_html_files=$(mktemp)
find ./ -name '*.html' -type f \
  -not -path "*/node_modules/*" \
  -not -path "*/samples/*" \
  -not -path "*/gr-diff/gr-diff-root.html" \
  -not -path "*/embed/gr-diff.html" \
  -not -path "*/elements/change/gr-change-metadata/test/plugin.html" \
  -not -path "*/elements/change/gr-reply-dialog/test/plugin.html" \
  -not -path "*/elements/test/plugin.html" \
  -not -path "*/test/index.html" \
  -exec sh -c 'echo {}' \n \; >$html_files
bazel run //tools/node_tools/polygerrit_app_preprocessor:script-tag-replacer-bin -- $DIR/polygerrit-ui/app $html_files $generated_html_files

# Temporary install bower component, so polymer-modulizer can find them
bower install

# Create temporary files, so polymer-modulizer can find them
mkdir -p components/web-component-tester/data
cp bower_components/web-component-tester/data/a11ySuite.js components/web-component-tester/data
mkdir -p components/wct-browser-legacy
echo "" >components/wct-browser-legacy/browser.js

# Run polymer-modulizer, manually create the new gr-app.html entrypoint
bazel run @npm//polymer-modulizer/bin:modulizer --run_under="cd $PWD && " -- --npm-version 1.0.0 --npm-name polygerrit-ui-dependencies --force --import-style name --package-type application
echo "<script src='./gr-app.js' type='module'></script>" >modulizer_out/elements/gr-app.html

# Postprocessing after the polymer-modulizer tool
find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "s/<script src=\"\/node_modules\/%40webcomponents/<script src=\"\/node_modules\/@webcomponents/g" {}' \n \;
find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "s/<script src=\"\/node_modules\/@webcomponents\/webcomponentsjs\/webcomponents-bundle.js\"/<script src=\"\/node_modules\/@webcomponents\/webcomponentsjs\/webcomponents-lite.js\"/g" {}' \n \;

find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -i "/await readyToTest();/d" {}' \n \;
find ./modulizer_out -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c 'sed -E -i "s/suite\(('.*'), async/suite(\1,/g" {}' \n \;

# Remove some generated directories
rm -rf modulizer_out/node_modules
rm -rf modulizer_out/polymer
rm -rf modulizer_out/polymer-resin

# Remove temporary files which doesn't need for the next actions
rm -rf components
rm -rf bower_components
cat $generated_html_files | xargs -n1 -d '\n' rm

# Undo all changes made to polygerrit-ui/app source code (the modulizer_out folder is not deleted)
cd $DIR
git restore polygerrit-ui/app/

# Remove files with empty templates (eslint and/or build will report an error if this list is not valid/full)
rm polygerrit-ui/app/behaviors/gr-tooltip-behavior/gr-tooltip-behavior.html \
  polygerrit-ui/app/elements/plugins/gr-admin-api/gr-admin-api.html \
  polygerrit-ui/app/elements/core/gr-reporting/gr-reporting.html \
  polygerrit-ui/app/elements/diff/gr-diff-processor/gr-diff-processor.html \
  polygerrit-ui/app/elements/plugins/gr-attribute-helper/gr-attribute-helper.html \
  polygerrit-ui/app/elements/plugins/gr-change-metadata-api/gr-change-metadata-api.html \
  polygerrit-ui/app/elements/plugins/gr-dom-hooks/gr-dom-hooks.html \
  polygerrit-ui/app/elements/plugins/gr-endpoint-param/gr-endpoint-param.html \
  polygerrit-ui/app/elements/plugins/gr-event-helper/gr-event-helper.html \
  polygerrit-ui/app/elements/plugins/gr-plugin-host/gr-plugin-host.html \
  polygerrit-ui/app/elements/plugins/gr-popup-interface/gr-popup-interface.html \
  polygerrit-ui/app/elements/plugins/gr-repo-api/gr-repo-api.html \
  polygerrit-ui/app/elements/plugins/gr-settings-api/gr-settings-api.html \
  polygerrit-ui/app/elements/plugins/gr-styles-api/gr-styles-api.html \
  polygerrit-ui/app/elements/plugins/gr-theme-api/gr-theme-api.html \
  polygerrit-ui/app/elements/shared/gr-js-api-interface/gr-js-api-interface.html \
  polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-etag-decorator.html \
  polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-api-interface.html \
  polygerrit-ui/app/elements/shared/gr-storage/gr-storage.html \
  polygerrit-ui/app/elements/shared/gr-select/gr-select.html

# To prevent history, renames manuall .html files to .js files and commit them as a separate change.
find ./polygerrit-ui/app -name '*.html' -type f \
  -not -path "*/node_modules/*" \
  -not -path "*_test.html" \
  -not -path "*/modulizer_out/*" \
  -not -path "*/styles/themes/dark-theme.html" \
  -not -path "*/samples/*" \
  -not -path "*/gr-diff/gr-diff-root.html" \
  -not -path "*/embed/gr-diff.html" \
  -not -path "*/elements/change/gr-change-metadata/test/plugin.html" \
  -not -path "*/elements/change/gr-reply-dialog/test/plugin.html" \
  -not -path "*/elements/test/plugin.html" \
  -not -path "*/test/index.html" \
  -not -path "*/gr-embed-dashboard/gr-embed-dashboard.html" \
  -exec sh -c './rename-html.sh {}' \;

git commit -m "Rename .html files to .js files

Git doesn't track renaming if a file has too much changes. The change renames
.html files to .js files. This change must be submitted together with es6
modules conversion.

The build and tests fail on this change - this is expected and must be
overwritten manually.

This change was produced automatically by ./es6-modules-converter.sh script.
No manual changes were made."

# Postprocess the result of the polymer-modulizer tool:
# The modulizer-postprocessor fixes import paths, makes minor changes and extracts templates
# to a separate file.
temp_file=$(mktemp)
(cd $DIR/polygerrit-ui/app/modulizer_out && find ./ -name '*.js' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$temp_file)
bazel run //tools/node_tools/polygerrit_app_preprocessor:modulizer-postprocessor-bin -- $DIR/polygerrit-ui/app $temp_file

# Copy updated files from modulizer_out folder to the polygerrit-ui/app folder
(cd $DIR/polygerrit-ui/app/modulizer_out && find ./ -name '*_test.html' -type f -not -path "*/node_modules/*" -exec sh -c "cp '{}' '$DIR/polygerrit-ui/app/{}'" \n \;)
cp polygerrit-ui/app/modulizer_out/elements/gr-app.html polygerrit-ui/app/elements/gr-app.html

# Remove all temporary changes
rm -rf polygerrit-ui/app/modulizer_out

git restore -- polygerrit-ui/app/package.json
git restore -- polygerrit-ui/app/yarn.lock
git restore -- package.json
git restore -- yarn.lock

rm -rf polygerrit-ui/app/node_modules
bazel fetch @ui_npm//:node_modules

rm -rf node_modules

# Sometimes the first fetch fails.
# Do it twice
set +e
bazel fetch @npm//:node_modules
set -e
bazel fetch @npm//:node_modules
npm run eslintfix

git add polygerrit-ui/app
git commit -m "Convert polygerrit to es6-modules

This change replace all HTML imports with es6-modules. The only exceptions are:
* gr-app.html file, which can be deleted only after updating the
  gerrit/httpd/raw/PolyGerritIndexHtml.soy file.
* dark-theme.html which is loaded via importHref. Must be updated manually
  later in a separate change.

This change was produced automatically by ./es6-modules-converter.sh script.
No manual changes were made.
"


echo "Conversion complete. You can push your changes."