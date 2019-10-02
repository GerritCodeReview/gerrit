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
(function() {
  'use strict';

  const COPY_TIMEOUT_MS = 1000;

  Polymer({
    is: 'gr-copy-clipboard',

    properties: {
      text: String,
      buttonTitle: String,
      hasTooltip: {
        type: Boolean,
        value: false,
      },
      hideInput: {
        type: Boolean,
        value: false,
      },
    },

    focusOnCopy() {
      this.$.button.focus();
    },

    _computeInputClass(hideInput) {
      return hideInput ? 'hideInput' : '';
    },

    _handleInputTap(e) {
      e.preventDefault();
      Polymer.dom(e).rootTarget.select();
    },

    _copyToClipboard() {
      if (this.hideInput) {
        this.$.input.style.display = 'block';
      }
      this.$.input.focus();
      this.$.input.select();
      document.execCommand('copy');
      if (this.hideInput) {
        this.$.input.style.display = 'none';
      }
      this.$.icon.icon = 'gr-icons:check';
      this.async(
          () => this.$.icon.icon = 'gr-icons:content-copy',
          COPY_TIMEOUT_MS);
    },
  });
})();
