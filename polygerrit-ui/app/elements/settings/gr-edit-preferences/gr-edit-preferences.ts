/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {EditPreferencesInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {convertToString} from '../../../utils/string-util';

@customElement('gr-edit-preferences')
export class GrEditPreferences extends LitElement {
  @query('#editTabWidth') private editTabWidth?: HTMLInputElement;

  @query('#editColumns') private editColumns?: HTMLInputElement;

  @query('#editIndentUnit') private editIndentUnit?: HTMLInputElement;

  @query('#editSyntaxHighlighting')
  private editSyntaxHighlighting?: HTMLInputElement;

  @query('#showAutoCloseBrackets')
  private showAutoCloseBrackets?: HTMLInputElement;

  @query('#showIndentWithTabs') private showIndentWithTabs?: HTMLInputElement;

  @query('#showMatchBrackets') private showMatchBrackets?: HTMLInputElement;

  @query('#editShowLineWrapping')
  private editShowLineWrapping?: HTMLInputElement;

  @query('#editShowTabs') private editShowTabs?: HTMLInputElement;

  @query('#editShowTrailingWhitespaceInput')
  private editShowTrailingWhitespaceInput?: HTMLInputElement;

  @state() private originalTabSize?: Number;

  @state() private originalLineLength?: Number;

  @state() private originalIndentUnit?: Number;

  @state() private originalSyntaxHighlighting?: Boolean;

  @state() private originalShowTabs?: Boolean;

  @state() private originalShowWhitespaceErrors?: Boolean;

  @state() private originalMatchBrackets?: Boolean;

  @state() private originalLineWrapping?: Boolean;

  @state() private originalIndentWithTabs?: Boolean;

  @state() private originalAutoCloseBrackets?: Boolean;

  @property({type: Object})
  editPrefs?: EditPreferencesInfo;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      menuPageStyles,
      formStyles,
      css`
        :host {
          border: none;
          margin-bottom: var(--spacing-xxl);
        }
        h2 {
          font-family: var(--header-font-family);
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-h2);
          line-height: var(--line-height-h2);
        }
      `,
    ];
  }

  override render() {
    const hasUnsavedChanges = this.hasUnsavedChanges();
    return html`
      <h2 id="EditPreferences" class="${hasUnsavedChanges ? 'edited' : ''}">
        Edit Preferences
      </h2>
      <fieldset id="editPreferences">
        <div id="editPreferences" class="gr-form-styles">
          <section>
            <label for="editTabWidth" class="title">Tab width</label>
            <span class="value">
              <iron-input
                .allowedPattern=${'[0-9]'}
                .bindValue=${convertToString(this.editPrefs?.tab_size)}
                @change=${this.handleEditTabWidthChanged}
              >
                <input id="editTabWidth" type="number" />
              </iron-input>
            </span>
          </section>
          <section>
            <label for="editColumns" class="title">Columns</label>
            <span class="value">
              <iron-input
                .allowedPattern=${'[0-9]'}
                .bindValue=${convertToString(this.editPrefs?.line_length)}
                @change=${this.handleEditLineLengthChanged}
              >
                <input id="editColumns" type="number" />
              </iron-input>
            </span>
          </section>
          <section>
            <label for="editIndentUnit" class="title">Indent unit</label>
            <span class="value">
              <iron-input
                .allowedPattern=${'[0-9]'}
                .bindValue=${convertToString(this.editPrefs?.indent_unit)}
                @change=${this.handleEditIndentUnitChanged}
              >
                <input id="editIndentUnit" type="number" />
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
                ?checked=${this.editPrefs?.syntax_highlighting}
                @change=${this.handleEditSyntaxHighlightingChanged}
              />
            </span>
          </section>
          <section>
            <label for="editShowTabs" class="title">Show tabs</label>
            <span class="value">
              <input
                id="editShowTabs"
                type="checkbox"
                ?checked=${this.editPrefs?.show_tabs}
                @change=${this.handleEditShowTabsChanged}
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
                ?checked=${this.editPrefs?.show_whitespace_errors}
                @change=${this.handleEditShowTrailingWhitespaceTap}
              />
            </span>
          </section>
          <section>
            <label for="showMatchBrackets" class="title">Match brackets</label>
            <span class="value">
              <input
                id="showMatchBrackets"
                type="checkbox"
                ?checked=${this.editPrefs?.match_brackets}
                @change=${this.handleMatchBracketsChanged}
              />
            </span>
          </section>
          <section>
            <label for="editShowLineWrapping" class="title"
              >Line wrapping</label
            >
            <span class="value">
              <input
                id="editShowLineWrapping"
                type="checkbox"
                ?checked=${this.editPrefs?.line_wrapping}
                @change=${this.handleEditLineWrappingChanged}
              />
            </span>
          </section>
          <section>
            <label for="showIndentWithTabs" class="title"
              >Indent with tabs</label
            >
            <span class="value">
              <input
                id="showIndentWithTabs"
                type="checkbox"
                ?checked=${this.editPrefs?.indent_with_tabs}
                @change=${this.handleIndentWithTabsChanged}
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
                ?checked=${this.editPrefs?.auto_close_brackets}
                @change=${this.handleAutoCloseBracketsChanged}
              />
            </span>
          </section>
        </div>
        <gr-button
          id="saveEditPrefs"
          @click=${this.handleSaveEditPreferences}
          ?disabled=${!this.hasUnsavedChanges()}
          >Save changes</gr-button
        >
      </fieldset>
    `;
  }

  loadData() {
    return this.restApiService.getEditPreferences().then(prefs => {
      this.originalTabSize = prefs?.tab_size;
      this.originalLineLength = prefs?.line_length;
      this.originalIndentUnit = prefs?.indent_unit;
      this.originalSyntaxHighlighting = prefs?.syntax_highlighting;
      this.originalShowTabs = prefs?.show_tabs;
      this.originalShowWhitespaceErrors = prefs?.show_whitespace_errors;
      this.originalMatchBrackets = prefs?.match_brackets;
      this.originalLineWrapping = prefs?.line_wrapping;
      this.originalIndentWithTabs = prefs?.indent_with_tabs;
      this.originalAutoCloseBrackets = prefs?.auto_close_brackets;
      this.editPrefs = prefs;
    });
  }

  private readonly handleEditTabWidthChanged = () => {
    this.editPrefs!.tab_size = Number(this.editTabWidth!.value);
    this.requestUpdate();
  };

  private readonly handleEditLineLengthChanged = () => {
    this.editPrefs!.line_length = Number(this.editColumns!.value);
    this.requestUpdate();
  };

  private readonly handleEditIndentUnitChanged = () => {
    this.editPrefs!.indent_unit = Number(this.editIndentUnit!.value);
    this.requestUpdate();
  };

  private readonly handleEditSyntaxHighlightingChanged = () => {
    this.editPrefs!.syntax_highlighting = this.editSyntaxHighlighting!.checked;
    this.requestUpdate();
  };

  // private but used in test
  readonly handleEditShowTabsChanged = () => {
    this.editPrefs!.show_tabs = this.editShowTabs!.checked;
    this.requestUpdate();
  };

  private readonly handleEditShowTrailingWhitespaceTap = () => {
    this.editPrefs!.show_whitespace_errors =
      this.editShowTrailingWhitespaceInput!.checked;
    this.requestUpdate();
  };

  private readonly handleMatchBracketsChanged = () => {
    this.editPrefs!.match_brackets = this.showMatchBrackets!.checked;
    this.requestUpdate();
  };

  private readonly handleEditLineWrappingChanged = () => {
    this.editPrefs!.line_wrapping = this.editShowLineWrapping!.checked;
    this.requestUpdate();
  };

  private readonly handleIndentWithTabsChanged = () => {
    this.editPrefs!.indent_with_tabs = this.showIndentWithTabs!.checked;
    this.requestUpdate();
  };

  private readonly handleAutoCloseBracketsChanged = () => {
    this.editPrefs!.auto_close_brackets = this.showAutoCloseBrackets!.checked;
    this.requestUpdate();
  };

  private readonly handleSaveEditPreferences = () => {
    this.save();
  };

  // private but used in test
  hasUnsavedChanges() {
    // We have to wrap boolean values in Boolean() to ensure undefined values
    // use false rather than undefined.
    return (
      this.originalTabSize !== this.editPrefs?.tab_size ||
      this.originalLineLength !== this.editPrefs?.line_length ||
      this.originalIndentUnit !== this.editPrefs?.indent_unit ||
      Boolean(this.originalSyntaxHighlighting) !==
        Boolean(this.editPrefs?.syntax_highlighting) ||
      Boolean(this.originalShowTabs) !== Boolean(this.editPrefs?.show_tabs) ||
      Boolean(this.originalShowWhitespaceErrors) !==
        Boolean(this.editPrefs?.show_whitespace_errors) ||
      Boolean(this.originalMatchBrackets) !==
        Boolean(this.editPrefs?.match_brackets) ||
      Boolean(this.originalLineWrapping) !==
        Boolean(this.editPrefs?.line_wrapping) ||
      Boolean(this.originalIndentWithTabs) !==
        Boolean(this.editPrefs?.indent_with_tabs) ||
      Boolean(this.originalAutoCloseBrackets) !==
        Boolean(this.editPrefs?.auto_close_brackets)
    );
  }

  save() {
    if (!this.editPrefs)
      return Promise.reject(new Error('Missing edit preferences'));
    return this.restApiService.saveEditPreferences(this.editPrefs).then(() => {
      // This is to make sure that hasUnsavedChanges is triggered within
      // the html template.
      this.loadData();
      this.requestUpdate();
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-preferences': GrEditPreferences;
  }
}
