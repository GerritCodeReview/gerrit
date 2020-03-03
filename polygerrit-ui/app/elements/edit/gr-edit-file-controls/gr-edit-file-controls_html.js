import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        align-items: center;
        display: flex;
        justify-content: flex-end;
      }
      #actions {
        margin-right: var(--spacing-l);
      }
      gr-button,
      gr-dropdown {
        --gr-button: {
          height: 1.8em;
        }
      }
      gr-dropdown {
        --gr-dropdown-item: {
          background-color: transparent;
          border: none;
          color: var(--link-color);
          text-transform: uppercase;
        }
      }
    </style>
    <gr-dropdown id="actions" items="[[_fileActions]]" down-arrow="" vertical-offset="20" on-tap-item="_handleActionTap" link="">Actions</gr-dropdown>
`;
