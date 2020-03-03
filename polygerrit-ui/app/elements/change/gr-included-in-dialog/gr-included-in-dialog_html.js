import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        background-color: var(--dialog-background-color);
        display: block;
        max-height: 80vh;
        overflow-y: auto;
        padding: 4.5em var(--spacing-l) var(--spacing-l) var(--spacing-l);
      }
      header {
        background-color: var(--dialog-background-color);
        border-bottom: 1px solid var(--border-color);
        left: 0;
        padding: var(--spacing-l);
        position: absolute;
        right: 0;
        top: 0;
      }
      #title {
        display: inline-block;
        font-family: var(--header-font-family);
        font-size: var(--font-size-h3);
        font-weight: var(--font-weight-h3);
        line-height: var(--line-height-h3);
        margin-top: var(--spacing-xs);
      }
      #filterInput {
        display: inline-block;
        float: right;
        margin: 0 var(--spacing-l);
        padding: var(--spacing-xs);
      }
      .closeButtonContainer {
        float: right;
      }
      ul {
        margin-bottom: var(--spacing-l);
      }
      ul li {
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        background: var(--chip-background-color);
        display: inline-block;
        margin: 0 var(--spacing-xs) var(--spacing-s) var(--spacing-xs);
        padding: var(--spacing-xs) var(--spacing-s);
      }
      .loading.loaded {
        display: none;
      }
    </style>
    <header>
      <h1 id="title">Included In:</h1>
      <span class="closeButtonContainer">
        <gr-button id="closeButton" link="" on-click="_handleCloseTap">Close</gr-button>
      </span>
      <iron-input placeholder="Filter" on-bind-value-changed="_onFilterChanged">
        <input id="filterInput" is="iron-input" placeholder="Filter" on-bind-value-changed="_onFilterChanged">
      </iron-input>
    </header>
    <div class\$="[[_computeLoadingClass(_loaded)]]">Loading...</div>
    <template is="dom-repeat" items="[[_computeGroups(_includedIn, _filterText)]]" as="group">
      <div>
        <span>[[group.title]]:</span>
        <ul>
          <template is="dom-repeat" items="[[group.items]]">
            <li>[[item]]</li>
          </template>
        </ul>
      </div>
    </template>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
