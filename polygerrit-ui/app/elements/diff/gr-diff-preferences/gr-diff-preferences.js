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

import '../../../../@polymer/iron-input/iron-input.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-storage/gr-storage.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      :host([disabled]) {
        opacity: .5;
        pointer-events: none;
      }
      input,
      select {
        font: inherit;
      }
      input[type="number"] {
        width: 4em;
      }
      .header,
      .actions {
        padding: 1em 1.5em;
      }
      .header,
      .mainContainer,
      .actions {
        background-color: var(--dialog-background-color);
      }
      .header {
        border-bottom: 1px solid var(--border-color);
        font-family: var(--font-family-bold);
      }
      .mainContainer {
        padding: 1em 0;
      }
      .pref {
        align-items: center;
        display: flex;
        padding: .35em 1.5em;
        width: 20em;
      }
      .pref:hover {
        background-color: var(--hover-background-color);
      }
      .pref label {
        cursor: pointer;
        flex: 1;
      }
      .actions {
        border-top: 1px solid var(--border-color);
        display: flex;
        justify-content: flex-end;
      }
      gr-button {
        margin-left: 1em;
      }
    </style>
    <gr-overlay id="prefsOverlay" with-backdrop="">
      <div class="header">
        Diff View Preferences
      </div>
      <div class="mainContainer">
        <div class="pref">
          <label for="contextSelect">Context</label>
          <select id="contextSelect" on-change="_handleContextSelectChange">
            <option value="3">3 lines</option>
            <option value="10">10 lines</option>
            <option value="25">25 lines</option>
            <option value="50">50 lines</option>
            <option value="75">75 lines</option>
            <option value="100">100 lines</option>
            <option value="-1">Whole file</option>
          </select>
        </div>
        <div class="pref">
          <label for="lineWrappingInput">Fit to screen</label>
          <input is="iron-input" type="checkbox" id="lineWrappingInput" on-tap="_handlelineWrappingTap">
        </div>
        <div class="pref" id="columnsPref">
          <label for="columnsInput">Diff width</label>
          <input is="iron-input" type="number" id="columnsInput" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{_newPrefs.line_length}}">
        </div>
        <div class="pref">
          <label for="tabSizeInput">Tab width</label>
          <input is="iron-input" type="number" id="tabSizeInput" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{_newPrefs.tab_size}}">
        </div>
        <div class="pref" hidden\$="[[!_newPrefs.font_size]]">
          <label for="fontSizeInput">Font size</label>
          <input is="iron-input" type="number" id="fontSizeInput" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{_newPrefs.font_size}}">
        </div>
        <div class="pref">
          <label for="showTabsInput">Show tabs</label>
          <input is="iron-input" type="checkbox" id="showTabsInput" on-tap="_handleShowTabsTap">
        </div>
        <div class="pref">
          <label for="showTrailingWhitespaceInput">
            Show trailing whitespace</label>
          <input is="iron-input" type="checkbox" id="showTrailingWhitespaceInput" on-tap="_handleShowTrailingWhitespaceTap">
        </div>
        <div class="pref">
          <label for="syntaxHighlightInput">Syntax highlighting</label>
          <input is="iron-input" type="checkbox" id="syntaxHighlightInput" on-tap="_handleSyntaxHighlightTap">
        </div>
        <div class="pref">
          <label for="automaticReviewInput">Automatically mark viewed files reviewed</label>
          <input is="iron-input" id="automaticReviewInput" type="checkbox" on-tap="_handleAutomaticReviewTap">
        </div>
      </div>
      <div class="actions">
        <gr-button id="cancelButton" link="" on-tap="_handleCancel">
            Cancel</gr-button>
        <gr-button id="saveButton" link="" primary="" on-tap="_handleSave">
            Save</gr-button>
      </div>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-storage id="storage"></gr-storage>
`,

  is: 'gr-diff-preferences',

  properties: {
    prefs: {
      type: Object,
      notify: true,
    },
    localPrefs: {
      type: Object,
      notify: true,
    },
    disabled: {
      type: Boolean,
      value: false,
      reflectToAttribute: true,
    },

    /** @type {?} */
    _newPrefs: Object,
    _newLocalPrefs: Object,
  },

  observers: [
    '_prefsChanged(prefs.*)',
    '_localPrefsChanged(localPrefs.*)',
  ],

  getFocusStops() {
    return {
      start: this.$.contextSelect,
      end: this.$.saveButton,
    };
  },

  resetFocus() {
    this.$.contextSelect.focus();
  },

  _prefsChanged(changeRecord) {
    const prefs = changeRecord.base;
    // NOTE: Object.assign is NOT automatically a deep copy. If prefs adds
    // an object as a value, it must be marked enumerable.
    this._newPrefs = Object.assign({}, prefs);
    this.$.contextSelect.value = prefs.context;
    this.$.showTabsInput.checked = prefs.show_tabs;
    this.$.showTrailingWhitespaceInput.checked = prefs.show_whitespace_errors;
    this.$.lineWrappingInput.checked = prefs.line_wrapping;
    this.$.syntaxHighlightInput.checked = prefs.syntax_highlighting;
    this.$.automaticReviewInput.checked = !prefs.manual_review;
  },

  _localPrefsChanged(changeRecord) {
    const localPrefs = changeRecord.base || {};
    this._newLocalPrefs = Object.assign({}, localPrefs);
  },

  _handleContextSelectChange(e) {
    const selectEl = Polymer.dom(e).rootTarget;
    this.set('_newPrefs.context', parseInt(selectEl.value, 10));
  },

  _handleShowTabsTap(e) {
    this.set('_newPrefs.show_tabs', Polymer.dom(e).rootTarget.checked);
  },

  _handleShowTrailingWhitespaceTap(e) {
    this.set('_newPrefs.show_whitespace_errors',
        Polymer.dom(e).rootTarget.checked);
  },

  _handleSyntaxHighlightTap(e) {
    this.set('_newPrefs.syntax_highlighting',
        Polymer.dom(e).rootTarget.checked);
  },

  _handlelineWrappingTap(e) {
    this.set('_newPrefs.line_wrapping', Polymer.dom(e).rootTarget.checked);
  },

  _handleAutomaticReviewTap(e) {
    this.set('_newPrefs.manual_review', !Polymer.dom(e).rootTarget.checked);
  },

  _handleSave(e) {
    e.stopPropagation();
    this.prefs = this._newPrefs;
    this.localPrefs = this._newLocalPrefs;
    const el = Polymer.dom(e).rootTarget;
    el.disabled = true;
    this.$.storage.savePreferences(this._localPrefs);
    this._saveDiffPreferences().then(response => {
      el.disabled = false;
      if (!response.ok) { return response; }

      this.$.prefsOverlay.close();
    }).catch(err => {
      el.disabled = false;
    });
  },

  _handleCancel(e) {
    e.stopPropagation();
    this.$.prefsOverlay.close();
  },

  open() {
    this.$.prefsOverlay.open().then(() => {
      const focusStops = this.getFocusStops();
      this.$.prefsOverlay.setFocusStops(focusStops);
      this.resetFocus();
    });
  },

  _saveDiffPreferences() {
    return this.$.restAPI.saveDiffPreferences(this.prefs);
  }
});
