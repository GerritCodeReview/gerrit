import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      gr-labeled-autocomplete,
      iron-icon {
        display: inline-block;
      }
      iron-icon {
        margin-bottom: var(--spacing-l);
      }
    </style>
    <div>
      <gr-labeled-autocomplete id="repoInput" label="Repository" placeholder="Select repo" on-commit="_repoCommitted" query="[[_repoQuery]]">
      </gr-labeled-autocomplete>
      <iron-icon icon="gr-icons:chevron-right"></iron-icon>
      <gr-labeled-autocomplete id="branchInput" label="Branch" placeholder="Select branch" disabled="[[_branchDisabled]]" on-commit="_branchCommitted" query="[[_query]]">
      </gr-labeled-autocomplete>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
