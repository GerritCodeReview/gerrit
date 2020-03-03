import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: inline;
      }
      :host::after {
        content: var(--account-label-suffix);
      }
      gr-avatar {
        height: var(--line-height-normal);
        width: var(--line-height-normal);
        vertical-align: top;
      }
      .text {
        @apply --gr-account-label-text-style;
      }
      .text:hover {
        @apply --gr-account-label-text-hover-style;
      }
      .email,
      .showEmail .name {
        display: none;
      }
      .showEmail .email {
        display: inline-block;
      }
    </style>
    <span>
      <template is="dom-if" if="[[!hideAvatar]]">
        <gr-avatar account="[[account]]" image-size="[[avatarImageSize]]"></gr-avatar>
      </template>
      <span class\$="text [[_computeShowEmailClass(account)]]">
        <span class="name">
          [[_computeName(account, _serverConfig)]]</span>
        <span class="email">
          [[_computeEmailStr(account)]]
        </span>
        <template is="dom-if" if="[[account.status]]">
          (<gr-limited-text disable-tooltip="true" limit="[[_computeStatusTextLength(account, _serverConfig)]]" text="[[account.status]]">
          </gr-limited-text>)
        </template>
      </span>
    </span>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
