<!--
@license
Copyright (C) 2017 The Android Open Source Project

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
<link rel="import" href="../base-url-behavior/base-url-behavior.html">
<link rel="import" href="../gr-url-encoding-behavior/gr-url-encoding-behavior.html">
<script>
(function(window) {
  'use strict';

  window.Gerrit = window.Gerrit || {};

  /** @polymerBehavior Gerrit.ListViewBehavior */
  Gerrit.ListViewBehavior = [{
    computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    computeShownItems(items) {
      return items.slice(0, 25);
    },

    getUrl(path, item) {
      return this.getBaseUrl() + path + this.encodeURL(item, true);
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
  Gerrit.BaseUrlBehavior,
  Gerrit.URLEncodingBehavior,
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
    Gerrit.ListViewMixin = base =>
      class extends base {
        computeLoadingClass(loading) {}

        computeShownItems(items) {}
      };
  }
})(window);

</script>
