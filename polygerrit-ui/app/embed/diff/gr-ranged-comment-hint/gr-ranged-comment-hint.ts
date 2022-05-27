/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
