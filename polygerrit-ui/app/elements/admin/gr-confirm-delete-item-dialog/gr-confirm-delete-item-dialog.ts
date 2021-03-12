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
import '../../shared/gr-dialog/gr-dialog';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-delete-item-dialog_html';
import {customElement, property} from '@polymer/decorators';

// TODO(TS): add description for this
export enum DetailType {
  BRANCHES = 'branches',
  ID = 'id',
  TAGS = 'tags',
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-delete-item-dialog': GrConfirmDeleteItemDialog;
  }
}

@customElement('gr-confirm-delete-item-dialog')
export class GrConfirmDeleteItemDialog extends PolymerElement {
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
  item?: string;

  @property({type: String})
  itemType?: DetailType;

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

  _computeItemName(detailType: DetailType) {
    if (detailType === DetailType.BRANCHES) {
      return 'Branch';
    } else if (detailType === DetailType.TAGS) {
      return 'Tag';
    } else if (detailType === DetailType.ID) {
      return 'ID';
    }
    // TODO(TS): should never happen, this is to pass:
    // not all code returns value
    return '';
  }
}
