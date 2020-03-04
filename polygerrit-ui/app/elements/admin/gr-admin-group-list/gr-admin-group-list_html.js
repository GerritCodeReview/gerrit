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
    <gr-list-view create-new="[[_createNewCapability]]" filter="[[_filter]]" items="[[_groups]]" items-per-page="[[_groupsPerPage]]" loading="[[_loading]]" offset="[[_offset]]" on-create-clicked="_handleCreateClicked" path="[[_path]]">
      <table id="list" class="genericList">
        <tbody><tr class="headerRow">
          <th class="name topHeader">Group Name</th>
          <th class="description topHeader">Group Description</th>
          <th class="visibleToAll topHeader">Visible To All</th>
        </tr>
        <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        </tbody><tbody class\$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_shownGroups]]">
            <tr class="table">
              <td class="name">
                <a href\$="[[_computeGroupUrl(item.group_id)]]">[[item.name]]</a>
              </td>
              <td class="description">[[item.description]]</td>
              <td class="visibleToAll">[[_visibleToAll(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </gr-list-view>
    <gr-overlay id="createOverlay" with-backdrop="">
      <gr-dialog id="createDialog" class="confirmDialog" disabled="[[!_hasNewGroupName]]" confirm-label="Create" confirm-on-enter="" on-confirm="_handleCreateGroup" on-cancel="_handleCloseCreate">
        <div class="header" slot="header">
          Create Group
        </div>
        <div class="main" slot="main">
          <gr-create-group-dialog has-new-group-name="{{_hasNewGroupName}}" params="[[params]]" id="createNewModal"></gr-create-group-dialog>
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
