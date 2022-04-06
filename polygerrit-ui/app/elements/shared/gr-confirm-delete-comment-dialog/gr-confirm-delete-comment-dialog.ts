/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '../gr-dialog/gr-dialog';
import {css, html, LitElement} from 'lit';
import {property, query, customElement} from 'lit/decorators';
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
          placeholder="<Insert reasoning here>"
          .bindValue=${this.message}
          @bind-value-changed=${this.handleBindValueChanged}
        ></iron-autogrow-textarea>
      </div>
    </gr-dialog>`;
  }

  resetFocus() {
    assertIsDefined(this.messageInput, 'messageInput');
    this.messageInput?.textarea.focus();
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

  private handleBindValueChanged(e: BindValueChangeEvent) {
    this.message = e.detail.value;
  }
}
