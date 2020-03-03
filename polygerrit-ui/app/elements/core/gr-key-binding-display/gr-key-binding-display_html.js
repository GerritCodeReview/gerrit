import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .key {
        background-color: var(--chip-background-color);
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        display: inline-block;
        font-weight: var(--font-weight-bold);
        padding: var(--spacing-xxs) var(--spacing-m);
        text-align: center;
      }
    </style>
    <template is="dom-repeat" items="[[binding]]">
      <template is="dom-if" if="[[index]]">
        or
      </template>
      <template is="dom-repeat" items="[[_computeModifiers(item)]]" as="modifier">
        <span class="key modifier">[[modifier]]</span>
      </template>
      <span class="key">[[_computeKey(item)]]</span>
    </template>
`;
