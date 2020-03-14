<!--
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-input/iron-input.html">
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../core/gr-navigation/gr-navigation.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-registration-dialog">
  <template>
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
      section.hide {
        display: none;
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
        <hr>
        <section>
          <div class="title">Full Name</div>
          <iron-input
              bind-value="{{_account.name}}">
            <input
                is="iron-input"
                id="name"
                bind-value="{{_account.name}}"
                disabled="[[_saving]]">
          </iron-input>
        </section>
        <section class$="[[_computeUsernameClass(_usernameMutable)]]">
          <div class="title">Username</div>
          <iron-input
              bind-value="{{_account.username}}">
            <input
                is="iron-input"
                id="username"
                bind-value="{{_account.username}}"
                disabled="[[_saving]]">
          </iron-input>
        </section>
        <section>
          <div class="title">Preferred Email</div>
          <select
              id="email"
              disabled="[[_saving]]">
            <option value="[[_account.email]]">[[_account.email]]</option>
            <template is="dom-repeat" items="[[_account.secondary_emails]]">
              <option value="[[item]]">[[item]]</option>
            </template>
          </select>
        </section>
        <hr>
        <p>
          More configuration options for Gerrit may be found in the
          <a on-click="close" href$="[[settingsUrl]]">settings</a>.
        </p>
      </main>
      <footer>
        <gr-button
            id="closeButton"
            link
            disabled="[[_saving]]"
            on-click="_handleClose">Close</gr-button>
        <gr-button
            id="saveButton"
            primary
            link
            disabled="[[_computeSaveDisabled(_account.name, _account.email, _saving)]]"
            on-click="_handleSave">Save</gr-button>
      </footer>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-registration-dialog.js"></script>
</dom-module>
