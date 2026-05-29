/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const grRangedCommentTheme = css`
  gr-diff-text hl.rangeHighlight {
    background-color: var(--diff-highlight-range-color);
  }
  gr-diff-text hl.rangeHoverHighlight {
    background-color: var(--diff-highlight-range-hover-color);
  }
`;
