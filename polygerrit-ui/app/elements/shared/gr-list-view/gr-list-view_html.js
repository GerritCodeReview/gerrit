import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      #filter {
        max-width: 25em;
      }
      #filter:focus {
        outline: none;
      }
      #topContainer {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: space-between;
        margin: 0 var(--spacing-l);
      }
      #createNewContainer:not(.show) {
        display: none;
      }
      a {
        color: var(--primary-text-color);
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }
      nav {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: flex-end;
        margin-right: 20px;
      }
      nav,
      iron-icon {
        color: var(--deemphasized-text-color);
      }
      iron-icon {
        height: 1.85rem;
        margin-left: 16px;
        width: 1.85rem;
      }
    </style>
    <div id="topContainer">
      <div class="filterContainer">
        <label>Filter:</label>
        <iron-input type="text" bind-value="{{filter}}">
          <input is="iron-input" type="text" id="filter" bind-value="{{filter}}">
        </iron-input>
      </div>
      <div id="createNewContainer" class\$="[[_computeCreateClass(createNew)]]">
        <gr-button primary="" link="" id="createNew" on-click="_createNewItem">
          Create New
        </gr-button>
      </div>
    </div>
    <slot></slot>
    <nav>
      Page [[_computePage(offset, itemsPerPage)]]
      <a id="prevArrow" href\$="[[_computeNavLink(offset, -1, itemsPerPage, filter, path)]]" hidden\$="[[_hidePrevArrow(loading, offset)]]" hidden="">
        <iron-icon icon="gr-icons:chevron-left"></iron-icon>
      </a>
      <a id="nextArrow" href\$="[[_computeNavLink(offset, 1, itemsPerPage, filter, path)]]" hidden\$="[[_hideNextArrow(loading, items)]]" hidden="">
        <iron-icon icon="gr-icons:chevron-right"></iron-icon>
      </a>
    </nav>
`;
