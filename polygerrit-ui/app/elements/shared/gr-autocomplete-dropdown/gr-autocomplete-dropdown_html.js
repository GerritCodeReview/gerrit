import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        z-index: 100;
      }
      :host([is-hidden]) {
        display: none;
      }
      ul {
        list-style: none;
      }
      li {
        border-bottom: 1px solid var(--border-color);
        cursor: pointer;
        display: flex;
        justify-content: space-between;
        padding: var(--spacing-m) var(--spacing-l);
      }
      li:last-of-type {
        border: none;
      }
      li:focus {
        outline: none;
      }
      li:hover {
        background-color: var(--hover-background-color);
      }
      li.selected {
        background-color: var(--selection-background-color);
      }
      .dropdown-content {
        background: var(--dropdown-background-color);
        box-shadow: var(--elevation-level-2);
        border-radius: var(--border-radius);
        max-height: 50vh;
        overflow: auto;
      }
      @media only screen and (max-height: 35em) {
        .dropdown-content {
          max-height: 80vh;
        }
      }
      .label {
        color: var(--deemphasized-text-color);
        padding-left: var(--spacing-l);
      }
      .hide {
        display: none;
      }
    </style>
    <div class="dropdown-content" slot="dropdown-content" id="suggestions" role="listbox">
      <ul>
        <template is="dom-repeat" items="[[suggestions]]">
          <li data-index\$="[[index]]" data-value\$="[[item.dataValue]]" tabindex="-1" aria-label\$="[[item.name]]" class="autocompleteOption" role="option" on-click="_handleClickItem">
            <span>[[item.text]]</span>
            <span class\$="label [[_computeLabelClass(item)]]">[[item.label]]</span>
          </li>
        </template>
      </ul>
    </div>
    <gr-cursor-manager id="cursor" index="{{index}}" cursor-target-class="selected" scroll-behavior="never" focus-on-move="" stops="[[_suggestionEls]]"></gr-cursor-manager>
`;
