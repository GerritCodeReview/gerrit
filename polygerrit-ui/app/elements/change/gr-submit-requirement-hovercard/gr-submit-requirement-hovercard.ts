/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import '../../shared/gr-hovercard/gr-hovercard-shared-style';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement} from '@polymer/decorators';
import {hovercardBehaviorMixin} from '../../shared/gr-hovercard/gr-hovercard-behavior';
import {htmlTemplate} from './gr-submit-requirement-hovercard_html';

@customElement('gr-submit-requirement-hovercard')
export class GrHovercardRun extends hovercardBehaviorMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirement-hovercard': GrHovercardRun;
  }
}
