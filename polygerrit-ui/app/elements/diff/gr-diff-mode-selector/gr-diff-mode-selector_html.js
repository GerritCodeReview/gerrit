import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        /* Used to remove horizontal whitespace between the icons. */
        display: flex;
      }
      gr-button.selected iron-icon {
        color: var(--link-color);
      }
      iron-icon {
        height: 1.3rem;
        width: 1.3rem;
      }
    </style>
    <gr-button id="sideBySideBtn" link="" has-tooltip="" class\$="[[_computeSelectedClass(mode, _VIEW_MODES.SIDE_BY_SIDE)]]" title="Side-by-side diff" on-click="_handleSideBySideTap">
      <iron-icon icon="gr-icons:side-by-side"></iron-icon>
    </gr-button>
    <gr-button id="unifiedBtn" link="" has-tooltip="" title="Unified diff" class\$="[[_computeSelectedClass(mode, _VIEW_MODES.UNIFIED)]]" on-click="_handleUnifiedTap">
      <iron-icon icon="gr-icons:unified"></iron-icon>
    </gr-button>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
