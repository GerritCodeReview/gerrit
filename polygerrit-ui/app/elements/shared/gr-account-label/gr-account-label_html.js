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
        position: relative;
      }
      :host::after {
        content: var(--account-label-suffix);
      }
      :host(:not([blurred])) .overlay {
        display: none;
      }
      .overlay {
        position: absolute;
        pointer-events: none;
        height: var(--line-height-normal);
        right: 0;
        left: 0;
        background-color: rgba(255, 255, 255, 0.5);
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
      iron-icon.attention {
        vertical-align: top;
      }
      iron-icon.status {
        width: 14px;
        height: 14px;
        vertical-align: top;
        position: relative;
        top: 2px;
      }
    </style>
    <div class="overlay"></div>
    <span>
      <gr-hovercard-account attention="[[showAttention]]"
                            account="[[account]]"
                            voteable-text="[[voteableText]]">
      </gr-hovercard-account>
      <template is="dom-if" if="[[showAttention]]">
        <iron-icon class="attention" icon="gr-icons:attention"></iron-icon><!--
   --></template><!--
   --><template is="dom-if" if="[[!hideAvatar]]"><!--
     --><gr-avatar account="[[account]]" image-size="32"></gr-avatar>
      </template>
      <span class="text">
        <span class="name">
          [[_computeName(account, _serverConfig)]]</span>
        <template is="dom-if" if="[[!hideStatus]]">
          <template is="dom-if" if="[[account.status]]">
            <iron-icon class="status" icon="gr-icons:calendar"></iron-icon>
          </template>
        </template>
      </span>
    </span>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
