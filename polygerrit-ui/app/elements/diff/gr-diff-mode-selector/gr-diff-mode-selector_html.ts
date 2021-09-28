/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    :host {
      /* Used to remove horizontal whitespace between the icons. */
      display: flex;
    }
    gr-button.selected iron-icon {
      color: var(--link-color);
    }
    iron-icon {
      height: 1.3rem;
      width: 1.3rem;
    }
  </style>
  <gr-tooltip-content
    has-tooltip=""
    title="Side-by-side diff"
    position-below="[[showTooltipBelow]]"
  >
  <gr-button
    id="sideBySideBtn"
    link=""
    class$="[[_computeSideBySideSelected(mode)]]"
    aria-pressed$="[[isSideBySideSelected(mode)]]"
    on-click="_handleSideBySideTap"
  >
    <iron-icon icon="gr-icons:side-by-side"></iron-icon>
  </gr-button>
  </gr-tooltip-content>
  <gr-tooltip-content
    has-tooltip=""
    position-below="[[showTooltipBelow]]"
    title="Unified diff"
  >
  <gr-button
    id="unifiedBtn"
    link=""

    class$="[[_computeUnifiedSelected(mode)]]"
    aria-pressed$="[[isUnifiedSelected(mode)]]"
    on-click="_handleUnifiedTap"
  >
    <iron-icon icon="gr-icons:unified"></iron-icon>
  </gr-button>
  </gr-tooltip-content>
`;
