/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const fontStyles = css`
  .font-normal {
    font-size: var(--font-size-normal);
    font-weight: var(--font-weight-normal);
    line-height: var(--line-height-normal);
  }
  .font-small {
    font-size: var(--font-size-small);
    font-weight: var(--font-weight-normal);
    line-height: var(--line-height-small);
  }
  .heading-1 {
    font-family: var(--header-font-family);
    font-size: var(--font-size-h1);
    font-weight: var(--font-weight-h1);
    line-height: var(--line-height-h1);
  }
  .heading-2 {
    font-family: var(--header-font-family);
    font-size: var(--font-size-h2);
    font-weight: var(--font-weight-h2);
    line-height: var(--line-height-h2);
  }
  .heading-3 {
    font-family: var(--header-font-family);
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
  }
  .heading-4 {
    font-family: var(--header-font-family);
    font-size: var(--font-size-normal);
    font-weight: var(--font-weight-h4);
    line-height: var(--line-height-normal);
  }
  .heading-5 {
    font-family: var(--header-font-family);
    font-size: var(--font-size-normal);
    font-weight: var(--font-weight-h4);
    line-height: var(--line-height-normal);
  }
  strong {
    font-weight: var(--font-weight-bold);
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-font-styles">
  <template>
    <style>
    ${fontStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
