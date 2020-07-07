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
import '@polymer/iron-input/iron-input.js';
import '../../../styles/shared-styles.js';
import '../gr-button/gr-button.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-select/gr-select.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-diff-preferences_html.js';

// eslint-disable-next-line max-len
goog.declareModuleId('polygerrit.elements.shared.gr$2ddiff$2dpreferences.gr$2ddiff$2dpreferences');

class GrDiffPreferences extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-diff-preferences'; }

  static get properties() {
    return {
      hasUnsavedChanges: {
        type: Boolean,
        notify: true,
        value: false,
      },

      /** @type {?} */
      diffPrefs: Object,
    };
  }

  loadData() {
    return this.$.restAPI.getDiffPreferences().then(prefs => {
      this.diffPrefs = prefs;
    });
  }

  _handleDiffPrefsChanged() {
    this.hasUnsavedChanges = true;
  }

  _handleLineWrappingTap() {
    this.set('diffPrefs.line_wrapping', this.$.lineWrappingInput.checked);
    this._handleDiffPrefsChanged();
  }

  _handleShowTabsTap() {
    this.set('diffPrefs.show_tabs', this.$.showTabsInput.checked);
    this._handleDiffPrefsChanged();
  }

  _handleShowTrailingWhitespaceTap() {
    this.set('diffPrefs.show_whitespace_errors',
        this.$.showTrailingWhitespaceInput.checked);
    this._handleDiffPrefsChanged();
  }

  _handleSyntaxHighlightTap() {
    this.set('diffPrefs.syntax_highlighting',
        this.$.syntaxHighlightInput.checked);
    this._handleDiffPrefsChanged();
  }

  _handleAutomaticReviewTap() {
    this.set('diffPrefs.manual_review',
        !this.$.automaticReviewInput.checked);
    this._handleDiffPrefsChanged();
  }

  save() {
    return this.$.restAPI.saveDiffPreferences(this.diffPrefs).then(res => {
      this.hasUnsavedChanges = false;
    });
  }
}

customElements.define(GrDiffPreferences.is, GrDiffPreferences);

