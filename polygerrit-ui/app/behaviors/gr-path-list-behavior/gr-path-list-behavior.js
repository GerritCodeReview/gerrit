<!--
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<script src="../../scripts/util.js"></script>
<script>
(function(window) {
  'use strict';

  window.Gerrit = window.Gerrit || {};
  /** @polymerBehavior Gerrit.PathListBehavior */
  Gerrit.PathListBehavior = {

    COMMIT_MESSAGE_PATH: '/COMMIT_MSG',
    MERGE_LIST_PATH: '/MERGE_LIST',

    /**
     * @param {string} a
     * @param {string} b
     * @return {number}
     */
    specialFilePathCompare(a, b) {
      // The commit message always goes first.
      if (a === Gerrit.PathListBehavior.COMMIT_MESSAGE_PATH) {
        return -1;
      }
      if (b === Gerrit.PathListBehavior.COMMIT_MESSAGE_PATH) {
        return 1;
      }

      // The merge list always comes next.
      if (a === Gerrit.PathListBehavior.MERGE_LIST_PATH) {
        return -1;
      }
      if (b === Gerrit.PathListBehavior.MERGE_LIST_PATH) {
        return 1;
      }

      const aLastDotIndex = a.lastIndexOf('.');
      const aExt = a.substr(aLastDotIndex + 1);
      const aFile = a.substr(0, aLastDotIndex) || a;

      const bLastDotIndex = b.lastIndexOf('.');
      const bExt = b.substr(bLastDotIndex + 1);
      const bFile = b.substr(0, bLastDotIndex) || b;

      // Sort header files above others with the same base name.
      const headerExts = ['h', 'hxx', 'hpp'];
      if (aFile.length > 0 && aFile === bFile) {
        if (headerExts.includes(aExt) && headerExts.includes(bExt)) {
          return a.localeCompare(b);
        }
        if (headerExts.includes(aExt)) {
          return -1;
        }
        if (headerExts.includes(bExt)) {
          return 1;
        }
      }
      return aFile.localeCompare(bFile) || a.localeCompare(b);
    },

    computeDisplayPath(path) {
      if (path === Gerrit.PathListBehavior.COMMIT_MESSAGE_PATH) {
        return 'Commit message';
      } else if (path === Gerrit.PathListBehavior.MERGE_LIST_PATH) {
        return 'Merge list';
      }
      return path;
    },

    isMagicPath(path) {
      return !!path &&
          (path === Gerrit.PathListBehavior.COMMIT_MESSAGE_PATH || path ===
              Gerrit.PathListBehavior.MERGE_LIST_PATH);
    },

    computeTruncatedPath(path) {
      return Gerrit.PathListBehavior.truncatePath(
          Gerrit.PathListBehavior.computeDisplayPath(path));
    },

    /**
     * Truncates URLs to display filename only
     * Example
     * // returns '.../text.html'
     * util.truncatePath.('dir/text.html');
     * Example
     * // returns 'text.html'
     * util.truncatePath.('text.html');
     *
     * @param {string} path
     * @param {number=} opt_threshold
     * @return {string} Returns the truncated value of a URL.
     */
    truncatePath(path, opt_threshold) {
      const threshold = opt_threshold || 1;
      const pathPieces = path.split('/');

      if (pathPieces.length <= threshold) { return path; }

      const index = pathPieces.length - threshold;
      // Character is an ellipsis.
      return `\u2026/${pathPieces.slice(index).join('/')}`;
    },
  };

  // eslint-disable-next-line no-unused-vars
  function defineEmptyMixin() {
    // This is a temporary function.
    // Polymer linter doesn't process correctly the following code:
    // class MyElement extends Polymer.mixinBehaviors([legacyBehaviors], ...) {...}
    // To workaround this issue, the mock mixin is declared in this method.
    // In the following changes, legacy behaviors will be converted to mixins.

    /**
     * @polymer
     * @mixinFunction
     */
    Gerrit.PathListMixin = base =>
      class extends base {
        computeDisplayPath(path) {}

        computeTruncatedPath(path) {}
      };
  }
})(window);
</script>
