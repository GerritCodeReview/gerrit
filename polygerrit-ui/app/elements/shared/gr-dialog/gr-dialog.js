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
import '../gr-button/gr-button.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-dialog_html.js';

/**
 * @extends PolymerElement
 */
class GrDialog extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-dialog'; }
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
      confirmLabel: {
        type: String,
        value: 'Confirm',
      },
      // Supplying an empty cancel label will hide the button completely.
      cancelLabel: {
        type: String,
        value: 'Cancel',
      },
      disabled: {
        type: Boolean,
        value: false,
      },
      confirmOnEnter: {
        type: Boolean,
        value: false,
      },
      confirmTooltip: {
        type: String,
        observer: '_handleConfirmTooltipUpdate',
      },
    };
  }

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  _handleConfirmTooltipUpdate(confirmTooltip) {
    if (confirmTooltip) {
      this.$.confirm.setAttribute('has-tooltip', true);
    } else {
      this.$.confirm.removeAttribute('has-tooltip');
    }
  }

  _handleConfirm(e) {
    if (this.disabled) { return; }

    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {
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

  _handleKeydown(e) {
    if (this.confirmOnEnter && e.keyCode === 13) { this._handleConfirm(e); }
  }

  resetFocus() {
    this.$.confirm.focus();
  }

  _computeCancelClass(cancelLabel) {
    return cancelLabel.length ? '' : 'hidden';
  }
}

customElements.define(GrDialog.is, GrDialog);
