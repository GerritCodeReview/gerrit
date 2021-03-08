/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-error-dialog_html';
import {customElement, property} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-error-dialog': GrErrorDialog;
  }
}

@customElement('gr-error-dialog')
export class GrErrorDialog extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the dismiss button is pressed.
   *
   * @event dismiss
   */

  @property({type: String})
  text?: string;

  @property({type: String})
  loginUrl = '/login';

  @property({type: Boolean})
  showSignInButton = false;

  _handleConfirm() {
    this.dispatchEvent(new CustomEvent('dismiss'));
  }
}
