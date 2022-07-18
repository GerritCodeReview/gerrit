/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

const $_documentContainer = document.createElement('template');

export const checksStyles = css`
  .material-icon.error {
    color: var(--error-foreground);
  }
  .material-icon.warning {
    color: var(--warning-foreground);
  }
  .material-icon.info {
    color: var(--info-foreground);
  }
  .material-icon.check_circle {
    color: var(--success-foreground);
  }
`;

$_documentContainer.innerHTML = `<dom-module id="gr-checks-styles">
  <template>
    <style>
    ${checksStyles.cssText}
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
