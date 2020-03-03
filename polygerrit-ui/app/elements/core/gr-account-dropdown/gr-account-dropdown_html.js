import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      gr-dropdown {
        padding: 0 var(--spacing-m);
        --gr-button: {
          color: var(--header-text-color);
        }
        --gr-dropdown-item: {
          color: var(--primary-text-color);
        }
      }
      gr-avatar {
        height: 2em;
        width: 2em;
        vertical-align: middle;
      }
    </style>
    <gr-dropdown link="" items="[[links]]" top-content="[[topContent]]" horizontal-align="right">
        <span hidden\$="[[_hasAvatars]]" hidden="">[[_accountName(account)]]</span>
        <gr-avatar account="[[account]]" hidden\$="[[!_hasAvatars]]" hidden="" image-size="56" aria-label="Account avatar"></gr-avatar>
    </gr-dropdown>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
