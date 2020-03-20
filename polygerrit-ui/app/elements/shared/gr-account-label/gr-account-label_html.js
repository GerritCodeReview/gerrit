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
        display: inline;
      }
      :host::after {
        content: var(--account-label-suffix);
      }
      gr-avatar {
        height: var(--line-height-normal);
        width: var(--line-height-normal);
        vertical-align: top;
      }
      .text {
        @apply --gr-account-label-text-style;
      }
      .text:hover {
        @apply --gr-account-label-text-hover-style;
      }
      .email,
      .showEmail .name {
        display: none;
      }
      .showEmail .email {
        display: inline-block;
      }
    </style>
    <span>
      <template is="dom-if" if="[[!hideAvatar]]">
        <gr-avatar account="[[account]]" image-size="32"></gr-avatar>
      </template>
      <span class\$="text [[_computeShowEmailClass(account)]]">
        <span class="name">
          [[_computeName(account, _serverConfig)]]</span>
        <span class="email">
          [[_computeEmailStr(account)]]
        </span>
        <template is="dom-if" if="[[account.status]]">
          (<gr-limited-text disable-tooltip="true" limit="[[_computeStatusTextLength(account, _serverConfig)]]" text="[[account.status]]">
          </gr-limited-text>)
        </template>
      </span>
    </span>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
