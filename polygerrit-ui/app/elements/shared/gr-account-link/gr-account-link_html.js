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
    :host {
      display: inline-block;
      /* Setting this really high, so all the following rules don't change
           anything, only if --account-max-length is actually set to something
           smaller like 20ch. */
      max-width: var(--account-max-length, 500px);
      overflow: hidden;
      text-overflow: ellipsis;
      vertical-align: top;
      white-space: nowrap;
    }
    a {
      color: var(--primary-text-color);
      text-decoration: none;
    }
    gr-account-label {
      --gr-account-label-text-hover-style: {
        text-decoration: underline;
      }
    }
  </style>
  <span>
    <a href$="[[_computeOwnerLink(account)]]">
      <gr-account-label
        account="[[account]]"
        change="[[change]]"
        show-attention="[[showAttention]]"
        hide-avatar="[[hideAvatar]]"
        hide-status="[[hideStatus]]"
        voteable-text="[[voteableText]]"
      >
      </gr-account-label>
    </a>
  </span>
`;
