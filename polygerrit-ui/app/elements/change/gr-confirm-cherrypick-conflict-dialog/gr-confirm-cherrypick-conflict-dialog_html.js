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
    </style>
    <gr-dialog confirm-label="Continue" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">Cherry Pick Conflict!</div>
      <div class="main" slot="main">
        <span>Cherry Pick failed! (merge conflicts)</span>

        <span>Please select "Continue" to continue with conflicts or select "cancel" to close the dialog.</span>
      </div>
    </gr-dialog>
`;
