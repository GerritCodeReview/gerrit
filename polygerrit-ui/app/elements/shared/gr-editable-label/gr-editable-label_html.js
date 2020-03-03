import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        align-items: center;
        display: inline-flex;
      }
      :host([uppercase]) label {
        text-transform: uppercase;
      }
      input,
      label {
        width: 100%;
      }
      label {
        color: var(--deemphasized-text-color);
        display: inline-block;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        @apply --label-style;
      }
      label.editable {
        color: var(--link-color);
        cursor: pointer;
      }
      #dropdown {
        box-shadow: var(--elevation-level-2);
      }
      .inputContainer {
        background-color: var(--dialog-background-color);
        padding: var(--spacing-m);
        @apply --input-style;
      }
      .buttons {
        display: flex;
        justify-content: flex-end;
        padding-top: var(--spacing-l);
        width: 100%;
      }
      .buttons gr-button {
        margin-left: var(--spacing-m);
      }
      paper-input {
        --paper-input-container: {
          padding: 0;
          min-width: 15em;
        }
        --paper-input-container-input: {
          font-size: inherit;
        }
        --paper-input-container-focus-color: var(--link-color);
      }
    </style>
      <label class\$="[[_computeLabelClass(readOnly, value, placeholder)]]" title\$="[[_computeLabel(value, placeholder)]]" on-click="_showDropdown">[[_computeLabel(value, placeholder)]]</label>
      <iron-dropdown id="dropdown" vertical-align="auto" horizontal-align="auto" vertical-offset="[[_verticalOffset]]" allow-outside-scroll="true" on-iron-overlay-canceled="_cancel">
        <div class="dropdown-content" slot="dropdown-content">
          <div class="inputContainer">
            <paper-input id="input" label="[[labelText]]" maxlength="[[maxLength]]" value="{{_inputText}}"></paper-input>
            <div class="buttons">
              <gr-button link="" id="cancelBtn" on-click="_cancel">cancel</gr-button>
              <gr-button link="" id="saveBtn" on-click="_save">save</gr-button>
            </div>
          </div>
        </div>
    </iron-dropdown>
`;
