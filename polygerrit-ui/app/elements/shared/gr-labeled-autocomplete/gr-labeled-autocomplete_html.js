import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        width: 12em;
      }
      #container {
        background: var(--chip-background-color);
        border-radius: 1em;
        padding: var(--spacing-m);
      }
      #header {
        color: var(--deemphasized-text-color);
        font-weight: var(--font-weight-bold);
        font-size: var(--font-size-small);
      }
      #body {
        display: flex;
      }
      #trigger {
        color: var(--deemphasized-text-color);
        cursor: pointer;
        padding-left: var(--spacing-s);
      }
      #trigger:hover {
        color: var(--primary-text-color);
      }
    </style>
    <div id="container">
      <div id="header">[[label]]</div>
      <div id="body">
        <gr-autocomplete id="autocomplete" threshold="[[_autocompleteThreshold]]" query="[[query]]" disabled="[[disabled]]" placeholder="[[placeholder]]" borderless=""></gr-autocomplete>
        <div id="trigger" on-click="_handleTriggerClick">▼</div>
      </div>
    </div>
`;
