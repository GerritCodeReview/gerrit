import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      button {
        background-color: transparent;
        cursor: pointer;
      }
      iron-icon.active {
        fill: var(--link-color);
      }
      iron-icon {
        vertical-align: top;
        --iron-icon-height: var(--gr-change-star-size, var(--line-height-normal, 20px));
        --iron-icon-width: var(--gr-change-star-size, var(--line-height-normal, 20px));
      }
    </style>
    <button aria-label="Change star" on-click="toggleStar">
      <iron-icon class\$="[[_computeStarClass(change.starred)]]" icon\$="[[_computeStarIcon(change.starred)]]"></iron-icon>
    </button>
`;
