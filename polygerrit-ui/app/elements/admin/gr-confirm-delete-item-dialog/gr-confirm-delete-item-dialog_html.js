import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        width: 30em;
      }
    </style>
    <gr-dialog confirm-label="Delete [[_computeItemName(itemType)]]" confirm-on-enter="" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">[[_computeItemName(itemType)]] Deletion</div>
      <div class="main" slot="main">
        <label for="branchInput">
          Do you really want to delete the following [[_computeItemName(itemType)]]?
        </label>
        <div>
          [[item]]
        </div>
      </div>
    </gr-dialog>
`;
