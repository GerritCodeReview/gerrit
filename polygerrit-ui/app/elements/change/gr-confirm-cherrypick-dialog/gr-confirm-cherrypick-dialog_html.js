import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      :host([disabled]) {
        opacity: .5;
        pointer-events: none;
      }
      label {
        cursor: pointer;
      }
      .main {
        display: flex;
        flex-direction: column;
        width: 100%;
      }
      .main label,
      .main input[type="text"] {
        display: block;
        width: 100%;
      }
      iron-autogrow-textarea {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
        width: 73ch; /* Add a char to account for the border. */
      }
    </style>
    <gr-dialog confirm-label="Cherry Pick" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">Cherry Pick Change to Another Branch</div>
      <div class="main" slot="main">
        <label for="branchInput">
          Cherry Pick to branch
        </label>
        <gr-autocomplete id="branchInput" text="{{branch}}" query="[[_query]]" placeholder="Destination branch">
        </gr-autocomplete>
        <label for="baseInput">
          Provide base commit sha1 for cherry-pick
        </label>
        <iron-input maxlength="40" placeholder="(optional)" bind-value="{{baseCommit}}">
          <input is="iron-input" id="baseCommitInput" maxlength="40" placeholder="(optional)" bind-value="{{baseCommit}}">
        </iron-input>
        <label for="messageInput">
          Cherry Pick Commit Message
        </label>
        <iron-autogrow-textarea id="messageInput" class="message" autocomplete="on" rows="4" max-rows="15" bind-value="{{message}}"></iron-autogrow-textarea>
      </div>
    </gr-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
