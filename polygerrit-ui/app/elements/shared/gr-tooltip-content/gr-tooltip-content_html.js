import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style>
      iron-icon {
        width: var(--line-height-normal);
        height: var(--line-height-normal);
        vertical-align: top;
      }
    </style>
    <slot></slot><!--
 --><iron-icon icon="gr-icons:info" hidden\$="[[!showIcon]]"></iron-icon>
`;
