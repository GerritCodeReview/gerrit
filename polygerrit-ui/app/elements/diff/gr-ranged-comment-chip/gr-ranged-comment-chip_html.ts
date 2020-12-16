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
    .row {
      background: var(--diff-highlight-range-color);
      color: var(--primary-button-text-color);
      display: flex;
      font-family: var(--font-family);
      justify-content: flex-end;
      margin: var(--spacing-s) 0;
    }
    .icon {
      color: var(--primary-button-text-color);
      --iron-icon-height: 15px;
      --iron-icon-width: 15px;
    }
    .chip {
      background-color: var(--highlight-chip-background-color);
      border-radius: 1em;
      margin: var(--spacing-s);
      padding: var(--spacing-s);
      padding-right: var(--spacing-xl);
    }
  </style>
  <div class="row">
    <div class="chip">
      <iron-icon class="icon" icon="gr-icons:comment-outline"></iron-icon>
      [[_computeRangeLabel(range)]]
    </div>
  </div>
`;
