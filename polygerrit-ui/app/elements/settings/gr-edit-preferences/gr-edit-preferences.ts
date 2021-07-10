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
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-select/gr-select';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-edit-preferences_html';
import {customElement, property} from '@polymer/decorators';
import {EditPreferencesInfo} from '../../../types/common';
import {appContext} from '../../../services/app-context';

export interface GrEditPreferences {
  $: {
    editTabWidth: HTMLInputElement;
    editColumns: HTMLInputElement;
    editIndentUnit: HTMLInputElement;
    editSyntaxHighlighting: HTMLInputElement;
    showAutoCloseBrackets: HTMLInputElement;
    showIndentWithTabs: HTMLInputElement;
    showMatchBrackets: HTMLInputElement;
    editShowLineWrapping: HTMLInputElement;
    editShowTabs: HTMLInputElement;
    editShowTrailingWhitespaceInput: HTMLInputElement;
  };
}
@customElement('gr-edit-preferences')
export class GrEditPreferences extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasUnsavedChanges? = false;

  @property({type: Object})
  editPrefs?: EditPreferencesInfo;

  private readonly restApiService = appContext.restApiService;

  loadData() {
    return this.restApiService.getEditPreferences().then(prefs => {
      this.editPrefs = prefs;
    });
  }

  _handleEditPrefsChanged() {
    this.hasUnsavedChanges = true;
  }

  _handleEditTabWidthChanged() {
    this.set('editPrefs.tab_size', Number(this.$.editTabWidth.value));
    this._handleEditPrefsChanged();
  }

  _handleEditLineLengthChanged() {
    this.set('editPrefs.line_length', Number(this.$.editColumns.value));
    this._handleEditPrefsChanged();
  }

  _handleEditIndentUnitChanged() {
    this.set('editPrefs.indent_unit', Number(this.$.editIndentUnit.value));
    this._handleEditPrefsChanged();
  }

  _handleEditSyntaxHighlightingChanged() {
    this.set(
      'editPrefs.syntax_highlighting',
      this.$.editSyntaxHighlighting.checked
    );
    this._handleEditPrefsChanged();
  }

  _handleEditShowTabsChanged() {
    this.set('editPrefs.show_tabs', this.$.editShowTabs.checked);
    this._handleEditPrefsChanged();
  }

  _handleEditShowTrailingWhitespaceTap() {
    this.set(
      'editPrefs.show_whitespace_errors',
      this.$.editShowTrailingWhitespaceInput.checked
    );
    this._handleEditPrefsChanged();
  }

  _handleMatchBracketsChanged() {
    this.set('editPrefs.match_brackets', this.$.showMatchBrackets.checked);
    this._handleEditPrefsChanged();
  }

  _handleEditLineWrappingChanged() {
    this.set('editPrefs.line_wrapping', this.$.editShowLineWrapping.checked);
    this._handleEditPrefsChanged();
  }

  _handleIndentWithTabsChanged() {
    this.set('editPrefs.indent_with_tabs', this.$.showIndentWithTabs.checked);
    this._handleEditPrefsChanged();
  }

  _handleAutoCloseBracketsChanged() {
    this.set(
      'editPrefs.auto_close_brackets',
      this.$.showAutoCloseBrackets.checked
    );
    this._handleEditPrefsChanged();
  }

  save() {
    if (!this.editPrefs)
      return Promise.reject(new Error('Missing edit preferences'));
    return this.restApiService.saveEditPreferences(this.editPrefs).then(() => {
      this.hasUnsavedChanges = false;
    });
  }

  /**
   * bind-value has type string so we have to convert
   * anything inputed to string.
   *
   * This is so typescript checker doesn't fail.
   */
  _convertToString(key?: number) {
    return key !== undefined ? String(key) : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-preferences': GrEditPreferences;
  }
}
