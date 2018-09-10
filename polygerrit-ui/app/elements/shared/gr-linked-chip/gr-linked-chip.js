/**
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
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.js';
import '../gr-button/gr-button.js';
import '../gr-icons/gr-icons.js';
import '../gr-limited-text/gr-limited-text.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
        overflow: hidden;
      }
      .container {
        align-items: center;
        background: var(--chip-background-color);
        border-radius: .75em;
        display: inline-flex;
        padding: 0 .5em;
      }
      gr-button.remove:hover,
      gr-button.remove:focus {
        --gr-button: {
          color: #333;
        }
      }
      gr-button.remove {
        --gr-button: {
          border: 0;
          color: var(--deemphasized-text-color);
          font-size: 1.7rem;
          font-weight: normal;
          height: .6em;
          line-height: .6;
          margin-left: .15em;
          padding: 0;
          text-decoration: none;
        }
      }
      .transparentBackground,
      gr-button.transparentBackground {
        background-color: transparent;
      }
      :host([disabled]) {
        opacity: .6;
        pointer-events: none;
      }
      a {
       color: var(--linked-chip-text-color);
      }
      iron-icon {
        height: 1.2rem;
        width: 1.2rem;
      }
    </style>
    <div class\$="container [[_getBackgroundClass(transparentBackground)]]">
      <a href\$="[[href]]">
        <gr-limited-text limit="[[limit]]" text="[[text]]"></gr-limited-text>
      </a>
      <gr-button id="remove" link="" hidden\$="[[!removable]]" hidden="" class\$="remove [[_getBackgroundClass(transparentBackground)]]" on-tap="_handleRemoveTap">
        <iron-icon icon="gr-icons:close"></iron-icon>
      </gr-button>
    </div>
`,

  is: 'gr-linked-chip',

  properties: {
    href: String,
    disabled: {
      type: Boolean,
      value: false,
      reflectToAttribute: true,
    },
    removable: {
      type: Boolean,
      value: false,
    },
    text: String,
    transparentBackground: {
      type: Boolean,
      value: false,
    },

    /**  If provided, sets the maximum length of the content. */
    limit: Number,
  },

  _getBackgroundClass(transparent) {
    return transparent ? 'transparentBackground' : '';
  },

  _handleRemoveTap(e) {
    e.preventDefault();
    this.fire('remove');
  }
});
