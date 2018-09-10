/**
@license
Copyright (C) 2018 The Android Open Source Project

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

import '../../../styles/shared-styles.js';
import '../gr-copy-clipboard/gr-copy-clipboard.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      .commandContainer {
        margin-bottom: .75em;
      }
      .commandContainer {
        background-color: var(--shell-command-background-color);
        padding: .5em .5em .5em 2.5em;
        position: relative;
        width: 100%;
      }
      .commandContainer:before {
        background: var(--shell-command-decoration-background-color);
        bottom: 0;
        box-sizing: border-box;
        content: '\$';
        display: block;
        left: 0;
        padding: .8em;
        position: absolute;
        top: 0;
        width: 2em;
      }
      .commandContainer gr-copy-clipboard {
        --text-container-style: {
          border: none;
        }
      }
    </style>
    <label>[[label]]</label>
    <div class="commandContainer">
      <gr-copy-clipboard text="[[command]]"></gr-copy-clipboard>
    </div>
`,

  is: 'gr-shell-command',

  properties: {
    command: String,
    label: String,
  },

  focusOnCopy() {
    this.$$('gr-copy-clipboard').focusOnCopy();
  }
});
