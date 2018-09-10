/**
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
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/shared-styles.js';
import '../gr-button/gr-button.js';
import '../gr-icons/gr-icons.js';

const COPY_TIMEOUT_MS = 1000;

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      .text {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
      }
      .copyText {
        flex-grow: 1;
        margin-right: .3em;
      }
      .hideInput {
        display: none;
      }
      input {
        font-family: var(--monospace-font-family);
        font-size: inherit;
        @apply --text-container-style;
      }
      #icon {
        height: 1.2em;
        width: 1.2em;
      }
    </style>
    <div class="text">
        <input id="input" is="iron-input" class\$="copyText [[_computeInputClass(hideInput)]]" type="text" bind-value="[[text]]" on-tap="_handleInputTap" readonly="">
        <gr-button id="button" link="" has-tooltip="[[hasTooltip]]" class="copyToClipboard" title="[[buttonTitle]]" on-tap="_copyToClipboard">
          <iron-icon id="icon" icon="gr-icons:content-copy"></iron-icon>
        </gr-button>
    </div>
`,

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
  }
});
