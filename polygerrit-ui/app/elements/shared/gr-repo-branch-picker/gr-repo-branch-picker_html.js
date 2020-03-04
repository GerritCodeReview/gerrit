/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

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
