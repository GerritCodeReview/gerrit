/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-preferences-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {GrButton} from '../../shared/gr-button/gr-button';

export interface GrDiffPreferencesDialog {
  $: {
    diffPreferences: GrDiffPreferences;
    diffPrefsOverlay: GrOverlay;
    saveButton: GrButton;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-preferences-dialog': GrDiffPreferencesDialog;
  }
}

type DiffPreferences = {};

@customElement('gr-diff-preferences-dialog')
export class GrDiffPreferencesDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  diffPrefs: DiffPreferences | undefined;

  /**
   * _editableDiffPrefs is a clone of diffPrefs.
   * All changes in the dialog are applied to this object
   * immediately, when a value in an editor is changed.
   * The "Save" button replaces the "diffPrefs" object with
   * the value of _editableDiffPrefs.
   */
  @property({type: Object})
  _editableDiffPrefs: DiffPreferences | undefined;

  @property({type: Boolean})
  _diffPrefsChanged = true;

  getFocusStops() {
    return {
      start: this.$.diffPreferences.$.contextSelect,
      end: this.$.saveButton,
    };
  }

  resetFocus() {
    this.$.diffPreferences.$.contextSelect.focus();
  }

  _computeHeaderClass(changed: boolean) {
    return changed ? 'edited' : '';
  }

  _handleCancelDiff(e: MouseEvent) {
    e.stopPropagation();
    this.$.diffPrefsOverlay.close();
  }

  open() {
    // JSON.parse(JSON.stringify(...)) makes a deep clone of diffPrefs.
    // It is known, that diffPrefs is obtained from an RestAPI call and
    // it is safe to clone the object this way.
    this._editableDiffPrefs = JSON.parse(JSON.stringify(this.diffPrefs));
    console.log(this.$.diffPrefsOverlay);
    this.$.diffPrefsOverlay.open().then(() => {
      const focusStops = this.getFocusStops();
      this.$.diffPrefsOverlay.setFocusStops(focusStops);
      this.resetFocus();
    });
  }

  _handleSaveDiffPreferences() {
    this.diffPrefs = this._editableDiffPrefs;
    this.$.diffPreferences.save().then(() => {
      this.dispatchEvent(
        new CustomEvent('reload-diff-preference', {
          composed: true,
          bubbles: false,
        })
      );

      this.$.diffPrefsOverlay.close();
    });
  }
}
