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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-hovercard_html';
import {HovercardBehaviorMixin} from './gr-hovercard-behavior';
import './gr-hovercard-shared-style';
import {customElement} from '@polymer/decorators';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardBehaviorMixin(PolymerElement);

@customElement('gr-hovercard')
export class GrHovercard extends base {
  static get template() {
    return htmlTemplate;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-hovercard': GrHovercard;
  }
}
