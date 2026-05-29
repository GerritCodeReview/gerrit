/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-dialog/gr-dialog';
import {css, html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import '../gr-autogrow-textarea/gr-autogrow-textarea';
import {sharedStyles} from '../../../styles/shared-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {fireNoBubble} from '../../../utils/event-util';
import {GrAutogrowTextarea} from '../gr-autogrow-textarea/gr-autogrow-textarea';

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
  messageInput?: GrAutogrowTextarea;

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
        gr-autogrow-textarea {
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
        <gr-autogrow-textarea
          id="messageInput"
          class="message"
          autocomplete="on"
          placeholder="&lt;Insert reasoning here&gt;"
          .value=${this.message}
          @input=${(e: InputEvent) => {
            const value = (e.target as GrAutogrowTextarea).value ?? '';
            this.message = value;
          }}
        ></gr-autogrow-textarea>
      </div>
    </gr-dialog>`;
  }

  resetFocus() {
    assertIsDefined(this.messageInput, 'messageInput');
    this.messageInput.focus();
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubble(this, 'confirm', {});
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubble(this, 'cancel', {});
  }
}
