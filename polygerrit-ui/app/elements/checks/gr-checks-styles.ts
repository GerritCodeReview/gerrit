/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

const $_documentContainer = document.createElement('template');

export const checksStyles = css`
  gr-icon.error {
    color: var(--error-foreground);
  }
  gr-icon.warning {
    color: var(--warning-foreground);
  }
  gr-icon.info {
    color: var(--info-foreground);
  }
  gr-icon.check_circle {
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
