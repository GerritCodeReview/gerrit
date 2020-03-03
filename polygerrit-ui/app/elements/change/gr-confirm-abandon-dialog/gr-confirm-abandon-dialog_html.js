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
      .main {
        display: flex;
        flex-direction: column;
        width: 100%;
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
    <gr-dialog confirm-label="Abandon" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">Abandon Change</div>
      <div class="main" slot="main">
        <label for="messageInput">Abandon Message</label>
        <iron-autogrow-textarea id="messageInput" class="message" autocomplete="on" placeholder="<Insert reasoning here>" bind-value="{{message}}"></iron-autogrow-textarea>
      </div>
    </gr-dialog>
`;
