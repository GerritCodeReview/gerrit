/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-form-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <div id="editPreferences" class="gr-form-styles">
    <section>
      <label for="editTabWidth" class="title">Tab width</label>
      <span class="value">
        <iron-input
          type="number"
          prevent-invalid-input=""
          allowed-pattern="[0-9]"
          bind-value="{{editPrefs.tab_size}}"
          on-keypress="_handleEditPrefsChanged"
          on-change="_handleEditPrefsChanged"
        >
          <input
            is="iron-input"
            id="editTabWidth"
            type="number"
            prevent-invalid-input=""
            allowed-pattern="[0-9]"
            bind-value="{{editPrefs.tab_size}}"
            on-keypress="_handleEditPrefsChanged"
            on-change="_handleEditPrefsChanged"
          />
        </iron-input>
      </span>
    </section>
    <section>
      <label for="editColumns" class="title">Columns</label>
      <span class="value">
        <iron-input
          type="number"
          prevent-invalid-input=""
          allowed-pattern="[0-9]"
          bind-value="{{editPrefs.line_length}}"
          on-keypress="_handleEditPrefsChanged"
          on-change="_handleEditPrefsChanged"
        >
          <input
            id="editColumns"
            is="iron-input"
            type="number"
            prevent-invalid-input=""
            allowed-pattern="[0-9]"
            bind-value="{{editPrefs.line_length}}"
            on-keypress="_handleEditPrefsChanged"
            on-change="_handleEditPrefsChanged"
          />
        </iron-input>
      </span>
    </section>
    <section>
      <label for="indentUnit" class="title">Indent unit</label>
      <span class="value">
        <iron-input
          type="number"
          prevent-invalid-input=""
          allowed-pattern="[0-9]"
          bind-value="{{editPrefs.indent_unit}}"
          on-keypress="_handleEditPrefsChanged"
          on-change="_handleEditPrefsChanged"
        >
          <input
            is="iron-input"
            id="indentUnit"
            type="number"
            prevent-invalid-input=""
            allowed-pattern="[0-9]"
            bind-value="{{editPrefs.indent_unit}}"
            on-keypress="_handleEditPrefsChanged"
            on-change="_handleEditPrefsChanged"
          />
        </iron-input>
      </span>
    </section>
    <section>
      <label for="editSyntaxHighlighting" class="title"
        >Syntax highlighting</label
      >
      <span class="value">
        <input
          id="editSyntaxHighlighting"
          type="checkbox"
          checked$="[[editPrefs.syntax_highlighting]]"
          on-change="_handleEditSyntaxHighlightingChanged"
        />
      </span>
    </section>
    <section>
      <label for="editShowTabs" class="title">Show tabs</label>
      <span class="value">
        <input
          id="editShowTabs"
          type="checkbox"
          checked$="[[editPrefs.show_tabs]]"
          on-change="_handleEditShowTabsChanged"
        />
      </span>
    </section>
    <section>
      <label for="showTrailingWhitespaceInput" class="title"
        >Show trailing whitespace</label
      >
      <span class="value">
        <input
          id="editShowTrailingWhitespaceInput"
          type="checkbox"
          checked$="[[editPrefs.show_whitespace_errors]]"
          on-change="_handleEditShowTrailingWhitespaceTap"
        />
      </span>
    </section>
    <section>
      <label for="showMatchBrackets" class="title">Match brackets</label>
      <span class="value">
        <input
          id="showMatchBrackets"
          type="checkbox"
          checked$="[[editPrefs.match_brackets]]"
          on-change="_handleMatchBracketsChanged"
        />
      </span>
    </section>
    <section>
      <label for="editShowLineWrapping" class="title">Line wrapping</label>
      <span class="value">
        <input
          id="editShowLineWrapping"
          type="checkbox"
          checked$="[[editPrefs.line_wrapping]]"
          on-change="_handleEditLineWrappingChanged"
        />
      </span>
    </section>
    <section>
      <label for="showIndentWithTabs" class="title">Indent with tabs</label>
      <span class="value">
        <input
          id="showIndentWithTabs"
          type="checkbox"
          checked$="[[editPrefs.indent_with_tabs]]"
          on-change="_handleIndentWithTabsChanged"
        />
      </span>
    </section>
    <section>
      <label for="showAutoCloseBrackets" class="title"
        >Auto close brackets</label
      >
      <span class="value">
        <input
          id="showAutoCloseBrackets"
          type="checkbox"
          checked$="[[editPrefs.auto_close_brackets]]"
          on-change="_handleAutoCloseBracketsChanged"
        />
      </span>
    </section>
  </div>
`;
