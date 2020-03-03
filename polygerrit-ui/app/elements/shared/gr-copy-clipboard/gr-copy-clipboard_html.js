import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .text {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
      }
      .copyText {
        flex-grow: 1;
        margin-right: var(--spacing-s);
      }
      .hideInput {
        display: none;
      }
      input#input {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
        @apply --text-container-style;
        width: 100%;
      }
      /*
       * Typically icons are 20px, which is the normal line-height.
       * The copy icon is too prominent at 20px, so we choose 16px
       * here, but add 2x2px padding below, so the entire
       * component should still fit nicely into a normal inline
       * layout flow.
       */
      #icon {
        height: 16px;
        width: 16px;
      }
      gr-button {
        --gr-button: {
          padding: 2px;
        }
      }
    </style>
    <div class="text">
      <iron-input class="copyText" type="text" bind-value="[[text]]" on-tap="_handleInputClick" readonly="">
        <input id="input" is="iron-input" class\$="[[_computeInputClass(hideInput)]]" type="text" bind-value="[[text]]" on-click="_handleInputClick" readonly="">
      </iron-input>
      <gr-button id="button" link="" has-tooltip="[[hasTooltip]]" class="copyToClipboard" title="[[buttonTitle]]" on-click="_copyToClipboard">
        <iron-icon id="icon" icon="gr-icons:content-copy"></iron-icon>
      </gr-button>
    </div>
`;
