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
import '../gr-hovercard/gr-hovercard-shared-style';
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-hovercard-shared-style">
    .top,
    .attention,
    .status,
    .voteable {
      padding: var(--spacing-s) var(--spacing-l);
    }
    .top {
      display: flex;
      padding-top: var(--spacing-xl);
      min-width: 300px;
    }
    gr-avatar {
      height: 48px;
      width: 48px;
      margin-right: var(--spacing-l);
    }
    .title,
    .email {
      color: var(--deemphasized-text-color);
    }
    .status iron-icon {
      width: 14px;
      height: 14px;
      vertical-align: top;
      position: relative;
      top: 2px;
    }
    .action {
      border-top: 1px solid var(--border-color);
      padding: var(--spacing-s) var(--spacing-l);
      --gr-button: {
        padding: var(--spacing-s) var(--spacing-m);
      }
    }
    .attention {
      background-color: var(--emphasis-color);
    }
    .attention iron-icon {
      width: 14px;
      height: 14px;
      vertical-align: top;
      position: relative;
      top: 3px;
    }
    .reason {
      padding-top: var(--spacing-s);
    }
  </style>
  <div id="container" role="tooltip" tabindex="-1">
    <template is="dom-if" if="[[_isShowing]]">
      <div class="top">
        <div class="avatar">
          <gr-avatar account="[[account]]" image-size="56"></gr-avatar>
        </div>
        <div class="account">
          <h3 class="name heading-3">[[account.name]]</h3>
          <div class="email">[[account.email]]</div>
        </div>
      </div>
      <template is="dom-if" if="[[account.status]]">
        <div class="status">
          <span class="title">
            <iron-icon icon="gr-icons:calendar"></iron-icon>
            Status:
          </span>
          <span class="value">[[account.status]]</span>
        </div>
      </template>
      <template is="dom-if" if="[[voteableText]]">
        <div class="voteable">
          <span class="title">Voteable:</span>
          <span class="value">[[voteableText]]</span>
        </div>
      </template>
      <template
        is="dom-if"
        if="[[_computeShowLabelNeedsAttention(_config, highlightAttention, account, change)]]"
      >
        <div class="attention">
          <div>
            <iron-icon icon="gr-icons:attention"></iron-icon>
            <span>
              [[_computeText(account, _selfAccount)]] turn to take action.
            </span>
          </div>
          <div class="reason">
            <span class="title">Reason:</span>
            <span class="value">[[_computeReason(change)]]</span>,
            <gr-date-formatter
              has-tooltip
              date-str="[[_computeLastUpdate(change)]]"
            ></gr-date-formatter>
          </div>
        </div>
      </template>
      <template
        is="dom-if"
        if="[[_computeShowActionAddToAttentionSet(_config, highlightAttention, account, change)]]"
      >
        <div class="action">
          <gr-button
            link=""
            no-uppercase=""
            on-click="_handleClickAddToAttentionSet"
          >
            Add to attention set
          </gr-button>
        </div>
      </template>
      <template
        is="dom-if"
        if="[[_computeShowActionRemoveFromAttentionSet(_config, highlightAttention, account, change)]]"
      >
        <div class="action">
          <gr-button
            link=""
            no-uppercase=""
            on-click="_handleClickRemoveFromAttentionSet"
          >
            Remove from attention set
          </gr-button>
        </div>
      </template>
    </template>
  </div>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
