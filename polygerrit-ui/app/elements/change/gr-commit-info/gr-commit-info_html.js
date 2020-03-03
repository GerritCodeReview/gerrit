import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .container {
        align-items: center;
        display: flex;
      }
    </style>
    <div class="container">
      <template is="dom-if" if="[[_showWebLink]]">
        <a target="_blank" rel="noopener" href\$="[[_webLink]]">[[_computeShortHash(commitInfo)]]</a>
      </template>
      <template is="dom-if" if="[[!_showWebLink]]">
        [[_computeShortHash(commitInfo)]]
      </template>
      <gr-copy-clipboard has-tooltip="" button-title="Copy full SHA to clipboard" hide-input="" text="[[commitInfo.commit]]">
      </gr-copy-clipboard>
    </div>
`;
