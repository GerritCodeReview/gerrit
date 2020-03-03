import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      form {
        display: flex;
      }
      gr-autocomplete {
        background-color: var(--view-background-color);
        border-radius: var(--border-radius);
        flex: 1;
        outline: none;
      }
    </style>
    <form>
      <gr-autocomplete show-search-icon="" id="searchInput" text="{{_inputVal}}" query="[[query]]" on-commit="_handleInputCommit" allow-non-suggested-values="" multi="" threshold="[[_threshold]]" tab-complete="" vertical-offset="30"></gr-autocomplete>
    </form>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
