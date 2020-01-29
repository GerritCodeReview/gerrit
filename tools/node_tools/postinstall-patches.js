/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

try {
  require.resolve('shelljs');
} catch (e) {
  // We are in an bazel managed external node_modules repository
  // and the resolve has failed because node did not preserve the symlink
  // when loading the script.
  // This can be fixed using the --preserve-symlinks-main flag which
  // is introduced in node 10.2.0
  console.warn(
      `Running postinstall-patches.js script in an external repository requires --preserve-symlinks-main node flag introduced in node 10.2.0. ` +
      `Current node version is ${process.version}. Node called with '${process.argv.join(" ")}'.`);
  process.exit(0);
}

const {set, cd, sed} = require('shelljs');
const path = require('path');
const log = console.log;

log('===== about to run the postinstall-patches.js script     =====');
// fail on first error
set('-e');
// print commands as being executed
set('-v');
// jump to project root
cd(path.join(__dirname, '../..'));

// TODO(davido): Replace font-roboto with font-roboto-local in paper-style.
// https://gerrit-review.googlesource.com/c/gerrit/+/246796
// https://bugs.chromium.org/p/gerrit/issues/detail?id=11993
// https://gerrit-review.googlesource.com/c/gerrit/+/246692
// https://github.com/PolymerElements/paper-styles/pull/165
log('\n# patch: paper-styles to replace font-roboto with font-roboto-local');
sed('-i', '/font-roboto/', '/font-roboto-local/',
    'polygerrit-ui/app/node_modules/@polymer/paper-styles/typography.js');
sed('-i', '/font-roboto/', '/font-roboto-local/',
    'polygerrit-ui/app/node_modules/@polymer/paper-styles/classes/typography.js');

log('===== finished running the postinstall-patches.js script =====');
