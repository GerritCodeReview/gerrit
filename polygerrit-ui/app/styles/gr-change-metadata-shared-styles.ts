/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
import {css} from 'lit';

export const changeMetadataStyles = css`
  section {
    display: table-row;
  }

  section:not(:first-of-type) .title,
  section:not(:first-of-type) .value {
    padding-top: var(--spacing-s);
  }

  .title,
  .value {
    display: table-cell;
    vertical-align: top;
  }

  .title {
    color: var(--deemphasized-text-color);
    max-width: 20em;
    padding-left: var(--metadata-horizontal-padding);
    padding-right: var(--metadata-horizontal-padding);
    word-break: break-word;
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-change-metadata-shared-styles">
  <template>
    <style>
    ${changeMetadataStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
