/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-delete-item-dialog': GrConfirmDeleteItemDialog;
  }
}

@customElement('gr-confirm-delete-item-dialog')
export class GrConfirmDeleteItemDialog extends LitElement {
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

  @property({type: String})
  item?: string;

  @property({type: String})
  itemTypeName?: string;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          width: 30em;
        }
      `,
    ];
  }

  override render() {
    const item = this.item ?? 'UNKNOWN ITEM';
    const itemTypeName = this.itemTypeName ?? 'UNKNOWN ITEM TYPE';
    return html` <gr-dialog
      confirm-label="Delete ${itemTypeName}"
      confirm-on-enter=""
      @confirm=${this._handleConfirmTap}
      @cancel=${this._handleCancelTap}
    >
      <div class="header" slot="header">${itemTypeName} Deletion</div>
      <div class="main" slot="main">
        <label for="branchInput">
          Do you really want to delete the following ${itemTypeName}?
        </label>
        <div>${item}</div>
      </div>
    </gr-dialog>`;
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _handleCancelTap(e: Event) {
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
