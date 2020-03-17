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
<script>
(function(window) {
  'use strict';

  const PROBE_PATH = '/Documentation/index.html';
  const DOCS_BASE_PATH = '/Documentation';

  let cachedPromise;

  window.Gerrit = window.Gerrit || {};

  /** @polymerBehavior Gerrit.DocsUrlBehavior */
  Gerrit.DocsUrlBehavior = [{

    /**
     * Get the docs base URL from either the server config or by probing.
     *
     * @param {Object} config The server config.
     * @param {!Object} restApi A REST API instance
     * @return {!Promise<string>} A promise that resolves with the docs base
     *     URL.
     */
    getDocsBaseUrl(config, restApi) {
      if (!cachedPromise) {
        cachedPromise = new Promise(resolve => {
          if (config && config.gerrit && config.gerrit.doc_url) {
            resolve(config.gerrit.doc_url);
          } else {
            restApi.probePath(this.getBaseUrl() + PROBE_PATH).then(ok => {
              resolve(ok ? (this.getBaseUrl() + DOCS_BASE_PATH) : null);
            });
          }
        });
      }
      return cachedPromise;
    },

    /** For testing only. */
    _clearDocsBaseUrlCache() {
      cachedPromise = undefined;
    },
  },
  Gerrit.BaseUrlBehavior,
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
    Gerrit.DocsUrlMixin = base =>
      class extends base {
      };
  }
})(window);
</script>
