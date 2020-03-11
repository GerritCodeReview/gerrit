<!--
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
-->

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-input/iron-input.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../shared/gr-select/gr-select.html">

<dom-module id="gr-edit-preferences">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div id="editPreferences" class="gr-form-styles">
      <section>
        <span class="title">Tab width</span>
        <span class="value">
          <iron-input
              type="number"
              prevent-invalid-input
              allowed-pattern="[0-9]"
              bind-value="{{editPrefs.tab_size}}"
              on-keypress="_handleEditPrefsChanged"
              on-change="_handleEditPrefsChanged">
            <input
                is="iron-input"
                type="number"
                prevent-invalid-input
                allowed-pattern="[0-9]"
                bind-value="{{editPrefs.tab_size}}"
                on-keypress="_handleEditPrefsChanged"
                on-change="_handleEditPrefsChanged">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Columns</span>
        <span class="value">
          <iron-input
              type="number"
              prevent-invalid-input
              allowed-pattern="[0-9]"
              bind-value="{{editPrefs.line_length}}"
              on-keypress="_handleEditPrefsChanged"
              on-change="_handleEditPrefsChanged">
            <input
                is="iron-input"
                type="number"
                prevent-invalid-input
                allowed-pattern="[0-9]"
                bind-value="{{editPrefs.line_length}}"
                on-keypress="_handleEditPrefsChanged"
                on-change="_handleEditPrefsChanged">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Indent unit</span>
        <span class="value">
          <iron-input
              type="number"
              prevent-invalid-input
              allowed-pattern="[0-9]"
              bind-value="{{editPrefs.indent_unit}}"
              on-keypress="_handleEditPrefsChanged"
              on-change="_handleEditPrefsChanged">
            <input
                is="iron-input"
                type="number"
                prevent-invalid-input
                allowed-pattern="[0-9]"
                bind-value="{{editPrefs.indent_unit}}"
                on-keypress="_handleEditPrefsChanged"
                on-change="_handleEditPrefsChanged">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Syntax highlighting</span>
        <span class="value">
          <input
              id="editSyntaxHighlighting"
              type="checkbox"
              checked$="[[editPrefs.syntax_highlighting]]"
              on-change="_handleEditSyntaxHighlightingChanged">
        </span>
      </section>
      <section>
        <span class="title">Show tabs</span>
        <span class="value">
          <input
              id="editShowTabs"
              type="checkbox"
              checked$="[[editPrefs.show_tabs]]"
              on-change="_handleEditShowTabsChanged">
        </span>
      </section>
      <section>
        <span class="title">Match brackets</span>
        <span class="value">
          <input
              id="showMatchBrackets"
              type="checkbox"
              checked$="[[editPrefs.match_brackets]]"
              on-change="_handleMatchBracketsChanged">
        </span>
      </section>
      <section>
        <span class="title">Line wrapping</span>
        <span class="value">
          <input
              id="editShowLineWrapping"
              type="checkbox"
              checked$="[[editPrefs.line_wrapping]]"
              on-change="_handleEditLineWrappingChanged">
        </span>
      </section>
      <section>
        <span class="title">Indent with tabs</span>
        <span class="value">
          <input
              id="showIndentWithTabs"
              type="checkbox"
              checked$="[[editPrefs.indent_with_tabs]]"
              on-change="_handleIndentWithTabsChanged">
        </span>
      </section>
      <section>
        <span class="title">Auto close brackets</span>
        <span class="value">
          <input
              id="showAutoCloseBrackets"
              type="checkbox"
              checked$="[[editPrefs.auto_close_brackets]]"
              on-change="_handleAutoCloseBracketsChanged">
        </span>
      </section>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-edit-preferences.js"></script>
</dom-module>
