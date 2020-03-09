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
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-table-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <gr-list-view filter="[[_filter]]" items="false" offset="0" loading="[[_loading]]" path="[[_path]]">
      <table id="list" class="genericList">
        <tbody><tr class="headerRow">
          <th class="name topHeader">Name</th>
          <th class="name topHeader"></th>
          <th class="name topHeader"></th>
        </tr>
        <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        </tbody><tbody class\$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_documentationSearches]]">
            <tr class="table">
              <td class="name">
                <a href\$="[[_computeSearchUrl(item.url)]]">[[item.title]]</a>
              </td>
              <td></td>
              <td></td>
            </tr>
          </template>
        </tbody>
      </table>
    </gr-list-view>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
