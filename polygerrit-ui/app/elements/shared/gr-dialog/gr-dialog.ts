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
import '../gr-button/gr-button';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-dialog_html';
import {customElement, property, observe} from '@polymer/decorators';
import {GrButton} from '../gr-button/gr-button';
import {KeydownEvent} from '../../../types/events';

declare global {
  interface HTMLElementTagNameMap {
    'gr-dialog': GrDialog;
  }
}

export interface GrDialog {
  $: {
    confirm: GrButton;
  };
}

@customElement('gr-dialog')
export class GrDialog extends PolymerElement {
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
  confirmLabel = 'Confirm';

  // Supplying an empty cancel label will hide the button completely.
  @property({type: String})
  cancelLabel = 'Cancel';

  @property({type: Boolean})
  disabled = false;

  @property({type: Boolean})
  confirmOnEnter = false;

  @property({type: String})
  confirmTooltip?: string;

  override ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  @observe('confirmTooltip')
  _handleConfirmTooltipUpdate(confirmTooltip?: string) {
    if (confirmTooltip) {
      this.$.confirm.setAttribute('has-tooltip', 'true');
    } else {
      this.$.confirm.removeAttribute('has-tooltip');
    }
  }

  _handleConfirm(e: Event) {
    if (this.disabled) {
      return;
    }

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

  _handleKeydown(e: KeydownEvent) {
    if (this.confirmOnEnter && e.keyCode === 13) {
      this._handleConfirm(e);
    }
  }

  resetFocus() {
    this.$.confirm.focus();
  }

  _computeCancelClass(cancelLabel: string) {
    return cancelLabel.length ? '' : 'hidden';
  }
}
