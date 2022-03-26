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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-preferences-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {queryAndAssert} from '../../../utils/common-util';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {ValueChangedEvent} from '../../../types/events';

export interface GrDiffPreferencesDialog {
  $: {
    diffPreferences: GrDiffPreferences;
    saveButton: GrButton;
    cancelButton: GrButton;
    diffPrefsOverlay: GrOverlay;
  };
}
@customElement('gr-diff-preferences-dialog')
export class GrDiffPreferencesDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, observer: '_onDiffPrefsChanged'})
  _diffPrefsChanged?: boolean;

  getFocusStops() {
    return {
      start: queryAndAssert<GrSelect>(this.$.diffPreferences, '#contextSelect'),
      end: this.$.saveButton.disabled ? this.$.cancelButton : this.$.saveButton,
    };
  }

  resetFocus() {
    queryAndAssert<GrSelect>(this.$.diffPreferences, '#contextSelect').focus();
  }

  _computeHeaderClass(changed: boolean) {
    return changed ? 'edited' : '';
  }

  _handleCancelDiff(e: MouseEvent) {
    e.stopPropagation();
    this.$.diffPrefsOverlay.close();
  }

  _onDiffPrefsChanged() {
    this.$.diffPrefsOverlay.setFocusStops(this.getFocusStops());
  }

  open() {
    this.$.diffPrefsOverlay.open().then(() => {
      const focusStops = this.getFocusStops();
      this.$.diffPrefsOverlay.setFocusStops(focusStops);
      this.resetFocus();
    });
  }

  async _handleSaveDiffPreferences() {
    await this.$.diffPreferences.save();
    this.dispatchEvent(
      new CustomEvent('reload-diff-preference', {
        composed: true,
        bubbles: false,
      })
    );
    this.$.diffPrefsOverlay.close();
  }

  _handleHasUnsavedChangesChanged(e: ValueChangedEvent<boolean>) {
    this._diffPrefsChanged = !!e.detail.value;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-preferences-dialog': GrDiffPreferencesDialog;
  }
}
