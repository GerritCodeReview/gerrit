/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-dialog/gr-dialog';
import {css, html, LitElement} from 'lit';
import {property, query, customElement} from 'lit/decorators.js';
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea';
import {sharedStyles} from '../../../styles/shared-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {BindValueChangeEvent} from '../../../types/events';

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-delete-comment-dialog': GrConfirmDeleteCommentDialog;
  }
}

@customElement('gr-confirm-delete-comment-dialog')
export class GrConfirmDeleteCommentDialog extends LitElement {
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

  @query('#messageInput')
  messageInput?: IronAutogrowTextareaElement;

  @property({type: String})
  message = '';

  static override get styles() {
    return [
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
        p {
          margin-bottom: var(--spacing-l);
        }
        label {
          cursor: pointer;
          display: block;
          width: 100%;
        }
        iron-autogrow-textarea {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          width: 73ch; /* Add a char to account for the border. */
        }
      `,
    ];
  }

  override render() {
    return html` <gr-dialog
      confirm-label="Delete"
      ?disabled=${this.message === ''}
      @confirm=${this.handleConfirmTap}
      @cancel=${this.handleCancelTap}
    >
      <div class="header" slot="header">Delete Comment</div>
      <div class="main" slot="main">
        <p>
          This is an admin function. Please only use in exceptional
          circumstances.
        </p>
        <label for="messageInput">Enter comment delete reason</label>
        <iron-autogrow-textarea
          id="messageInput"
          class="message"
          autocomplete="on"
          placeholder="&lt;Insert reasoning here&gt;"
          .bindValue=${this.message}
          @bind-value-changed=${(e: BindValueChangeEvent) => {
            this.message = e.detail.value ?? '';
          }}
        ></iron-autogrow-textarea>
      </div>
    </gr-dialog>`;
  }

  resetFocus() {
    assertIsDefined(this.messageInput, 'messageInput');
    this.messageInput.textarea.focus();
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('confirm', {
        detail: {reason: this.message},
        composed: true,
        bubbles: false,
      })
    );
  }

  private handleCancelTap(e: Event) {
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
