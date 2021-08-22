/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {css} from 'lit';

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
export {};

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
