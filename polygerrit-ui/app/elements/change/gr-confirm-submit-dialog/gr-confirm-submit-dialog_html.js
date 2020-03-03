import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      #dialog {
        min-width: 40em;
      }
      p {
        margin-bottom: var(--spacing-l);
      }
      .warningBeforeSubmit {
        color: var(--error-text-color);
        vertical-align: top;
        margin-right: var(--spacing-s);
      }
      @media screen and (max-width: 50em) {
        #dialog {
          min-width: inherit;
          width: 100%;
        }
      }
    </style>
    <gr-dialog id="dialog" confirm-label="Continue" confirm-on-enter="" on-cancel="_handleCancelTap" on-confirm="_handleConfirmTap">
      <div class="header" slot="header">
        [[action.label]]
      </div>
      <div class="main" slot="main">
        <gr-endpoint-decorator name="confirm-submit-change">
          <p>Ready to submit “<strong>[[change.subject]]</strong>”?</p>
          <template is="dom-if" if="[[change.is_private]]">
            <p>
              <iron-icon icon="gr-icons:error" class="warningBeforeSubmit"></iron-icon>
              <strong>Heads Up!</strong>
              Submitting this private change will also make it public.
            </p>
          </template>
          <template is="dom-if" if="[[change.unresolved_comment_count]]">
            <p>
              <iron-icon icon="gr-icons:error" class="warningBeforeSubmit"></iron-icon>
              [[_computeUnresolvedCommentsWarning(change)]]
            </p>
          </template>
          <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
          <gr-endpoint-param name="action" value="[[action]]"></gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>
    </gr-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
