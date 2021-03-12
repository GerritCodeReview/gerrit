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
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-delete-comment-dialog_html';
import {property, customElement} from '@polymer/decorators';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea';

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-delete-comment-dialog': GrConfirmDeleteCommentDialog;
  }
}
export interface GrConfirmDeleteCommentDialog {
  $: {
    messageInput: IronAutogrowTextareaElement;
  };
}

@customElement('gr-confirm-delete-comment-dialog')
export class GrConfirmDeleteCommentDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  static get is() {
    return 'gr-confirm-delete-comment-dialog';
  }
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
  message?: string;

  resetFocus() {
    this.$.messageInput.textarea.focus();
  }

  _handleConfirmTap(e: MouseEvent) {
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

  _handleCancelTap(e: MouseEvent) {
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
