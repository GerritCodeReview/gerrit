import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      gr-autocomplete {
        display: inline-block;
        flex: 1;
        overflow: hidden;
      }
    </style>
    <gr-autocomplete id="input" borderless="[[borderless]]" placeholder="[[placeholder]]" threshold="[[suggestFrom]]" query="[[querySuggestions]]" allow-non-suggested-values="[[allowAnyInput]]" on-commit="_handleInputCommit" clear-on-commit="" warn-uncommitted="" text="{{_inputText}}" vertical-offset="24">
    </gr-autocomplete>
`;
