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

import {customElement, property} from '@polymer/decorators';
import {htmlTemplate} from './gr-range-header_html';
import {PolymerElement} from '@polymer/polymer/polymer-element';

/**
 * Represents a header (label) for a code chunk whenever showing
 * diffs.
 * Used as a labeled header to describe selections in code for cases
 * like long comments and moved in/out chunks.
 */
@customElement('gr-range-header')
export class GrRangeHeader extends PolymerElement {
  @property({type: String})
  icon?: string;

  static get template() {
    return htmlTemplate;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-range-header': GrRangeHeader;
  }
}
