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
  <style include="gr-form-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    :host {
      display: block;
    }
    main {
      max-width: 46em;
    }
    :host(.loading) main {
      display: none;
    }
    .loadingMessage {
      display: none;
      font-style: italic;
    }
    :host(.loading) .loadingMessage {
      display: block;
    }
    hr {
      margin-top: var(--spacing-l);
      margin-bottom: var(--spacing-l);
    }
    header {
      border-bottom: 1px solid var(--border-color);
      font-weight: var(--font-weight-bold);
      margin-bottom: var(--spacing-l);
    }
    .container {
      padding: var(--spacing-m) var(--spacing-xl);
    }
    footer {
      display: flex;
      justify-content: flex-end;
    }
    footer gr-button {
      margin-left: var(--spacing-l);
    }
    input {
      width: 20em;
    }
  </style>
  <div class="container gr-form-styles">
    <header>Please confirm your contact information</header>
    <div class="loadingMessage">Loading...</div>
    <main>
      <p>
        The following contact information was automatically obtained when you
        signed in to the site. This information is used to display who you are
        to others, and to send updates to code reviews you have either started
        or subscribed to.
      </p>
      <hr />
      <section>
        <span class="title">Full Name</span>
        <span class="value">
          <iron-input bind-value="{{_account.name}}">
            <input id="name" disabled="[[_saving]]" />
          </iron-input>
        </span>
      </section>
      <template is="dom-if" if="[[_computeUsernameEditable(_serverConfig)]]">
        <section>
          <span class="title">Username</span>
          <span hidden$="[[_usernameMutable]]" class="value"
            >[[_username]]</span
          >
          <span hidden$="[[!_usernameMutable]]" class="value">
            <iron-input bind-value="{{_username}}">
              <input id="username" disabled="[[_saving]]" />
            </iron-input>
          </span>
        </section>
      </template>
      <hr />
      <p>
        More configuration options for Gerrit may be found in the
        <a on-click="close" href$="[[settingsUrl]]">settings</a>.
      </p>
    </main>
    <footer>
      <gr-button
        id="closeButton"
        link=""
        disabled="[[_saving]]"
        on-click="_handleClose"
        >Close</gr-button
      >
      <gr-button
        id="saveButton"
        primary=""
        link=""
        disabled="[[_computeSaveDisabled(_account.name, _username, _saving)]]"
        on-click="_handleSave"
        >Save</gr-button
      >
    </footer>
  </div>
`;
