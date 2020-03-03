import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: inline-block;
      }
      a {
        color: var(--primary-text-color);
        text-decoration: none;
      }
      gr-account-label {
        --gr-account-label-text-hover-style: {
          text-decoration: underline;
        };
      }
    </style>
    <span>
      <a href\$="[[_computeOwnerLink(account)]]" tabindex="-1">
        <gr-account-label account="[[account]]" additional-text="[[additionalText]]" avatar-image-size="[[avatarImageSize]]"></gr-account-label>
      </a>
    </span>
`;
