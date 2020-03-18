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
import '../gr-hovercard/gr-hovercard-shared-style.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="gr-hovercard-shared-style">
      .top,
      .attention,
      .status,
      .voteable {
        padding: var(--spacing-s) var(--spacing-l);
      }
      .top {
        display: flex;
        padding-top: var(--spacing-xl);
        min-width: 300px;
      }
      gr-avatar {
        height: 48px;
        width: 48px;
        margin-right: var(--spacing-l);
      }
      .title,
      .email {
        color: var(--deemphasized-text-color);
      }
      .action {
        border-top: 1px solid var(--border-color);
        padding: var(--spacing-s) var(--spacing-l);
        --gr-button: {
          padding: var(--spacing-s) 0;
        };
      }
      :host(:not([attention])) .attention {
        display: none;
      }
      .attention {
        background-color: var(--emphasis-color);
      }
      .attention iron-icon {
        vertical-align: top;
      }
    </style>
    <div id="container" role="tooltip" tabindex="-1">
      <div class="top">
        <div class="avatar">
          <gr-avatar account="[[account]]" image-size="56"></gr-avatar>
        </div>
        <div class="account">
          <h3 class="name">[[account.name]]</h3>
          <div class="email">[[account.email]]</div>
        </div>
      </div>
      <template is="dom-if" if="[[account.status]]">
        <div class="status">
          <span class="title">Status:</span>
          <span class="value">[[account.status]]</span>
        </div>
      </template>
      <template is="dom-if" if="[[voteableText]]">
        <div class="voteable">
          <span class="title">Voteable:</span>
          <span class="value">[[voteableText]]</span>
        </div>
      </template>
      <div class="attention">
        <iron-icon icon="gr-icons:attention"></iron-icon>
        <span>It is this user's turn to take action.</span>
      </div>
    </div>
`;
