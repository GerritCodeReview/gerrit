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
import '../../shared/gr-diff-preferences/gr-diff-preferences';
import '../../shared/gr-overlay/gr-overlay';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-preferences-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {DiffPreferencesInfo} from '../../../types/common';

export interface GrDiffPreferencesDialog {
  $: {
    diffPreferences: GrDiffPreferences;
    saveButton: GrButton;
    cancelButton: GrButton;
    diffPrefsOverlay: GrOverlay;
  };
}
@customElement('gr-diff-preferences-dialog')
export class GrDiffPreferencesDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  diffPrefs?: DiffPreferencesInfo;

  @property({type: Object})
  _editableDiffPrefs?: DiffPreferencesInfo;

  @property({type: Boolean, observer: '_onDiffPrefsChanged'})
  _diffPrefsChanged?: boolean;

  returnFocusTo?: HTMLElement;

  getFocusStops() {
    return {
      start: this.$.diffPreferences.$.contextSelect,
      end: this.$.saveButton.disabled ? this.$.cancelButton : this.$.saveButton,
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
    this.$.diffPrefsOverlay.cancel();
  }

  onOverlayCanceled() {
    if (this.returnFocusTo) {
      this.returnFocusTo.focus();
    }
  }

  _onDiffPrefsChanged() {
    this.$.diffPrefsOverlay.setFocusStops(this.getFocusStops());
  }

  open(_returnFocusTo?: HTMLElement) {
    this.returnFocusTo = _returnFocusTo;
    // JSON.parse(JSON.stringify(...)) makes a deep clone of diffPrefs.
    // It is known, that diffPrefs is obtained from an RestAPI call and
    // it is safe to clone the object this way.
    this._editableDiffPrefs = JSON.parse(
      JSON.stringify(this.diffPrefs)
    ) as DiffPreferencesInfo;
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

      this.$.diffPrefsOverlay.cancel();
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-preferences-dialog': GrDiffPreferencesDialog;
  }
}
