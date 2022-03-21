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
import '../../shared/gr-select/gr-select';
import {EditPreferencesInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, property, query} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {fireEvent} from '../../../utils/event-util';

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

  @property({type: Boolean})
  hasUnsavedChanges = false;

  @property({type: Object})
  editPrefs?: EditPreferencesInfo;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [sharedStyles, formStyles];
  }

  override render() {
    return html`
      <div id="editPreferences" class="gr-form-styles">
        <section>
          <label for="editTabWidth" class="title">Tab width</label>
          <span class="value">
            <iron-input
              .allowedPattern=${'[0-9]'}
              .bindValue=${`${this.editPrefs?.tab_size}`}
              @change=${this.handleEditTabWidthChanged}
              @bind-value-changed=${this.handleTabSizeBindValueChanged}
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
              .bindValue=${`${this.editPrefs?.line_length}`}
              @change=${this.handleEditLineLengthChanged}
              @bind-value-changed=${this.handleLineLengthBindValueChanged}
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
              .bindValue=${`${this.editPrefs?.indent_unit}`}
              @change=${this.handleEditIndentUnitChanged}
              @bind-value-changed=${this.handleIndentUnitBindValueChanged}
            >
              <input id="indentUnit" type="number" />
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
              change=${this.handleMatchBracketsChanged}
            />
          </span>
        </section>
        <section>
          <label for="editShowLineWrapping" class="title">Line wrapping</label>
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
          <label for="showIndentWithTabs" class="title">Indent with tabs</label>
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
    `;
  }

  loadData() {
    return this.restApiService.getEditPreferences().then(prefs => {
      this.editPrefs = prefs;
    });
  }

  private handleEditPrefsChanged(hasChnaged: boolean) {
    this.hasUnsavedChanges = hasChnaged;
    fireEvent(this, 'has-unsaved-changes-changed');
  }

  private readonly handleEditTabWidthChanged = () => {
    this.editPrefs!.tab_size = Number(this.editTabWidth!.value);
    this.handleEditPrefsChanged(true);
  };

  private readonly handleEditLineLengthChanged = () => {
    this.editPrefs!.line_length = Number(this.editColumns!.value);
    this.handleEditPrefsChanged(true);
  };

  private readonly handleEditIndentUnitChanged = () => {
    this.editPrefs!.indent_unit = Number(this.editIndentUnit!.value);
    this.handleEditPrefsChanged(true);
  };

  private readonly handleEditSyntaxHighlightingChanged = () => {
    this.editPrefs!.syntax_highlighting = Boolean(
      this.editSyntaxHighlighting!.checked
    );
    this.handleEditPrefsChanged(true);
  };

  // private but used in test
  readonly handleEditShowTabsChanged = () => {
    this.editPrefs!.show_tabs = Boolean(this.editShowTabs!.checked);
    this.handleEditPrefsChanged(true);
  };

  private readonly handleEditShowTrailingWhitespaceTap = () => {
    this.editPrefs!.show_whitespace_errors = Boolean(
      this.editShowTrailingWhitespaceInput!.checked
    );
    this.handleEditPrefsChanged(true);
  };

  private readonly handleMatchBracketsChanged = () => {
    this.editPrefs!.match_brackets = Boolean(this.showMatchBrackets!.checked);
    this.handleEditPrefsChanged(true);
  };

  private readonly handleEditLineWrappingChanged = () => {
    this.editPrefs!.line_wrapping = Boolean(this.editShowLineWrapping!.checked);
    this.handleEditPrefsChanged(true);
  };

  private readonly handleIndentWithTabsChanged = () => {
    this.editPrefs!.indent_with_tabs = Boolean(
      this.showIndentWithTabs!.checked
    );
    this.handleEditPrefsChanged(true);
  };

  private readonly handleAutoCloseBracketsChanged = () => {
    this.editPrefs!.auto_close_brackets = Boolean(
      this.showAutoCloseBrackets!.checked
    );
    this.handleEditPrefsChanged(true);
  };

  save() {
    if (!this.editPrefs)
      return Promise.reject(new Error('Missing edit preferences'));
    return this.restApiService.saveEditPreferences(this.editPrefs).then(() => {
      this.handleEditPrefsChanged(false);
    });
  }

  private readonly handleTabSizeBindValueChanged = (
    e: BindValueChangeEvent
  ) => {
    this.editPrefs!.tab_size = Number(e.detail.value);
  };

  private readonly handleLineLengthBindValueChanged = (
    e: BindValueChangeEvent
  ) => {
    this.editPrefs!.line_length = Number(e.detail.value);
  };

  private readonly handleIndentUnitBindValueChanged = (
    e: BindValueChangeEvent
  ) => {
    this.editPrefs!.indent_unit = Number(e.detail.value);
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-preferences': GrEditPreferences;
  }
}
