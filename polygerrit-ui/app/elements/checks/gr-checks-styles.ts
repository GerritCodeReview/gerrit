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

const $_documentContainer = document.createElement('template');

export const checksStyles = css`
  iron-icon.error {
    color: var(--error-foreground);
  }
  iron-icon.warning {
    color: var(--warning-foreground);
  }
  iron-icon.info-outline {
    color: var(--info-foreground);
  }
  iron-icon.check-circle-outline {
    color: var(--success-foreground);
  }
`;

$_documentContainer.innerHTML = `<dom-module id="gr-checks-styles">
  <template>
    <style>
    ${checksStyles.cssText}
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
