/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
