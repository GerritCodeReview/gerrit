/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
  'use strict';

  Polymer({
    is: 'gr-highlight-text',
    _legacyUndefinedCheck: true,

    properties: {
      content: {
        type: String,
      },
      highlightText: {
        type: String,
        value: '',
      },
    },

    observers: [
      '_contentOrKeywordsChanged(content, highlightText)',
    ],

    _contentOrKeywordsChanged(content, highlightText) {
      const container = Polymer.dom(this.$.container);
      container.innerHTML = this._highlight(content, highlightText);
    },

    _highlight(content, highlightText) {
      return highlightText ?
        content.replace(
            new RegExp(`(${highlightText})`, 'gi'),
            `<span class="highlight-text">$1</span>`
        ) :
        content;
    },
  });
})();