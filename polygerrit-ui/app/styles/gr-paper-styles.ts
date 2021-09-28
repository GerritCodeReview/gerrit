/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
