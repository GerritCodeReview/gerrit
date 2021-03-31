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
    .status {
      display: inline-block;
      border-radius: var(--border-radius);
      margin-left: var(--spacing-s);
      padding: 0 var(--spacing-m);
      color: var(--primary-text-color);
      font-size: var(--font-size-small);
      background-color: var(--file-status-added);
    }
    .status.invisible,
    .status.M {
      display: none;
    }
    .status.D,
    .status.R,
    .status.W {
      background-color: var(--file-status-changed);
    }
    .status.U {
      background-color: var(--file-status-unchanged);
    }
  </style>
  <span
    class$="[[_computeStatusClass(file)]]"
    tabindex="0"
    title$="[[_computeFileStatusLabel(file.status)]]"
    aria-label$="[[_computeFileStatusLabel(file.status)]]"
  >
    [[_computeFileStatusLabel(file.status)]]
  </span>
`;
