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

// The IntelliJ (and probably other IDEs) passes test names as a regexp in
// the format:
// --grep=/some regexp.../
// But mochajs doesn't expect the '/' characters before and after the regexp.
// The code below patches input args and removes '/' if they exists.
function installPatch(karma) {
  const originalKarmaStart = karma.start;

  karma.start = function(config, ...args) {
    const regexpGrepPrefix = '--grep=/';
    const regexpGrepSuffix = '/';
    if (config && config.args) {
      for (let i = 0; i < config.args.length; i++) {
        const arg = config.args[i];
        if (arg.startsWith(regexpGrepPrefix) && arg.endsWith(regexpGrepSuffix)) {
          const regexpText = arg.slice(regexpGrepPrefix.length, -regexpGrepPrefix.length);
          config.args[i] = '--grep=' + regexpText;
        }
      }
    }
    originalKarmaStart.apply(this, [config, ...args]);
  }

}

const karma = window.__karma__;
if (karma && karma.start && !karma.__grep_patch_installed__) {
  karma.__grep_patch_installed__ = true;
  installPatch(karma);
}
