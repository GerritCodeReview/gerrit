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

/**
 * @fileoverview Consider removing this element as
 * its functionality seems to be duplicated with gr-tooltip and only
 * used in gr-label-info.
 */

import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement} from '@polymer/decorators';
import {htmlTemplate} from './gr-label_html';
import {TooltipMixin} from '../../../mixins/gr-tooltip-mixin/gr-tooltip-mixin';

declare global {
  interface HTMLElementTagNameMap {
    'gr-label': GrLabel;
  }
}

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = TooltipMixin(PolymerElement);

@customElement('gr-label')
export class GrLabel extends base {
  static get template() {
    return htmlTemplate;
  }
}
