import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      ol {
        list-style: decimal;
        margin-left: var(--spacing-l);
      }
      p {
        margin-bottom: var(--spacing-m);
      }
      #commandsDialog {
        max-width: 40em;
      }
    </style>
    <gr-overlay id="commandsOverlay" with-backdrop="">
      <gr-dialog id="commandsDialog" confirm-label="Done" cancel-label="" confirm-on-enter="" on-confirm="_handleClose">
        <div class="header" slot="header">
          Create change commands
        </div>
        <div class="main" slot="main">
          <ol>
            <li>
              <p>
                Make the changes to the files on your machine
              </p>
            </li>
            <li>
              <p>
                If you are making a new commit use
              </p>
              <gr-shell-command command="[[_createNewCommitCommand]]"></gr-shell-command>
              <p>
                Or to amend an existing commit use
              </p>
              <gr-shell-command command="[[_amendExistingCommitCommand]]"></gr-shell-command>
              <p>
                Please make sure you add a commit message as it becomes the
                description for your change.
              </p>
            </li>
            <li>
              <p>
                Push the change for code review
              </p>
              <gr-shell-command command="[[_pushCommand]]"></gr-shell-command>
            </li>
            <li>
              <p>
                Close this dialog and you should be able to see your recently
                created change in the 'Outgoing changes' section on the
                'Your changes' page.
              </p>
            </li>
          </ol>
        </div>
      </gr-dialog>
    </gr-overlay>
`;
