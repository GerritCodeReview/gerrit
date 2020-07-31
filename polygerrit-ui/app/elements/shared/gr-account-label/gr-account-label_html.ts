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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    :host {
      display: inline-block;
      vertical-align: top;
      position: relative;
      border-radius: var(--label-border-radius);
      /* Setting this really high, so all the following rules don't change
           anything, only if --account-max-length is actually set to something
           smaller like 20ch. */
      max-width: var(--account-max-length, 500px);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    :host::after {
      content: var(--account-label-suffix);
    }
    :host([deselected]) {
      background-color: var(--background-color-primary);
      border: 1px solid var(--comment-separator-color);
      border-radius: 8px;
      color: var(--deemphasized-text-color);
    }
    :host([selected]) {
      background-color: var(--chip-selected-background-color);
      border: 1px solid var(--chip-selected-background-color);
      border-radius: 8px;
      color: var(--chip-selected-text-color);
    }
    :host([selected]) iron-icon.attention {
      color: var(--chip-selected-text-color);
    }
    gr-avatar {
      height: calc(var(--line-height-normal) - 2px);
      width: calc(var(--line-height-normal) - 2px);
      vertical-align: sub;
    }
    .text {
      @apply --gr-account-label-text-style;
    }
    .text:hover {
      @apply --gr-account-label-text-hover-style;
    }
    iron-icon.attention {
      width: 12px;
      height: 12px;
      vertical-align: top;
      position: relative;
      top: 4px;
      padding-left: calc(1.5 * var(--spacing-s));
    }
    iron-icon.status {
      width: 14px;
      height: 14px;
      vertical-align: top;
      position: relative;
      top: 2px;
    }
  </style>
  <span>
    <template is="dom-if" if="[[!hideHovercard]]">
      <gr-hovercard-account
        account="[[account]]"
        change="[[change]]"
        highlight-attention="[[highlightAttention]]"
        voteable-text="[[voteableText]]"
      >
      </gr-hovercard-account>
    </template>
    <template
      is="dom-if"
      if="[[_hasAttention(_config, highlightAttention, account, change, forceAttention)]]"
    >
      <iron-icon class="attention" icon="gr-icons:attention"></iron-icon>
    </template>
    <template is="dom-if" if="[[!hideAvatar]]">
      <gr-avatar account="[[account]]" image-size="32"></gr-avatar>
    </template>
    <span class="text">
      <span class="name">[[_computeName(account, _config)]]</span>
      <template is="dom-if" if="[[!hideStatus]]">
        <template is="dom-if" if="[[account.status]]">
          <iron-icon class="status" icon="gr-icons:calendar"></iron-icon>
        </template>
      </template>
    </span>
  </span>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
