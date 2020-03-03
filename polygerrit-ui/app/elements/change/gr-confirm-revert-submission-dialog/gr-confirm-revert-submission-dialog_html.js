import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <!-- TODO(taoalpha): move all shared styles to a style module. -->
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
    <gr-dialog confirm-label="Revert Submission" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">Revert Submission</div>
      <div class="main" slot="main">
        <label for="messageInput">
          Revert Commit Message
        </label>
        <iron-autogrow-textarea id="messageInput" class="message" autocomplete="on" max-rows="15" bind-value="{{message}}"></iron-autogrow-textarea>
      </div>
    </gr-dialog>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
`;
