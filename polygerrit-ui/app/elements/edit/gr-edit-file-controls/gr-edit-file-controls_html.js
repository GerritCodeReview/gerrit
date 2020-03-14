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
        align-items: center;
        display: flex;
        justify-content: flex-end;
      }
      #actions {
        margin-right: var(--spacing-l);
      }
      gr-button,
      gr-dropdown {
        --gr-button: {
          height: 1.8em;
        }
      }
      gr-dropdown {
        --gr-dropdown-item: {
          background-color: transparent;
          border: none;
          color: var(--link-color);
          text-transform: uppercase;
        }
      }
    </style>
    <gr-dropdown id="actions" items="[[_fileActions]]" down-arrow="" vertical-offset="20" on-tap-item="_handleActionTap" link="">Actions</gr-dropdown>
`;
