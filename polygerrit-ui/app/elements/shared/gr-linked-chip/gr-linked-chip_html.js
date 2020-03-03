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
      .transparentBackground,
      gr-button.transparentBackground {
        background-color: transparent;
      }
      :host([disabled]) {
        opacity: .6;
        pointer-events: none;
      }
      a {
       color: var(--linked-chip-text-color);
      }
      iron-icon {
        height: 1.2rem;
        width: 1.2rem;
      }
    </style>
    <div class\$="container [[_getBackgroundClass(transparentBackground)]]">
      <a href\$="[[href]]">
        <gr-limited-text limit="[[limit]]" text="[[text]]"></gr-limited-text>
      </a>
      <gr-button id="remove" link="" hidden\$="[[!removable]]" hidden="" class\$="remove [[_getBackgroundClass(transparentBackground)]]" on-click="_handleRemoveTap">
        <iron-icon icon="gr-icons:close"></iron-icon>
      </gr-button>
    </div>
`;
