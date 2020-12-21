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

import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement} from '@polymer/decorators';
import {htmlTemplate} from './gr-create-change-help_html';
import {fireEvent} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-change-help': GrCreateChangeHelp;
  }
}

@customElement('gr-create-change-help')
export class GrCreateChangeHelp extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the "Create change" button is tapped.
   */
  _handleCreateTap(e: Event) {
    e.preventDefault();
    fireEvent(this, 'create-tap');
  }
}
