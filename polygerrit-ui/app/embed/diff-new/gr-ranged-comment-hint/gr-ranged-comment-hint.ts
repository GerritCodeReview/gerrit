/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-range-header/gr-range-header';
import {CommentRange} from '../../../types/common';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property} from 'lit/decorators.js';
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
          --gr-range-header-color: var(--ranged-comment-hint-text-color);
        }
        gr-range-header {
          flex-grow: 1;
        }
      `,
    ];
  }

  override render() {
    if (!this.range) return nothing;
    const text = `Long comment range ${this.range.start_line} - ${this.range.end_line}`;
    return html`
      <div class="rangeHighlight row">
        <gr-range-header icon="mode_comment" filled>${text}</gr-range-header>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-ranged-comment-hint': GrRangedCommentHint;
  }
}
