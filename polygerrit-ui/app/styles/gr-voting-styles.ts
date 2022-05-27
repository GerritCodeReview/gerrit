/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
