/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const subpageStyles = css`
  .main {
    margin: var(--spacing-l);
  }
  .loading {
    display: none;
  }
  #loading.loading {
    display: block;
  }
  #loading:not(.loading) {
    display: none;
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `
  <dom-module id="gr-subpage-styles">
    <template>
      <style>
      ${subpageStyles.cssText}
      </style>
    </template>
  </dom-module>
`;
document.head.appendChild($_documentContainer.content);
