import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        margin-bottom: var(--spacing-xxl);
      }
    </style>
    <h3>[[title]]</h3>
    <gr-button title\$="[[tooltip]]" disabled\$="[[disabled]]" on-click="_onCommandTap">
      [[title]]
    </gr-button>
`;
