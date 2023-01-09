/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const menuPageStyles = css`
  :host {
    display: block;
  }
  .main {
    margin: var(--spacing-xxl) auto;
    max-width: 50em;
  }
  .mainHeader {
    margin-left: 14em;
    padding: var(--spacing-l) 0 var(--spacing-l) var(--spacing-xxl);
  }
  .main.table,
  .mainHeader {
    margin-top: 0;
    margin-right: 0;
    margin-left: 14em;
    max-width: none;
  }
  h2.edited:after {
    color: var(--deemphasized-text-color);
    content: ' *';
  }
  .loading {
    color: var(--deemphasized-text-color);
    padding: var(--spacing-l);
  }
  @media only screen and (max-width: 70em) {
    .main {
      margin: var(--spacing-xxl) 0 var(--spacing-xxl) 15em;
    }
    .main.table {
      margin-left: 14em;
    }
  }
  @media only screen and (max-width: 53em) {
    .loading {
      padding: 0 var(--spacing-l);
    }
    .main {
      margin: var(--spacing-xxl) var(--spacing-l);
    }
    .main.table {
      margin: 0;
    }
    .mainHeader {
      margin-left: 0;
      padding: var(--spacing-m) 0 var(--spacing-m) var(--spacing-l);
    }
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `
  <dom-module id="gr-menu-page-styles">
    <template>
      <style>
      ${menuPageStyles.cssText}
      </style>
    </template>
  </dom-module>
`;
document.head.appendChild($_documentContainer.content);
