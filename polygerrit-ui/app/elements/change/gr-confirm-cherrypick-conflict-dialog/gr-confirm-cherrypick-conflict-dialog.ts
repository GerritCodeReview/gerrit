/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';

@customElement('gr-confirm-cherrypick-conflict-dialog')
export class GrConfirmCherrypickConflictDialog extends LitElement {
  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  static override styles = [
    sharedStyles,
    css`
      :host {
        display: block;
      }
      :host([disabled]) {
        opacity: 0.5;
        pointer-events: none;
      }
      .main {
        display: flex;
        flex-direction: column;
        width: 100%;
      }
    `,
  ];

  override render() {
    return html`
      <gr-dialog
        confirm-label="Continue"
        @confirm=${this.handleConfirmTap}
        @cancel=${this.handleCancelTap}
      >
        <div class="header" slot="header">Cherry Pick Conflict!</div>
        <div class="main" slot="main">
          <span>Cherry Pick failed! (merge conflicts)</span>
          <span
            >Please select "Continue" to continue with conflicts or select
            "cancel" to close the dialog.</span
          >
        </div>
      </gr-dialog>
    `;
  }

  handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: false,
      })
    );
  }

  handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-cherrypick-conflict-dialog': GrConfirmCherrypickConflictDialog;
  }
}
