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

import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-form-styles"></style>
    <div id="editPreferences" class="gr-form-styles">
      <section>
        <span class="title">Tab width</span>
        <span class="value">
          <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.tab_size}}" on-change="_handleEditPrefsChanged">
        </span>
      </section>
      <section>
        <span class="title">Columns</span>
        <span class="value">
          <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.line_length}}" on-change="_handleEditPrefsChanged">
        </span>
      </section>
      <section>
        <span class="title">Indent unit</span>
        <span class="value">
          <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.indent_unit}}" on-change="_handleEditPrefsChanged">
        </span>
      </section>
      <section>
        <span class="title">Syntax highlighting</span>
        <span class="value">
          <input id="editSyntaxHighlighting" type="checkbox" checked\$="[[editPrefs.syntax_highlighting]]" on-change="_handleEditSyntaxHighlightingChanged">
        </span>
      </section>
      <section>
        <span class="title">Show tabs</span>
        <span class="value">
          <input id="editShowTabs" type="checkbox" checked\$="[[editPrefs.show_tabs]]" on-change="_handleEditShowTabsChanged">
        </span>
      </section>
      <section>
        <span class="title">Match brackets</span>
        <span class="value">
          <input id="showMatchBrackets" type="checkbox" checked\$="[[editPrefs.match_brackets]]" on-change="_handleMatchBracketsChanged">
        </span>
      </section>
      <section>
        <span class="title">Line wrapping</span>
        <span class="value">
          <input id="editShowLineWrapping" type="checkbox" checked\$="[[editPrefs.line_wrapping]]" on-change="_handleEditLineWrappingChanged">
        </span>
      </section>
      <section>
        <span class="title">Indent with tabs</span>
        <span class="value">
          <input id="showIndentWithTabs" type="checkbox" checked\$="[[editPrefs.indent_with_tabs]]" on-change="_handleIndentWithTabsChanged">
        </span>
      </section>
      <section>
        <span class="title">Auto close brackets</span>
        <span class="value">
          <input id="showAutoCloseBrackets" type="checkbox" checked\$="[[editPrefs.auto_close_brackets]]" on-change="_handleAutoCloseBracketsChanged">
        </span>
      </section>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-edit-preferences',

  properties: {
    hasUnsavedChanges: {
      type: Boolean,
      notify: true,
      value: false,
    },

    /** @type {?} */
    editPrefs: Object,
  },

  loadData() {
    return this.$.restAPI.getEditPreferences().then(prefs => {
      this.editPrefs = prefs;
    });
  },

  _handleEditPrefsChanged() {
    this.hasUnsavedChanges = true;
  },

  _handleEditSyntaxHighlightingChanged() {
    this.set('editPrefs.syntax_highlighting',
        this.$.editSyntaxHighlighting.checked);
    this._handleEditPrefsChanged();
  },

  _handleEditShowTabsChanged() {
    this.set('editPrefs.show_tabs', this.$.editShowTabs.checked);
    this._handleEditPrefsChanged();
  },

  _handleMatchBracketsChanged() {
    this.set('editPrefs.match_brackets', this.$.showMatchBrackets.checked);
    this._handleEditPrefsChanged();
  },

  _handleEditLineWrappingChanged() {
    this.set('editPrefs.line_wrapping', this.$.editShowLineWrapping.checked);
    this._handleEditPrefsChanged();
  },

  _handleIndentWithTabsChanged() {
    this.set('editPrefs.indent_with_tabs', this.$.showIndentWithTabs.checked);
    this._handleEditPrefsChanged();
  },

  _handleAutoCloseBracketsChanged() {
    this.set('editPrefs.auto_close_brackets',
        this.$.showAutoCloseBrackets.checked);
    this._handleEditPrefsChanged();
  },

  save() {
    return this.$.restAPI.saveEditPreferences(this.editPrefs).then(res => {
      this.hasUnsavedChanges = false;
    });
  }
});
