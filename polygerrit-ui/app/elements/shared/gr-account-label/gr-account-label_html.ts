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
  <style>
    :host {
      display: inline-block;
      vertical-align: top;
      position: relative;
      border-radius: var(--label-border-radius);
      box-sizing: border-box;
      white-space: nowrap;
      padding: 0 var(--account-label-padding-horizontal, 0);
    }
    /* If the first element is the avatar, then we cancel the left padding, so
       we can fit nicely into the gr-account-chip rounding.
       The obvious alternative of 'chip has padding' and 'avatar gets negative
       margin' does not work, because we need 'overflow:hidden' on the label. */
    :host([cancel-left-padding]) {
      padding-left: 0;
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
      vertical-align: top;
      position: relative;
      top: 1px;
    }
    .text {
      @apply --gr-account-label-text-style;
    }
    .text:hover {
      @apply --gr-account-label-text-hover-style;
    }
    #attentionButton {
      /* This negates the 4px horizontal padding, which we appreciate as a
         larger click target, but which we don't want to consume space. :-) */
      margin: 0 -4px 0 -4px;
      vertical-align: top;
    }
    iron-icon.attention {
      color: var(--deemphasized-text-color);
      width: 12px;
      height: 12px;
      vertical-align: top;
    }
    iron-icon.status {
      color: var(--deemphasized-text-color);
      width: 14px;
      height: 14px;
      vertical-align: top;
      position: relative;
      top: 2px;
    }
    .name {
      display: inline-block;
      vertical-align: top;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: var(--account-max-length, 180px);
    }
    .hasAttention .name {
      font-weight: var(--font-weight-bold);
    }
  </style>
  <span>
    <template is="dom-if" if="[[!hideHovercard]]">
      <gr-hovercard-account
        for="hovercardTarget"
        account="[[account]]"
        change="[[change]]"
        highlight-attention="[[highlightAttention]]"
        voteable-text="[[voteableText]]"
      >
      </gr-hovercard-account>
    </template>
    <template
      is="dom-if"
      if="[[_hasAttention(highlightAttention, account, change, forceAttention)]]"
    >
      <gr-button
        id="attentionButton"
        link=""
        aria-label="Remove user from attention set"
        on-click="_handleRemoveAttentionClick"
        disabled="[[!_computeAttentionButtonEnabled(highlightAttention, account, change, _selfAccount, selected)]]"
        has-tooltip="[[_computeAttentionButtonEnabled(highlightAttention, account, change, _selfAccount, false)]]"
        title="[[_computeAttentionIconTitle(highlightAttention, account, change, _selfAccount, forceAttention, selected)]]"
        ><iron-icon class="attention" icon="gr-icons:attention"></iron-icon>
      </gr-button>
    </template>
  </span>
  <span
    id="hovercardTarget"
    class$="[[_computeHasAttentionClass(highlightAttention, account, change, forceAttention)]]"
  >
    <template is="dom-if" if="[[!hideAvatar]]">
      <gr-avatar account="[[account]]" image-size="32"></gr-avatar>
    </template>
    <span class="text">
      <span class="name">[[_computeName(account, _config, firstName)]]</span>
      <template is="dom-if" if="[[!hideStatus]]">
        <template is="dom-if" if="[[account.status]]">
          <iron-icon class="status" icon="gr-icons:calendar"></iron-icon>
        </template>
      </template>
    </span>
  </span>
`;
