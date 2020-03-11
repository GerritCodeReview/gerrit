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
import '../../../scripts/bundled-polymer.js';

import '../../shared/gr-dialog/gr-dialog.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-error-dialog_html.js';

/** @extends Polymer.Element */
class GrErrorDialog extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-error-dialog'; }
  /**
   * Fired when the dismiss button is pressed.
   *
   * @event dismiss
   */

  static get properties() {
    return {
      text: String,
      /**
       * loginUrl to open on "sign in" button click
       */
      loginUrl: {
        type: String,
        value: '/login',
      },
      /**
       * Show/hide "Sign In" button in dialog
       */
      showSignInButton: {
        type: Boolean,
        value: false,
      },
    };
  }

  _handleConfirm() {
    this.dispatchEvent(new CustomEvent('dismiss'));
  }
}

customElements.define(GrErrorDialog.is, GrErrorDialog);
