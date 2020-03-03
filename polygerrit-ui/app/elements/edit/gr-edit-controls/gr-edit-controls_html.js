import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        align-items: center;
        display: flex;
        justify-content: flex-end;
      }
      .invisible {
        display: none;
      }
      gr-button {
        margin-left: var(--spacing-l);
        text-decoration: none;
      }
      gr-dialog {
        width: 50em;
      }
      gr-dialog .main {
        width: 100%;
      }
      gr-dialog .main > iron-input{
        width: 100%;
      }
      input {
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        margin: var(--spacing-m) 0;
        padding: var(--spacing-s);
        width: 100%;
        box-sizing: content-box;
      }
      @media screen and (max-width: 50em) {
        gr-dialog {
          width: 100vw;
        }
      }
    </style>
    <template is="dom-repeat" items="[[_actions]]" as="action">
      <gr-button id\$="[[action.id]]" class\$="[[_computeIsInvisible(action.id, hiddenActions)]]" link="" on-click="_handleTap">[[action.label]]</gr-button>
    </template>
    <gr-overlay id="overlay" with-backdrop="">
      <gr-dialog id="openDialog" class="invisible dialog" disabled\$="[[!_isValidPath(_path)]]" confirm-label="Confirm" confirm-on-enter="" on-confirm="_handleOpenConfirm" on-cancel="_handleDialogCancel">
        <div class="header" slot="header">
          Add a new file or open an existing file
        </div>
        <div class="main" slot="main">
          <gr-autocomplete placeholder="Enter an existing or new full file path." query="[[_query]]" text="{{_path}}"></gr-autocomplete>
        </div>
      </gr-dialog>
      <gr-dialog id="deleteDialog" class="invisible dialog" disabled\$="[[!_isValidPath(_path)]]" confirm-label="Delete" confirm-on-enter="" on-confirm="_handleDeleteConfirm" on-cancel="_handleDialogCancel">
        <div class="header" slot="header">Delete a file from the repo</div>
        <div class="main" slot="main">
          <gr-autocomplete placeholder="Enter an existing full file path." query="[[_query]]" text="{{_path}}"></gr-autocomplete>
        </div>
      </gr-dialog>
      <gr-dialog id="renameDialog" class="invisible dialog" disabled\$="[[!_computeRenameDisabled(_path, _newPath)]]" confirm-label="Rename" confirm-on-enter="" on-confirm="_handleRenameConfirm" on-cancel="_handleDialogCancel">
        <div class="header" slot="header">Rename a file in the repo</div>
        <div class="main" slot="main">
          <gr-autocomplete placeholder="Enter an existing full file path." query="[[_query]]" text="{{_path}}"></gr-autocomplete>
          <iron-input class="newPathIronInput" bind-value="{{_newPath}}" placeholder="Enter the new path.">
            <input class="newPathInput" is="iron-input" bind-value="{{_newPath}}" placeholder="Enter the new path.">
          </iron-input>
        </div>
      </gr-dialog>
      <gr-dialog id="restoreDialog" class="invisible dialog" confirm-label="Restore" confirm-on-enter="" on-confirm="_handleRestoreConfirm" on-cancel="_handleDialogCancel">
        <div class="header" slot="header">Restore this file?</div>
        <div class="main" slot="main">
          <iron-input disabled="" bind-value="{{_path}}">
            <input is="iron-input" disabled="" bind-value="{{_path}}">
          </iron-input>
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
