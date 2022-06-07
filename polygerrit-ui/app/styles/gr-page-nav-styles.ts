/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

const $_documentContainer = document.createElement('template');

export const pageNavStyles = css`
  .navStyles ul {
    padding: var(--spacing-l) 0;
  }
  .navStyles li {
    border-bottom: 1px solid transparent;
    border-top: 1px solid transparent;
    display: block;
    padding: 0 var(--spacing-xl);
  }
  .navStyles li a {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .navStyles .subsectionItem {
    padding-left: var(--spacing-xxl);
  }
  .navStyles .hideSubsection {
    display: none;
  }
  .navStyles li.sectionTitle {
    padding: 0 var(--spacing-xxl) 0 var(--spacing-l);
  }
  .navStyles li.sectionTitle:not(:first-child) {
    margin-top: var(--spacing-l);
  }
  .navStyles .title {
    font-weight: var(--font-weight-bold);
    margin: var(--spacing-s) 0;
  }
  .navStyles .selected {
    background-color: var(--view-background-color);
    border-bottom: 1px solid var(--border-color);
    border-top: 1px solid var(--border-color);
    font-weight: var(--font-weight-bold);
  }
  .navStyles a {
    color: var(--primary-text-color);
    display: inline-block;
    margin: var(--spacing-s) 0;
  }
`;

$_documentContainer.innerHTML = `<dom-module id="gr-page-nav-styles">
  <template>
    <style>
    ${pageNavStyles.cssText}
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
