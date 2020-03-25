/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

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
          <template is="dom-if" if="[[_computeHasChangeEdit(change)]]">
              <iron-icon icon="gr-icons:error" class="warningBeforeSubmit"></iron-icon>
              Your unpublished change edit will not be submitted [PUBLISH BUTTON?]
          </template>
          <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
          <gr-endpoint-param name="action" value="[[action]]"></gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>
    </gr-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
