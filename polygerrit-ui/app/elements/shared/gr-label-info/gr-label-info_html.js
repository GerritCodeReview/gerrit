import { html } from '@polymer/polymer/lib/utils/html-tag.js';

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
      gr-account-chip {
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
    <p class\$="placeholder [[_computeShowPlaceholder(labelInfo, change.labels.*)]]">
        No votes.
      </p><table>
      
      <template is="dom-repeat" items="[[_mapLabelInfo(labelInfo, account, change.labels.*)]]" as="mappedLabel">
        <tr class="labelValueContainer">
          <td>
            <gr-label has-tooltip="" title="[[_computeValueTooltip(labelInfo, mappedLabel.value)]]" class\$="[[mappedLabel.className]] voteChip">
              [[mappedLabel.value]]
            </gr-label>
          </td>
          <td>
            <gr-account-chip account="[[mappedLabel.account]]" transparent-background=""></gr-account-chip>
          </td>
          <td>
            <gr-button link="" aria-label="Remove" on-click="_onDeleteVote" tooltip="Remove vote" data-account-id\$="[[mappedLabel.account._account_id]]" class\$="deleteBtn [[_computeDeleteClass(mappedLabel.account, mutable, change)]]">
              <iron-icon icon="gr-icons:delete"></iron-icon>
            </gr-button>
          </td>
        </tr>
      </template>
    </table>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
