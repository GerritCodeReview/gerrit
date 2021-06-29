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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-ranged-comment-theme">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    .row {
      display: flex;
      --gr-range-header-color: var(--ranged-comment-hint-text-color);
    }
    gr-range-header {
      flex-grow: 1;
    }
  </style>
  <div class="rangeHighlight row">
    <gr-range-header icon="gr-icons:comment"
      >[[_computeRangeLabel(range)]]</gr-range-header
    >
  </div>
`;
