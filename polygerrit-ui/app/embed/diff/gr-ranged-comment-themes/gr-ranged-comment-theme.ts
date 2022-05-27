/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css} from 'lit';

const $_documentContainer = document.createElement('template');

export const grRangedCommentTheme = css`
  .rangeHighlight {
    background-color: var(--diff-highlight-range-color);
  }
  .rangeHoverHighlight {
    background-color: var(--diff-highlight-range-hover-color);
  }
`;

$_documentContainer.innerHTML = `<dom-module id="gr-ranged-comment-theme">
  <template>
    <style>
    ${grRangedCommentTheme.cssText}
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
