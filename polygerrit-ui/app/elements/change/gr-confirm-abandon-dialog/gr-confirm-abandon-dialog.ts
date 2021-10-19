/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../shared/gr-dialog/gr-dialog';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-abandon-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {addShortcut, Key, Modifier} from '../../../utils/dom-util';

export interface GrConfirmAbandonDialog {
  $: {
    messageInput: IronAutogrowTextareaElement;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-abandon-dialog': GrConfirmAbandonDialog;
  }
}

@customElement('gr-confirm-abandon-dialog')
export class GrConfirmAbandonDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
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
  message = '';

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  override disconnectedCallback() {
    super.disconnectedCallback();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
  }

  override connectedCallback() {
    super.connectedCallback();
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.CTRL_KEY]}, _ =>
        this._confirm()
      )
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.META_KEY]}, _ =>
        this._confirm()
      )
    );
  }

  resetFocus() {
    this.$.messageInput.textarea.focus();
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this._confirm();
  }

  _confirm() {
    this.dispatchEvent(
      new CustomEvent('confirm', {
        detail: {reason: this.message},
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
