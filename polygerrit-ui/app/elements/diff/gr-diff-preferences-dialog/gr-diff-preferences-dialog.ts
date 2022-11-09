/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-diff-preferences/gr-diff-preferences';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {assertIsDefined} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {ValueChangedEvent} from '../../../types/events';
import {modalStyles} from '../../../styles/gr-modal-styles';

@customElement('gr-diff-preferences-dialog')
export class GrDiffPreferencesDialog extends LitElement {
  @query('#diffPreferences') private diffPreferences?: GrDiffPreferences;

  @query('#diffPrefsModal') private diffPrefsModal?: HTMLDialogElement;

  @state() diffPrefsChanged?: boolean;

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
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
        .diffPrefsModal gr-button {
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
      <dialog id="diffPrefsModal" tabindex="-1">
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
      </dialog>
    `;
  }

  resetFocus() {
    assertIsDefined(this.diffPreferences, 'diffPreferences');

    this.diffPreferences.contextSelect!.focus();
  }

  private readonly handleCancelDiff = (e: MouseEvent) => {
    e.stopPropagation();
    assertIsDefined(this.diffPrefsModal, 'diffPrefsModal');
    this.diffPrefsModal.close();
  };

  open() {
    assertIsDefined(this.diffPrefsModal, 'diffPrefsModal');
    this.diffPrefsModal.showModal();
  }

  private async handleSaveDiffPreferences() {
    assertIsDefined(this.diffPreferences, 'diffPreferences');
    assertIsDefined(this.diffPrefsModal, 'diffPrefsModal');
    await this.diffPreferences.save();
    this.dispatchEvent(
      new CustomEvent('reload-diff-preference', {
        composed: true,
        bubbles: false,
      })
    );
    this.diffPrefsModal.close();
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
