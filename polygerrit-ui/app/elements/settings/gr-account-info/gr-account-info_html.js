import { html } from '@polymer/polymer/lib/utils/html-tag.js';

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
