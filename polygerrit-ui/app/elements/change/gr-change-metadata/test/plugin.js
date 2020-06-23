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
(function() {
  const testHtmlPlugin = document.createElement('dom-module');
  testHtmlPlugin.innerHTML = `
    <template>
      <style>
        html {
          --change-metadata-assignee: {
            display: none;
          }
          --change-metadata-label-status: {
            display: none;
          }
          --change-metadata-strategy: {
            display: none;
          }
          --change-metadata-topic: {
            display: none;
          }
        }
      </style>
    </template>
  `;
  testHtmlPlugin.register('my-plugin-style');

  window.Gerrit.install(plugin => {
    plugin.registerStyleModule('change-metadata', 'my-plugin-style');
  });
})();
