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

import '../../shared/gr-button/gr-button';
import '../../shared/gr-diff-preferences/gr-diff-preferences';
import '../../shared/gr-overlay/gr-overlay';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {assertIsDefined, queryAndAssert} from '../../../utils/common-util';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css, PropertyValues} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {ValueChangedEvent} from '../../../types/events';

@customElement('gr-diff-preferences-dialog')
export class GrDiffPreferencesDialog extends LitElement {
  @query('#diffPreferences') private diffPreferences?: GrDiffPreferences;

  @query('#saveButton') private saveButton?: GrButton;

  @query('#cancelButton') private cancelButton?: GrButton;

  @query('#diffPrefsOverlay') private diffPrefsOverlay?: GrOverlay;

  @state() diffPrefsChanged?: boolean;

  static override get styles() {
    return [
      sharedStyles,
      css`
        .diffHeader,
        .diffActions {
          padding: var(--spacing-l) var(--spacing-xl);
        }
        .diffHeader,
        .diffActions {
          background-color: var(--dialog-background-color);
        }
        .diffHeader {
          border-bottom: 1px solid var(--border-color);
          font-weight: var(--font-weight-bold);
        }
        .diffActions {
          border-top: 1px solid var(--border-color);
          display: flex;
          justify-content: flex-end;
        }
        .diffPrefsOverlay gr-button {
          margin-left: var(--spacing-l);
        }
        div.edited:after {
          color: var(--deemphasized-text-color);
          content: ' *';
        }
        #diffPreferences {
          display: flex;
          padding: var(--spacing-s) var(--spacing-xl);
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-overlay id="diffPrefsOverlay" with-backdrop="">
        <div role="dialog" aria-labelledby="diffPreferencesTitle">
          <h3
            class="heading-3 diffHeader ${this.diffPrefsChanged
              ? 'edited'
              : ''}"
            id="diffPreferencesTitle"
          >
            Diff Preferences
          </h3>
          <gr-diff-preferences
            id="diffPreferences"
            @has-unsaved-changes-changed=${this.handleHasUnsavedChangesChanged}
          ></gr-diff-preferences>
          <div class="diffActions">
            <gr-button
              id="cancelButton"
              link=""
              @click=${this.handleCancelDiff}
            >
              Cancel
            </gr-button>
            <gr-button
              id="saveButton"
              link=""
              primary=""
              @click=${() => {
                this.handleSaveDiffPreferences();
              }}
              ?disabled=${!this.diffPrefsChanged}
            >
              Save
            </gr-button>
          </div>
        </div>
      </gr-overlay>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('diffPrefsChanged')) {
      this.onDiffPrefsChanged();
    }
  }

  getFocusStops() {
    assertIsDefined(this.diffPreferences, 'diffPrefsOverlay');
    assertIsDefined(this.saveButton, 'saveButton');
    assertIsDefined(this.cancelButton, 'cancelbutton');
    return {
      start: queryAndAssert<GrSelect>(this.diffPreferences, '#contextSelect'),
      end: this.saveButton.disabled ? this.cancelButton : this.saveButton,
    };
  }

  resetFocus() {
    assertIsDefined(this.diffPrefsOverlay, 'diffPrefsOverlay');
    queryAndAssert<GrSelect>(this.diffPreferences, '#contextSelect').focus();
  }

  private readonly handleCancelDiff = (e: MouseEvent) => {
    e.stopPropagation();
    assertIsDefined(this.diffPrefsOverlay, 'diffPrefsOverlay');
    this.diffPrefsOverlay.close();
  };

  private onDiffPrefsChanged() {
    assertIsDefined(this.diffPrefsOverlay, 'diffPrefsOverlay');
    this.diffPrefsOverlay.setFocusStops(this.getFocusStops());
  }

  open() {
    assertIsDefined(this.diffPrefsOverlay, 'diffPrefsOverlay');
    this.diffPrefsOverlay.open().then(() => {
      const focusStops = this.getFocusStops();
      this.diffPrefsOverlay!.setFocusStops(focusStops);
      this.resetFocus();
    });
  }

  private async handleSaveDiffPreferences() {
    assertIsDefined(this.diffPreferences, 'diffPreferences');
    assertIsDefined(this.diffPrefsOverlay, 'diffPrefsOverlay');
    await this.diffPreferences.save();
    this.dispatchEvent(
      new CustomEvent('reload-diff-preference', {
        composed: true,
        bubbles: false,
      })
    );
    this.diffPrefsOverlay.close();
  }

  private readonly handleHasUnsavedChangesChanged = (
    e: ValueChangedEvent<boolean>
  ) => {
    this.diffPrefsChanged = e.detail.value;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-preferences-dialog': GrDiffPreferencesDialog;
  }
}
