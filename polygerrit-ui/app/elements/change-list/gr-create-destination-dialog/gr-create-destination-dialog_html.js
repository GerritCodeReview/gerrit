import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
    </style>
    <gr-overlay id="createOverlay" with-backdrop="">
      <gr-dialog confirm-label="View commands" on-confirm="_pickerConfirm" on-cancel="_handleClose" disabled="[[!_repoAndBranchSelected]]">
        <div class="header" slot="header">
          Create change
        </div>
        <div class="main" slot="main">
          <gr-repo-branch-picker repo="{{_repo}}" branch="{{_branch}}"></gr-repo-branch-picker>
          <p>
            If you haven't done so, you will need to clone the repository.
          </p>
        </div>
      </gr-dialog>
    </gr-overlay>
`;
