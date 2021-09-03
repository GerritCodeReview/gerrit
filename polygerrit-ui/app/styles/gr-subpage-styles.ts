/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
