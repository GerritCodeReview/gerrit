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
      gr-avatar {
        height: 120px;
        width: 120px;
        margin-right: var(--spacing-xs);
        vertical-align: -.25em;
      }
      div section.hide {
        display: none;
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="gr-form-styles">
      <section>
        <span class="title"></span>
        <span class="value">
          <gr-avatar account="[[_account]]" image-size="120"></gr-avatar>
        </span>
      </section>
      <section class\$="[[_hideAvatarChangeUrl(_avatarChangeUrl)]]">
        <span class="title"></span>
        <span class="value">
          <a href\$="[[_avatarChangeUrl]]">
            Change avatar
          </a>
        </span>
      </section>
      <section>
        <span class="title">ID</span>
        <span class="value">[[_account._account_id]]</span>
      </section>
      <section>
        <span class="title">Email</span>
        <span class="value">[[_account.email]]</span>
      </section>
      <section>
        <span class="title">Registered</span>
        <span class="value">
          <gr-date-formatter has-tooltip="" date-str="[[_account.registered_on]]"></gr-date-formatter>
        </span>
      </section>
      <section id="usernameSection">
        <span class="title">Username</span>
        <span hidden\$="[[usernameMutable]]" class="value">[[_username]]</span>
        <span hidden\$="[[!usernameMutable]]" class="value">
          <iron-input on-keydown="_handleKeydown" bind-value="{{_username}}">
            <input is="iron-input" id="usernameInput" disabled="[[_saving]]" on-keydown="_handleKeydown" bind-value="{{_username}}">
          </iron-input>
        </span>
      </section>
      <section id="nameSection">
        <span class="title">Full name</span>
        <span hidden\$="[[nameMutable]]" class="value">[[_account.name]]</span>
        <span hidden\$="[[!nameMutable]]" class="value">
          <iron-input on-keydown="_handleKeydown" bind-value="{{_account.name}}">
            <input is="iron-input" id="nameInput" disabled="[[_saving]]" on-keydown="_handleKeydown" bind-value="{{_account.name}}">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Display name (defaults to "Full name")</span>
        <span class="value">
          <iron-input on-keydown="_handleKeydown" bind-value="{{_account.display_name}}">
            <input is="iron-input" id="displayNameInput" disabled="[[_saving]]" on-keydown="_handleKeydown" bind-value="{{_account.display_name}}">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Status (e.g. "Vacation")</span>
        <span class="value">
          <iron-input on-keydown="_handleKeydown" bind-value="{{_account.status}}">
            <input is="iron-input" id="statusInput" disabled="[[_saving]]" on-keydown="_handleKeydown" bind-value="{{_account.status}}">
          </iron-input>
        </span>
      </section>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
