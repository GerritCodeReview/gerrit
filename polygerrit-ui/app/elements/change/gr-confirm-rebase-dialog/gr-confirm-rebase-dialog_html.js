import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        width: 30em;
      }
      :host([disabled]) {
        opacity: .5;
        pointer-events: none;
      }
      label {
        cursor: pointer;
      }
      .message {
        font-style: italic;
      }
      .parentRevisionContainer label,
      .parentRevisionContainer input[type="text"] {
        display: block;
        width: 100%;
      }
      .parentRevisionContainer label {
        margin-bottom: var(--spacing-xs);
      }
      .rebaseOption {
        margin: var(--spacing-m) 0;
      }
    </style>
    <gr-dialog id="confirmDialog" confirm-label="Rebase" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">Confirm rebase</div>
      <div class="main" slot="main">
        <div id="rebaseOnParent" class="rebaseOption" hidden\$="[[!_displayParentOption(rebaseOnCurrent, hasParent)]]">
          <input id="rebaseOnParentInput" name="rebaseOptions" type="radio" on-click="_handleRebaseOnParent">
          <label id="rebaseOnParentLabel" for="rebaseOnParentInput">
            Rebase on parent change
          </label>
        </div>
        <div id="parentUpToDateMsg" class="message" hidden\$="[[!_displayParentUpToDateMsg(rebaseOnCurrent, hasParent)]]">
          This change is up to date with its parent.
        </div>
        <div id="rebaseOnTip" class="rebaseOption" hidden\$="[[!_displayTipOption(rebaseOnCurrent, hasParent)]]">
          <input id="rebaseOnTipInput" name="rebaseOptions" type="radio" disabled\$="[[!_displayTipOption(rebaseOnCurrent, hasParent)]]" on-click="_handleRebaseOnTip">
          <label id="rebaseOnTipLabel" for="rebaseOnTipInput">
            Rebase on top of the [[branch]]
            branch<span hidden\$="[[!hasParent]]">
              (breaks relation chain)
            </span>
          </label>
        </div>
        <div id="tipUpToDateMsg" class="message" hidden\$="[[_displayTipOption(rebaseOnCurrent, hasParent)]]">
          Change is up to date with the target branch already ([[branch]])
        </div>
        <div id="rebaseOnOther" class="rebaseOption">
          <input id="rebaseOnOtherInput" name="rebaseOptions" type="radio" on-click="_handleRebaseOnOther">
          <label id="rebaseOnOtherLabel" for="rebaseOnOtherInput">
            Rebase on a specific change, ref, or commit <span hidden\$="[[!hasParent]]">
              (breaks relation chain)
            </span>
          </label>
        </div>
        <div class="parentRevisionContainer">
          <gr-autocomplete id="parentInput" query="[[_query]]" no-debounce="" text="{{_text}}" on-click="_handleEnterChangeNumberClick" allow-non-suggested-values="" placeholder="Change number, ref, or commit hash">
          </gr-autocomplete>
        </div>
      </div>
    </gr-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
