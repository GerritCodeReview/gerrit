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
      gr-dropdown {
        padding: 0 var(--spacing-m);
        --gr-button: {
          color: var(--header-text-color);
        }
        --gr-dropdown-item: {
          color: var(--primary-text-color);
        }
      }
      gr-avatar {
        height: 2em;
        width: 2em;
        vertical-align: middle;
      }
    </style>
    <gr-dropdown link="" items="[[links]]" top-content="[[topContent]]" horizontal-align="right">
        <span hidden\$="[[_hasAvatars]]" hidden="">[[_accountName(account)]]</span>
        <gr-avatar account="[[account]]" hidden\$="[[!_hasAvatars]]" hidden="" image-size="56" aria-label="Account avatar"></gr-avatar>
    </gr-dropdown>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
