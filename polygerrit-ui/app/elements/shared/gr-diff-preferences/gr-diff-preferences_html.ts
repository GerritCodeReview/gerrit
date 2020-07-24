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
  <div id="diffPreferences" class="gr-form-styles">
    <section>
      <span class="title">Context</span>
      <span class="value">
        <gr-select id="contextSelect" bind-value="{{diffPrefs.context}}">
          <select
            on-keypress="_handleDiffPrefsChanged"
            on-change="_handleDiffPrefsChanged"
          >
            <option value="3">3 lines</option>
            <option value="10">10 lines</option>
            <option value="25">25 lines</option>
            <option value="50">50 lines</option>
            <option value="75">75 lines</option>
            <option value="100">100 lines</option>
            <option value="-1">Whole file</option>
          </select>
        </gr-select>
      </span>
    </section>
    <section>
      <span class="title">Fit to screen</span>
      <span class="value">
        <input
          id="lineWrappingInput"
          type="checkbox"
          checked="[[diffPrefs.line_wrapping]]"
          on-change="_handleLineWrappingTap"
        />
      </span>
    </section>
    <section>
      <span class="title">Diff width</span>
      <span class="value">
        <iron-input
          type="number"
          prevent-invalid-input=""
          allowed-pattern="[0-9]"
          bind-value="{{diffPrefs.line_length}}"
          on-keypress="_handleDiffPrefsChanged"
          on-change="_handleDiffPrefsChanged"
        >
          <input
            is="iron-input"
            type="number"
            id="columnsInput"
            prevent-invalid-input=""
            allowed-pattern="[0-9]"
            bind-value="{{diffPrefs.line_length}}"
            on-keypress="_handleDiffPrefsChanged"
            on-change="_handleDiffPrefsChanged"
          />
        </iron-input>
      </span>
    </section>
    <section>
      <span class="title">Tab width</span>
      <span class="value">
        <iron-input
          type="number"
          prevent-invalid-input=""
          allowed-pattern="[0-9]"
          bind-value="{{diffPrefs.tab_size}}"
          on-keypress="_handleDiffPrefsChanged"
          on-change="_handleDiffPrefsChanged"
        >
          <input
            is="iron-input"
            type="number"
            id="tabSizeInput"
            prevent-invalid-input=""
            allowed-pattern="[0-9]"
            bind-value="{{diffPrefs.tab_size}}"
            on-keypress="_handleDiffPrefsChanged"
            on-change="_handleDiffPrefsChanged"
          />
        </iron-input>
      </span>
    </section>
    <section hidden$="[[!diffPrefs.font_size]]">
      <span class="title">Font size</span>
      <span class="value">
        <iron-input
          type="number"
          prevent-invalid-input=""
          allowed-pattern="[0-9]"
          bind-value="{{diffPrefs.font_size}}"
          on-keypress="_handleDiffPrefsChanged"
          on-change="_handleDiffPrefsChanged"
        >
          <input
            is="iron-input"
            type="number"
            id="fontSizeInput"
            prevent-invalid-input=""
            allowed-pattern="[0-9]"
            bind-value="{{diffPrefs.font_size}}"
            on-keypress="_handleDiffPrefsChanged"
            on-change="_handleDiffPrefsChanged"
          />
        </iron-input>
      </span>
    </section>
    <section>
      <span class="title">Show tabs</span>
      <span class="value">
        <input
          id="showTabsInput"
          type="checkbox"
          checked="[[diffPrefs.show_tabs]]"
          on-change="_handleShowTabsTap"
        />
      </span>
    </section>
    <section>
      <span class="title">Show trailing whitespace</span>
      <span class="value">
        <input
          id="showTrailingWhitespaceInput"
          type="checkbox"
          checked="[[diffPrefs.show_whitespace_errors]]"
          on-change="_handleShowTrailingWhitespaceTap"
        />
      </span>
    </section>
    <section>
      <span class="title">Syntax highlighting</span>
      <span class="value">
        <input
          id="syntaxHighlightInput"
          type="checkbox"
          checked="[[diffPrefs.syntax_highlighting]]"
          on-change="_handleSyntaxHighlightTap"
        />
      </span>
    </section>
    <section>
      <span class="title">Automatically mark viewed files reviewed</span>
      <span class="value">
        <input
          id="automaticReviewInput"
          type="checkbox"
          checked="[[!diffPrefs.manual_review]]"
          on-change="_handleAutomaticReviewTap"
        />
      </span>
    </section>
    <section>
      <div class="pref">
        <span class="title">Ignore Whitespace</span>
        <span class="value">
          <gr-select bind-value="{{diffPrefs.ignore_whitespace}}">
            <select
              on-keypress="_handleDiffPrefsChanged"
              on-change="_handleDiffPrefsChanged"
            >
              <option value="IGNORE_NONE">None</option>
              <option value="IGNORE_TRAILING">Trailing</option>
              <option value="IGNORE_LEADING_AND_TRAILING"
                >Leading &amp; trailing</option
              >
              <option value="IGNORE_ALL">All</option>
            </select>
          </gr-select>
        </span>
      </div>
    </section>
  </div>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
