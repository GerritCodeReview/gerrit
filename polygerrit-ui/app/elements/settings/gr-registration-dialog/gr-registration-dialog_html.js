import { html } from '@polymer/polymer/lib/utils/html-tag.js';

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
          <iron-input bind-value="{{_account.name}}">
            <input is="iron-input" id="name" bind-value="{{_account.name}}" disabled="[[_saving]]">
          </iron-input>
        </section>
        <section class\$="[[_computeUsernameClass(_usernameMutable)]]">
          <div class="title">Username</div>
          <iron-input bind-value="{{_account.username}}">
            <input is="iron-input" id="username" bind-value="{{_account.username}}" disabled="[[_saving]]">
          </iron-input>
        </section>
        <section>
          <div class="title">Preferred Email</div>
          <select id="email" disabled="[[_saving]]">
            <option value="[[_account.email]]">[[_account.email]]</option>
            <template is="dom-repeat" items="[[_account.secondary_emails]]">
              <option value="[[item]]">[[item]]</option>
            </template>
          </select>
        </section>
        <hr>
        <p>
          More configuration options for Gerrit may be found in the
          <a on-click="close" href\$="[[settingsUrl]]">settings</a>.
        </p>
      </main>
      <footer>
        <gr-button id="closeButton" link="" disabled="[[_saving]]" on-click="_handleClose">Close</gr-button>
        <gr-button id="saveButton" primary="" link="" disabled="[[_computeSaveDisabled(_account.name, _account.email, _saving)]]" on-click="_handleSave">Save</gr-button>
      </footer>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
