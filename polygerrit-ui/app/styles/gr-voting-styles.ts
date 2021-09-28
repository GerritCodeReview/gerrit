/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
import {css} from 'lit';

export const votingStyles = css`
  .voteChip {
    border: 1px solid var(--border-color);
    /* max rounded */
    border-radius: 1em;
    box-shadow: none;
    box-sizing: border-box;
    min-width: 3em;
    color: var(--vote-text-color);
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-voting-styles">
  <template>
    <style>
    ${votingStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
