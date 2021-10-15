/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import '../gr-select/gr-select';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-preferences_html';
import {customElement, property} from '@polymer/decorators';
import {DiffPreferencesInfo, IgnoreWhitespaceType} from '../../../types/diff';
import {GrSelect} from '../gr-select/gr-select';
import {appContext} from '../../../services/app-context';
import {diffPreferences$} from '../../../services/user/user-model';
import {takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';

export interface GrDiffPreferences {
  $: {
    contextLineSelect: HTMLInputElement;
    columnsInput: HTMLInputElement;
    tabSizeInput: HTMLInputElement;
    fontSizeInput: HTMLInputElement;
    lineWrappingInput: HTMLInputElement;
    showTabsInput: HTMLInputElement;
    showTrailingWhitespaceInput: HTMLInputElement;
    automaticReviewInput: HTMLInputElement;
    syntaxHighlightInput: HTMLInputElement;
    contextSelect: GrSelect;
    ignoreWhiteSpace: HTMLInputElement;
  };
  save(): Promise<void>;
}

@customElement('gr-diff-preferences')
export class GrDiffPreferences extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasUnsavedChanges = false;

  @property({type: Object})
  diffPrefs?: DiffPreferencesInfo;

  private readonly userService = appContext.userService;

  disconnected$ = new Subject();

  constructor() {
    super();
    diffPreferences$
      .pipe(takeUntil(this.disconnected$))
      .subscribe(diffPreferences => {
        this.diffPrefs = diffPreferences;
      });
  }

  _handleDiffPrefsChanged() {
    this.hasUnsavedChanges = true;
  }

  _handleDiffContextChanged() {
    this.set('diffPrefs.context', Number(this.$.contextLineSelect.value));
    this._handleDiffPrefsChanged();
  }

  _handleLineWrappingTap() {
    this.set('diffPrefs.line_wrapping', this.$.lineWrappingInput.checked);
    this._handleDiffPrefsChanged();
  }

  _handleDiffLineLengthChanged() {
    this.set('diffPrefs.line_length', Number(this.$.columnsInput.value));
    this._handleDiffPrefsChanged();
  }

  _handleDiffTabSizeChanged() {
    this.set('diffPrefs.tab_size', Number(this.$.tabSizeInput.value));
    this._handleDiffPrefsChanged();
  }

  _handleDiffFontSizeChanged() {
    this.set('diffPrefs.font_size', Number(this.$.fontSizeInput.value));
    this._handleDiffPrefsChanged();
  }

  _handleShowTabsTap() {
    this.set('diffPrefs.show_tabs', this.$.showTabsInput.checked);
    this._handleDiffPrefsChanged();
  }

  _handleShowTrailingWhitespaceTap() {
    this.set(
      'diffPrefs.show_whitespace_errors',
      this.$.showTrailingWhitespaceInput.checked
    );
    this._handleDiffPrefsChanged();
  }

  _handleSyntaxHighlightTap() {
    this.set(
      'diffPrefs.syntax_highlighting',
      this.$.syntaxHighlightInput.checked
    );
    this._handleDiffPrefsChanged();
  }

  _handleAutomaticReviewTap() {
    this.set('diffPrefs.manual_review', !this.$.automaticReviewInput.checked);
    this._handleDiffPrefsChanged();
  }

  _handleDiffIgnoreWhitespaceChanged() {
    this.set(
      'diffPrefs.ignore_whitespace',
      this.$.ignoreWhiteSpace.value as IgnoreWhitespaceType
    );
    this._handleDiffPrefsChanged();
  }

  save() {
    if (!this.diffPrefs)
      return Promise.reject(new Error('Missing diff preferences'));
    return this.userService.updateDiffPreference(this.diffPrefs).then(_ => {
      this.hasUnsavedChanges = false;
    });
  }

  /**
   * bind-value has type string so we have to convert
   * anything inputed to string.
   *
   * This is so typescript checker doesn't fail.
   */
  _convertToString(key?: number | IgnoreWhitespaceType) {
    return key !== undefined ? String(key) : '';
  }

  /**
   * input 'checked' does not allow undefined,
   * so we make sure the value is boolean
   * by returning false if undefined.
   *
   * This is so typescript checker doesn't fail.
   */
  _convertToBoolean(key?: boolean) {
    return key !== undefined ? key : false;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-preferences': GrDiffPreferences;
  }
}
