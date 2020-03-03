import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        cursor: pointer;
        font-family: var(--font-family);
        position: absolute;
        white-space: nowrap;
      }
    </style>
    <gr-tooltip id="tooltip" text="Press c to comment" position-below="[[positionBelow]]"></gr-tooltip>
`;
