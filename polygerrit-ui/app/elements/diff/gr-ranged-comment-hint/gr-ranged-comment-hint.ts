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

import '../gr-range-header/gr-range-header';
import {CommentRange} from '../../../types/common';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';
import {sharedStyles} from '../../../styles/shared-styles';
import {grRangedCommentTheme} from '../gr-ranged-comment-themes/gr-ranged-comment-theme';

@customElement('gr-ranged-comment-hint')
export class GrRangedCommentHint extends GrLitElement {
  @property({type: Object})
  range?: CommentRange;

  static get styles() {
    return [
      grRangedCommentTheme,
      sharedStyles,
      css`
        .row {
          display: flex;
          --gr-range-header-color: var(--ranged-comment-hint-text-color);
        }
        gr-range-header {
          flex-grow: 1;
        }
      `,
    ];
  }

  render() {
    const range = this.range;
    return html` <div class="rangeHighlight row">
      <gr-range-header icon="gr-icons:comment"
        >${this._computeRangeLabel(range)}</gr-range-header
      >
    </div>`;
  }

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
