/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {Action} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {modalStyles} from '../../styles/gr-modal-styles';
import '../shared/gr-dialog/gr-dialog';

/**
 * Asks the user to confirm a checks action, with the action's `confirmation`
 * as the question. The checks model adds it to the document when needed, see
 * ChecksModel.confirm().
 */
@customElement('gr-checks-action-confirm')
export class GrChecksActionConfirm extends LitElement {
  @property({type: Object})
  action?: Action;

  @query('dialog')
  dialog?: HTMLDialogElement;

  private resolve?: (confirmed: boolean) => void;

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        .main {
          max-width: 50em;
          white-space: pre-wrap;
        }
      `,
    ];
  }

  override render() {
    const name = this.action?.name ?? '';
    // Escape closes the dialog without a cancel event from gr-dialog.
    return html`
      <dialog tabindex="-1" @close=${() => this.answer(false)}>
        <gr-dialog
          confirm-label=${name}
          confirm-on-enter
          @confirm=${() => this.answer(true)}
          @cancel=${() => this.answer(false)}
        >
          <div class="header" slot="header">${name}</div>
          <div class="main" slot="main">${this.action?.confirmation}</div>
        </gr-dialog>
      </dialog>
    `;
  }

  /** Opens the dialog. Resolves to true if the user confirmed. */
  async ask() {
    await this.updateComplete;
    return new Promise<boolean>(resolve => {
      this.resolve = resolve;
      this.dialog?.showModal();
    });
  }

  private answer(confirmed: boolean) {
    const resolve = this.resolve;
    // Closing the dialog fires a close event, which calls answer() again.
    this.resolve = undefined;
    if (this.dialog?.open) this.dialog.close();
    resolve?.(confirmed);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-action-confirm': GrChecksActionConfirm;
  }
}
