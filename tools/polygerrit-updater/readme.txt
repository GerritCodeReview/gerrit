This folder contains tool to update Polymer components to class based components.
This is a temporary tools, it will be removed in a few weeks.

How to use this tool: initial steps
1) Important - Commit and push all your changes. Otherwise, you can loose you work.

2) Ensure, that tools/polygerrit-updater is your current directory

3) Run
npm install

4) If you want to convert the whole project, run
npm run convert -- --i \
  --root ../../polygerrit-ui --src app/elements --r \
  --exclude app/elements/core/gr-reporting/gr-reporting.js \
     app/elements/diff/gr-comment-api/gr-comment-api-mock.js \
     app/elements/plugins/gr-dom-hooks/gr-dom-hooks.js

You can convert only specific files (can be useful if you want to convert some files in your change)
npm run convert -- --i \
  --root ../../polygerrit-ui
  --src app/elements/file1.js \
      app/elements/folder/file2.js

4) Search for the following string in all .js files:
//This file has the following problems with comments:

If you find such string in a .js file - you must manually fix comments in this file.
(It is expected that you shouldn't have such problems)

5) Go to the gerrit root folder and run
npm run eslintfix

(If you are doing it for the first time, run the following command before in gerrit root folder:
npm run install)

Fix error after eslintfix (if exists)

6) If you are doing conversion for the whole project, make the followin changes:

a) Add
<link rel="import" href="../../../types/polymer-behaviors.js">
to
polygerrit-ui/app/elements/shared/gr-autocomplete-dropdown/gr-autocomplete-dropdown.html

b) Update polymer.json with the following rules:
  "lint": {
    "rules": ["polymer-2"],
    "ignoreWarnings": ["deprecated-dom-call"]
  }



5) Commit changed files.

6) You can update excluded files later.
