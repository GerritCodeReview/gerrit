import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        background-color: var(--dialog-background-color);
        display: block;
      }
      .main {
        width: 100%;
      }
      ol {
        margin-left: var(--spacing-l);
        list-style: decimal;
      }
      p {
        margin-bottom: var(--spacing-m);
      }
    </style>
    <gr-dialog confirm-label="Done" cancel-label="" on-confirm="_handleCloseTap">
      <div class="header" slot="header">How to update this change:</div>
      <div class="main" slot="main">
        <ol>
          <li>
            <p>
              Checkout this change locally and make your desired modifications
              to the files.
            </p>
            <template is="dom-if" if="[[_fetchCommand]]">
              <gr-shell-command command="[[_fetchCommand]]"></gr-shell-command>
            </template>
          </li>
          <li>
            <p>
              Update the local commit with your modifications using the following
              command.
            </p>
            <gr-shell-command command="[[_commitCommand]]"></gr-shell-command>
            <p>
              Leave the "Change-Id:" line of the commit message as is.
            </p>
          </li>
          <li>
            <p>Push the updated commit to Gerrit.</p>
            <gr-shell-command command="[[_pushCommand]]"></gr-shell-command>
          </li>
          <li>
            <p>Refresh this page to view the the update.</p>
          </li>
        </ol>
      </div>
    </gr-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
