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

import '../gr-account-link/gr-account-link.js';
import '../gr-button/gr-button.js';
import '../gr-icons/gr-icons.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
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
      :host([show-avatar]) .container {
        padding-left: 0;
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
      :host:focus {
        border-color: transparent;
        box-shadow: none;
        outline: none;
      }
      :host:focus .container,
      :host:focus gr-button {
        background: #ccc;
      }
      .transparentBackground,
      gr-button.transparentBackground {
        background-color: transparent;
        padding: 0;
      }
      :host([disabled]) {
        opacity: .6;
        pointer-events: none;
      }
      iron-icon {
        height: 1.2rem;
        width: 1.2rem;
      }
    </style>
    <div class\$="container [[_getBackgroundClass(transparentBackground)]]">
      <gr-account-link account="[[account]]" additional-text="[[additionalText]]">
      </gr-account-link>
      <gr-button id="remove" link="" hidden\$="[[!removable]]" hidden="" tabindex="-1" aria-label="Remove" class\$="remove [[_getBackgroundClass(transparentBackground)]]" on-tap="_handleRemoveTap">
        <iron-icon icon="gr-icons:close"></iron-icon>
      </gr-button>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-account-chip',

  /**
   * Fired to indicate a key was pressed while this chip was focused.
   *
   * @event account-chip-keydown
   */

  /**
   * Fired to indicate this chip should be removed, i.e. when the x button is
   * clicked or when the remove function is called.
   *
   * @event remove
   */

  properties: {
    account: Object,
    additionalText: String,
    disabled: {
      type: Boolean,
      value: false,
      reflectToAttribute: true,
    },
    removable: {
      type: Boolean,
      value: false,
    },
    showAvatar: {
      type: Boolean,
      reflectToAttribute: true,
    },
    transparentBackground: {
      type: Boolean,
      value: false,
    },
  },

  ready() {
    this._getHasAvatars().then(hasAvatars => {
      this.showAvatar = hasAvatars;
    });
  },

  _getBackgroundClass(transparent) {
    return transparent ? 'transparentBackground' : '';
  },

  _handleRemoveTap(e) {
    e.preventDefault();
    this.fire('remove', {account: this.account});
  },

  _getHasAvatars() {
    return this.$.restAPI.getConfig().then(cfg => {
      return Promise.resolve(!!(cfg && cfg.plugin && cfg.plugin.has_avatars));
    });
  }
});
