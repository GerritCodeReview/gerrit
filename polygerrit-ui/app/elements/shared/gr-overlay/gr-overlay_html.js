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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        background: var(--dialog-background-color);
        border-radius: var(--border-radius);
        box-shadow: var(--elevation-level-5);
      }

      @media screen and (max-width: 50em) {
        :host {
          height: 100%;
          left: 0;
          position: fixed;
          right: 0;
          top: 0;
          border-radius: 0;
          box-shadow: none;
        }
      }
    </style>
    <slot></slot>
`;
