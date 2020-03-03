import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        position: relative;
      }
      gr-selection-action-box {
        /**
         * Needs z-index to apear above wrapped content, since it's inseted
         * into DOM before it.
         */
        z-index: 10;
      }
    </style>
    <div class="contentWrapper">
      <slot></slot>
    </div>
`;
