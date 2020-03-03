import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .main {
        max-height: 40em;
        max-width: 60em;
        overflow-y: auto;
        white-space: pre-wrap;
      }
      @media screen and (max-width: 50em) {
        .main {
          max-height: none;
          max-width: 50em;
        }
      }
      .signInLink {
        text-decoration: none;
      }
    </style>
    <gr-dialog id="dialog" cancel-label="" on-confirm="_handleConfirm" confirm-label="Dismiss" confirm-on-enter="">
      <div class="header" slot="header">An error occurred</div>
      <div class="main" slot="main">[[text]]</div>
      <gr-button id="signIn" class\$="signInLink" hidden\$="[[!showSignInButton]]" link="" slot="footer">
        <a href\$="[[loginUrl]]" class="signInLink">Sign in</a>
      </gr-button>
    </gr-dialog>
`;
