/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import {css} from 'lit';

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
export {};

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
