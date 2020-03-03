import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style>
      :host {
        display: block;
        margin-bottom: var(--spacing-xxl);
      }
    </style>
    <h2 id="[[anchor]]">[[title]]</h2>
    <slot></slot>
`;
