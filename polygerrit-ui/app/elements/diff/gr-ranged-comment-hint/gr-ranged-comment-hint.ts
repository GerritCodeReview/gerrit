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
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {grRangedCommentTheme} from '../gr-ranged-comment-themes/gr-ranged-comment-theme';

@customElement('gr-ranged-comment-hint')
export class GrRangedCommentHint extends LitElement {
  @property({type: Object})
  range?: CommentRange;

  static override get styles() {
    return [
      grRangedCommentTheme,
      sharedStyles,
      css`
        .row {
          display: flex;
        }
        gr-range-header {
          flex-grow: 1;
        }
      `,
    ];
  }

  override render() {
    // To pass CSS mixins for @apply to Polymer components, they need to appear
    // in <style> inside the template.
    /* eslint-disable lit/prefer-static-styles */
    const customStyle = html`
      <style>
        .row {
          --gr-range-header-color: var(--ranged-comment-hint-text-color);
        }
      </style>
    `;
    return html`${customStyle}
      <div class="rangeHighlight row">
        <gr-range-header icon="gr-icons:comment"
          >${this._computeRangeLabel(this.range)}</gr-range-header
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
