/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const spinnerStyles = css`
  .loadingSpin {
    border: 2px solid var(--disabled-button-background-color);
    border-top: 2px solid var(--primary-button-background-color);
    border-radius: 50%;
    width: 10px;
    height: 10px;
    animation: spin 2s linear infinite;
    margin-right: var(--spacing-s);
  }
  @keyframes spin {
    0% {
      transform: rotate(0deg);
    }
    100% {
      transform: rotate(360deg);
    }
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-spinner-styles">
  <template>
    <style>
      ${spinnerStyles.cssText}
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
