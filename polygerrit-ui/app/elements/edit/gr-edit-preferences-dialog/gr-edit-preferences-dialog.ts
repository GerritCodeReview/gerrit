/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../settings/gr-edit-preferences/gr-edit-preferences';
import {GrEditPreferences} from '../../settings/gr-edit-preferences/gr-edit-preferences';
import {assertIsDefined} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {ValueChangedEvent} from '../../../types/events';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {fire} from '../../../utils/event-util';
import {classMap} from 'lit/directives/class-map.js';

@customElement('gr-edit-preferences-dialog')
export class GrEditPreferencesDialog extends LitElement {
  @query('#editPreferences') private editPreferences?: GrEditPreferences;

  @query('#editPrefsModal') private editPrefsModal?: HTMLDialogElement;

  @state() editPrefsChanged?: boolean;

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        .editHeader,
        .editActions {
          padding: var(--spacing-l) var(--spacing-xl);
        }
        .editHeader,
        .editActions {
          background-color: var(--dialog-background-color);
        }
        .editHeader {
          border-bottom: 1px solid var(--border-color);
          font-weight: var(--font-weight-medium);
        }
        .editActions {
          border-top: 1px solid var(--border-color);
          display: flex;
          justify-content: flex-end;
        }
        .editPrefsModal gr-button {
          margin-left: var(--spacing-l);
        }
        div.edited:after {
          color: var(--deemphasized-text-color);
          content: ' *';
        }
        #editPreferences {
          display: flex;
          padding: var(--spacing-s) var(--spacing-xl);
        }
      `,
    ];
  }

  override render() {
    return html`
      <dialog id="editPrefsModal" tabindex="-1">
        <div role="dialog" aria-labelledby="editPreferencesTitle">
          <h3
            class=${classMap({
              'heading-3': true,
              editHeader: true,
              edited: this.editPrefsChanged ?? false,
            })}
            id="editPreferencesTitle"
          >
            Edit Preferences
          </h3>
          <gr-edit-preferences
            id="editPreferences"
            @has-unsaved-changes-changed=${this.handleHasUnsavedChangesChanged}
          ></gr-edit-preferences>
          <div class="editActions">
            <gr-button
              id="cancelButton"
              link=""
              @click=${this.handleCancelEdit}
            >
              Cancel
            </gr-button>
            <gr-button
              id="saveButton"
              link=""
              primary=""
              @click=${this.handleSaveEditPreferences}
              ?disabled=${!this.editPrefsChanged}
            >
              Save
            </gr-button>
          </div>
        </div>
      </dialog>
    `;
  }

  private handleCancelEdit(e: MouseEvent) {
    e.stopPropagation();
    assertIsDefined(this.editPrefsModal, 'editPrefsModal');
    this.editPrefsModal.close();
  }

  open() {
    assertIsDefined(this.editPrefsModal, 'editPrefsModal');
    this.editPrefsModal.showModal();
  }

  private async handleSaveEditPreferences() {
    assertIsDefined(this.editPreferences, 'editPreferences');
    assertIsDefined(this.editPrefsModal, 'editPrefsModal');
    await this.editPreferences.save();
    this.editPrefsModal.close();
    fire(this, 'has-edit-pref-change-saved', {});
  }

  private handleHasUnsavedChangesChanged(e: ValueChangedEvent<boolean>) {
    this.editPrefsChanged = e.detail.value;
  }
}

declare global {
  interface HTMLElementEventMap {
    'has-edit-pref-change-saved': CustomEvent<{}>;
  }
  interface HTMLElementTagNameMap {
    'gr-edit-preferences-dialog': GrEditPreferencesDialog;
  }
}
