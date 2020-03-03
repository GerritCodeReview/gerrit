import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      gr-account-chip {
        display: inline-block;
        margin: var(--spacing-xs) var(--spacing-xs) var(--spacing-xs) 0;
      }
      gr-account-entry {
        display: flex;
        flex: 1;
        min-width: 10em;
        margin: var(--spacing-xs) var(--spacing-xs) var(--spacing-xs) 0;
      }
      .group {
        --account-label-suffix: ' (group)';
      }
      .pending-add {
        font-style: italic;
      }
      .list {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        @apply --account-list-style;
      }
    </style>
    <!--
      NOTE(Issue 6419): Nest the inner dom-repeat template in a div rather than
      as a direct child of the dom-module's template.
    -->
    <div class="list">
      <template id="chips" is="dom-repeat" items="[[accounts]]" as="account">
        <gr-account-chip account="[[account]]" class\$="[[_computeChipClass(account)]]" data-account-id\$="[[account._account_id]]" removable="[[_computeRemovable(account, readonly)]]" on-keydown="_handleChipKeydown" tabindex="-1">
        </gr-account-chip>
      </template>
    </div>
    <gr-account-entry borderless="" hidden\$="[[_computeEntryHidden(maxCount, accounts.*, readonly)]]" id="entry" placeholder="[[placeholder]]" on-add="_handleAdd" on-input-keydown="_handleInputKeydown" allow-any-input="[[allowAnyInput]]" query-suggestions="[[_querySuggestions]]">
    </gr-account-entry>
    <slot></slot>
`;
