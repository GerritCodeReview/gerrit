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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-confirm-abandon-dialog_html.js';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';

/**
 * @extends PolymerElement
 */
class GrConfirmAbandonDialog extends KeyboardShortcutMixin(
    GestureEventListeners(
        LegacyElementMixin(
            PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-confirm-abandon-dialog'; }
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

  static get properties() {
    return {
      message: String,
    };
  }

  get keyBindings() {
    return {
      'ctrl+enter meta+enter': '_handleEnterKey',
    };
  }

  resetFocus() {
    this.$.messageInput.textarea.focus();
  }

  _handleEnterKey(e) {
    this._confirm();
  }

  _handleConfirmTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this._confirm();
  }

  _confirm() {
    this.dispatchEvent(new CustomEvent('confirm', {
      detail: {reason: this.message},
      composed: true, bubbles: true,
    }));
  }

  _handleCancelTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel', {
      composed: true, bubbles: true,
    }));
  }
}

customElements.define(GrConfirmAbandonDialog.is, GrConfirmAbandonDialog);
