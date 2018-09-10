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
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        /* Used to remove horizontal whitespace between the icons. */
        display: flex;
      }
      gr-button.selected iron-icon {
        color: var(--link-color);
      }
      iron-icon {
        height: 1.3rem;
        width: 1.3rem;
      }
    </style>
    <gr-button id="sideBySideBtn" link="" has-tooltip="" class\$="[[_computeSelectedClass(mode, _VIEW_MODES.SIDE_BY_SIDE)]]" title="Side-by-side diff" on-tap="_handleSideBySideTap">
      <iron-icon icon="gr-icons:side-by-side"></iron-icon>
    </gr-button>
    <gr-button id="unifiedBtn" link="" has-tooltip="" title="Unified diff" class\$="[[_computeSelectedClass(mode, _VIEW_MODES.UNIFIED)]]" on-tap="_handleUnifiedTap">
      <iron-icon icon="gr-icons:unified"></iron-icon>
    </gr-button>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-diff-mode-selector',

  properties: {
    mode: {
      type: String,
      notify: true,
    },

    /**
     * If set to true, the user's preference will be updated every time a
     * button is tapped. Don't set to true if there is no user.
     */
    saveOnChange: {
      type: Boolean,
      value: false,
    },

    /** @type {?} */
    _VIEW_MODES: {
      type: Object,
      readOnly: true,
      value: {
        SIDE_BY_SIDE: 'SIDE_BY_SIDE',
        UNIFIED: 'UNIFIED_DIFF',
      },
    },
  },

  /**
   * Set the mode. If save on change is enabled also update the preference.
   */
  setMode(newMode) {
    if (this.saveOnChange && this.mode && this.mode !== newMode) {
      this.$.restAPI.savePreferences({diff_view: newMode});
    }
    this.mode = newMode;
  },

  _computeSelectedClass(diffViewMode, buttonViewMode) {
    return buttonViewMode === diffViewMode ? 'selected' : '';
  },

  _handleSideBySideTap() {
    this.setMode(this._VIEW_MODES.SIDE_BY_SIDE);
  },

  _handleUnifiedTap() {
    this.setMode(this._VIEW_MODES.UNIFIED);
  }
});
