import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: inline-block;
        border-radius: 50%;
        background-size: cover;
        background-color: var(--avatar-background-color, #f1f2f3);
      }
    </style>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
