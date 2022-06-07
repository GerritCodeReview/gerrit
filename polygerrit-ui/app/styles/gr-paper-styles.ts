/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const paperStyles = css`
  paper-toggle-button {
    --paper-toggle-button-checked-bar-color: var(--link-color);
    --paper-toggle-button-checked-button-color: var(--link-color);
  }
  paper-tabs {
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
    --paper-font-common-base: {
      font-family: var(--header-font-family);
      -webkit-font-smoothing: initial;
    }
    --paper-tab-content: {
      margin-bottom: var(--spacing-s);
    }
    --paper-tab-content-focused: {
      /* paper-tabs uses 700 here, which can look awkward */
      font-weight: var(--font-weight-h3);
      background: var(--gray-background-focus);
    }
    --paper-tab-content-unselected: {
      /* paper-tabs uses 0.8 here, but we want to control the color directly */
      opacity: 1;
      color: var(--deemphasized-text-color);
    }
  }
  paper-tab:focus {
    padding-left: 0px;
    padding-right: 0px;
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-paper-styles">
  <template>
    <style>
    ${paperStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
