/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import {encodeURL, getBaseUrl} from '../../utils/url-util.js';

/** @polymerBehavior ListViewBehavior */
export const ListViewBehavior = [{
  computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  },

  computeShownItems(items) {
    return items.slice(0, 25);
  },

  getUrl(path, item) {
    return getBaseUrl() + path + encodeURL(item, true);
  },

  /**
   * @param {Object} params
   * @return {string}
   */
  getFilterValue(params) {
    if (!params) { return ''; }
    return params.filter || '';
  },

  /**
   * @param {Object} params
   * @return {number}
   */
  getOffsetValue(params) {
    if (params && params.offset) {
      return params.offset;
    }
    return 0;
  },
},
];

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
  const ListViewMixin = base => // eslint-disable-line no-unused-vars
    class extends base {
      computeLoadingClass(loading) {}

      computeShownItems(items) {}
    };
}

// TODO(dmfilippov) Remove the following lines with assignments
// Plugins can use the behavior because it was accessible with
// the global Gerrit... variable. To avoid breaking changes in plugins
// temporary assign global variables.
window.Gerrit = window.Gerrit || {};
window.Gerrit.ListViewBehavior = ListViewBehavior;
