/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

const $_documentContainer = document.createElement('template');

export const checksStyles = css`
  iron-icon.error {
    color: var(--error-foreground);
  }
  iron-icon.warning {
    color: var(--warning-foreground);
  }
  iron-icon.info-outline {
    color: var(--info-foreground);
  }
  iron-icon.check-circle-outline {
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
