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
