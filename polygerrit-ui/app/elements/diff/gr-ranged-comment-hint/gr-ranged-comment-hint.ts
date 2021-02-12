/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {CommentRange} from '../../../types/common';
import {htmlTemplate} from './gr-ranged-comment-hint_html';
import {PolymerElement} from '@polymer/polymer/polymer-element';

@customElement('gr-ranged-comment-hint')
export class GrRangedCommentHint extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  range?: CommentRange;

  _computeRangeLabel(range?: CommentRange): string {
    if (!range) return '';
    return `Long comment range ${range.start_line} - ${range.end_line}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-ranged-comment-hint': GrRangedCommentHint;
  }
}
