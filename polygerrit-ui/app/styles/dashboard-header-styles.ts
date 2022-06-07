/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

const $_documentContainer = document.createElement('template');

export const dashboardHeaderStyles = css`
  :host {
    background-color: var(--view-background-color);
    display: block;
    min-height: 9em;
    width: 100%;
  }
  gr-avatar {
    display: inline-block;
    height: 7em;
    left: 1em;
    margin: 1em;
    top: 1em;
    width: 7em;
  }
  .info {
    display: inline-block;
    padding: var(--spacing-l);
    vertical-align: top;
  }
  .info > div > span {
    display: inline-block;
    font-weight: var(--font-weight-bold);
    text-align: right;
    width: 4em;
  }
`;

$_documentContainer.innerHTML = `<dom-module id="dashboard-header-styles">
  <template>
    <style>
    ${dashboardHeaderStyles.cssText}
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
