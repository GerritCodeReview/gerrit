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
  <style include="gr-voting-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    .placeholder {
      color: var(--deemphasized-text-color);
      padding-top: var(--spacing-xs);
    }
    .hidden {
      display: none;
    }
    .voteChip {
      display: flex;
      justify-content: center;
      margin-right: var(--spacing-s);
      padding: 0;
      @apply --vote-chip-styles;
      border-width: 0;
    }
    .max {
      background-color: var(--vote-color-approved);
    }
    .min {
      background-color: var(--vote-color-rejected);
    }
    .positive {
      background-color: var(--vote-color-recommended);
    }
    .negative {
      background-color: var(--vote-color-disliked);
    }
    .hidden {
      display: none;
    }
    td {
      vertical-align: top;
    }
    tr {
      min-height: var(--line-height-normal);
    }
    gr-button {
      vertical-align: top;
      --gr-button: {
        height: var(--line-height-normal);
        width: var(--line-height-normal);
        padding: 0;
      }
    }
    gr-button[disabled] iron-icon {
      color: var(--border-color);
    }
    gr-account-link {
      margin-right: var(--spacing-xs);
    }
    iron-icon {
      height: calc(var(--line-height-normal) - 2px);
      width: calc(var(--line-height-normal) - 2px);
    }
    .labelValueContainer:not(:first-of-type) td {
      padding-top: var(--spacing-s);
    }
  </style>
  <p
    class$="placeholder [[_computeShowPlaceholder(labelInfo, change.labels.*)]]"
  >
    No votes.
  </p>
  <table>
    <template
      is="dom-repeat"
      items="[[_mapLabelInfo(labelInfo, account, change.labels.*)]]"
      as="mappedLabel"
    >
      <tr class="labelValueContainer">
        <td>
          <gr-label
            has-tooltip=""
            title="[[_computeValueTooltip(labelInfo, mappedLabel.value)]]"
            class$="[[mappedLabel.className]] voteChip"
          >
            [[mappedLabel.value]]
          </gr-label>
        </td>
        <td>
          <gr-account-link
            account="[[mappedLabel.account]]"
          ></gr-account-link>
        </td>
        <td>
          <gr-button
            link=""
            aria-label="Remove vote"
            on-click="_onDeleteVote"
            tooltip="Remove vote"
            data-account-id$="[[mappedLabel.account._account_id]]"
            class$="deleteBtn [[_computeDeleteClass(mappedLabel.account, mutable, change)]]"
          >
            <iron-icon icon="gr-icons:delete"></iron-icon>
          </gr-button>
        </td>
      </tr>
    </template>
  </table>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
