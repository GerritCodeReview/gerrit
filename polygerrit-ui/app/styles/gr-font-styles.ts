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
