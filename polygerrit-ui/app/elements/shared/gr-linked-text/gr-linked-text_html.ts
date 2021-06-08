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
  <style>
    :host {
      display: block;
    }
    :host([pre]) span {
      white-space: var(--linked-text-white-space, pre-wrap);
      word-wrap: var(--linked-text-word-wrap, break-word);
    }
    :host([disabled]) a {
      color: inherit;
      text-decoration: none;
      pointer-events: none;
    }
    a {
      color: var(--link-color);
    }
  </style>
  <span id="output"></span>
`;
