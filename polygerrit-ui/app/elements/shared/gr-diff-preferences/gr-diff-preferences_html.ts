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
      <label for="contextLineSelect" class="title">Context</label>
      <span class="value">
        <gr-select
          id="contextSelect"
          bind-value="[[_convertToString(diffPrefs.context)]]"
          on-change="_handleDiffContextChanged"
        >
          <select id="contextLineSelect">
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
      <label for="lineWrappingInput" class="title">Fit to screen</label>
      <span class="value">
        <input
          id="lineWrappingInput"
          type="checkbox"
          checked="[[_convertToBoolean(diffPrefs.line_wrapping)]]"
          on-change="_handleLineWrappingTap"
        />
      </span>
    </section>
    <section>
      <label for="columnsInput" class="title">Diff width</label>
      <span class="value">
        <iron-input
          allowed-pattern="[0-9]"
          bind-value="[[_convertToString(diffPrefs.line_length)]]"
          on-change="_handleDiffLineLengthChanged"
        >
          <input id="columnsInput" type="number" />
        </iron-input>
      </span>
    </section>
    <section>
      <label for="tabSizeInput" class="title">Tab width</label>
      <span class="value">
        <iron-input
          allowed-pattern="[0-9]"
          bind-value="[[_convertToString(diffPrefs.tab_size)]]"
          on-change="_handleDiffTabSizeChanged"
        >
          <input id="tabSizeInput" type="number" />
        </iron-input>
      </span>
    </section>
    <section hidden$="[[!diffPrefs.font_size]]">
      <label for="fontSizeInput" class="title">Font size</label>
      <span class="value">
        <iron-input
          allowed-pattern="[0-9]"
          bind-value="[[_convertToString(diffPrefs.font_size)]]"
          on-change="_handleDiffFontSizeChanged"
        >
          <input id="fontSizeInput" type="number" />
        </iron-input>
      </span>
    </section>
    <section>
      <label for="showTabsInput" class="title">Show tabs</label>
      <span class="value">
        <input
          id="showTabsInput"
          type="checkbox"
          checked="[[_convertToBoolean(diffPrefs.show_tabs)]]"
          on-change="_handleShowTabsTap"
        />
      </span>
    </section>
    <section>
      <label for="showTrailingWhitespaceInput" class="title"
        >Show trailing whitespace</label
      >
      <span class="value">
        <input
          id="showTrailingWhitespaceInput"
          type="checkbox"
          checked="[[_convertToBoolean(diffPrefs.show_whitespace_errors)]]"
          on-change="_handleShowTrailingWhitespaceTap"
        />
      </span>
    </section>
    <section>
      <label for="syntaxHighlightInput" class="title"
        >Syntax highlighting</label
      >
      <span class="value">
        <input
          id="syntaxHighlightInput"
          type="checkbox"
          checked="[[_convertToBoolean(diffPrefs.syntax_highlighting)]]"
          on-change="_handleSyntaxHighlightTap"
        />
      </span>
    </section>
    <section>
      <label for="automaticReviewInput" class="title"
        >Automatically mark viewed files reviewed</label
      >
      <span class="value">
        <input
          id="automaticReviewInput"
          type="checkbox"
          checked="[[!_convertToBoolean(diffPrefs.manual_review)]]"
          on-change="_handleAutomaticReviewTap"
        />
      </span>
    </section>
    <section>
      <div class="pref">
        <label for="ignoreWhiteSpace" class="title">Ignore Whitespace</label>
        <span class="value">
          <gr-select
            bind-value="[[_convertToString(diffPrefs.ignore_whitespace)]]"
            on-change="_handleDiffIgnoreWhitespaceChanged"
          >
            <select id="ignoreWhiteSpace">
              <option value="IGNORE_NONE">None</option>
              <option value="IGNORE_TRAILING">Trailing</option>
              <option value="IGNORE_LEADING_AND_TRAILING">
                Leading &amp; trailing
              </option>
              <option value="IGNORE_ALL">All</option>
            </select>
          </gr-select>
        </span>
      </div>
    </section>
  </div>
`;
