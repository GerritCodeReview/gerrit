import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      :host([pre]) span {
        white-space: var(--linked-text-white-space, pre-wrap);
        word-wrap: var(--linked-text-word-wrap, break-word);
      }
      :host([disabled]) a {
        color: inherit;
        text-decoration: none;
        pointer-events: none;
      }
    </style>
    <span id="output"></span>
`;
