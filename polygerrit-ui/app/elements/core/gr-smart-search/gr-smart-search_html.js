import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">

    </style>
    <gr-search-bar id="search" value="{{searchQuery}}" on-handle-search="_handleSearch" project-suggestions="[[_projectSuggestions]]" group-suggestions="[[_groupSuggestions]]" account-suggestions="[[_accountSuggestions]]"></gr-search-bar>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
