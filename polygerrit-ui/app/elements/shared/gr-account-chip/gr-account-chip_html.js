import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        overflow: hidden;
      }
      .container {
        align-items: center;
        background: var(--chip-background-color);
        border-radius: .75em;
        display: inline-flex;
        padding: 0 var(--spacing-m);
      }
      :host([show-avatar]) .container {
        padding-left: 0;
      }
      gr-button.remove {
        --gr-remove-button-style: {
          border: 0;
          color: var(--deemphasized-text-color);
          font-weight: var(--font-weight-normal);
          height: .6em;
          line-height: 10px;
          margin-left: var(--spacing-xs);
          padding: 0;
          text-decoration: none;
        }
      }

      gr-button.remove:hover,
      gr-button.remove:focus {
        --gr-button: {
          @apply --gr-remove-button-style;
          color: #333;
        }
      }
      gr-button.remove {
        --gr-button: {
          @apply --gr-remove-button-style;
        }
      }
      :host:focus {
        border-color: transparent;
        box-shadow: none;
        outline: none;
      }
      :host:focus .container,
      :host:focus gr-button {
        background: #ccc;
      }
      .transparentBackground,
      gr-button.transparentBackground {
        background-color: transparent;
        padding: 0;
      }
      :host([disabled]) {
        opacity: .6;
        pointer-events: none;
      }
      iron-icon {
        height: 1.2rem;
        width: 1.2rem;
      }
    </style>
    <div class\$="container [[_getBackgroundClass(transparentBackground)]]">
      <gr-account-link account="[[account]]" additional-text="[[additionalText]]">
      </gr-account-link>
      <gr-button id="remove" link="" hidden\$="[[!removable]]" hidden="" tabindex="-1" aria-label="Remove" class\$="remove [[_getBackgroundClass(transparentBackground)]]" on-click="_handleRemoveTap">
        <iron-icon icon="gr-icons:close"></iron-icon>
      </gr-button>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
