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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-table-styles">
    .placeholder {
      color: var(--deemphasized-text-color);
    }
  </style>
  <gr-list-view
    filter="[[_filter]]"
    items-per-page="[[_pluginsPerPage]]"
    items="[[_plugins]]"
    loading="[[_loading]]"
    offset="[[_offset]]"
    path="[[_path]]"
  >
    <table id="list" class="genericList">
      <tbody>
        <tr class="headerRow">
          <th class="name topHeader">Plugin Name</th>
          <th class="version topHeader">Version</th>
          <th class="apiVersion topHeader">API Version</th>
          <th class="status topHeader">Status</th>
        </tr>
        <tr id="loading" class$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
      </tbody>
      <tbody class$="[[computeLoadingClass(_loading)]]">
        <template is="dom-repeat" items="[[_shownPlugins]]">
          <tr class="table">
            <td class="name">
              <template is="dom-if" if="[[item.index_url]]">
                <a href$="[[_computePluginUrl(item.index_url)]]">[[item.id]]</a>
              </template>
              <template is="dom-if" if="[[!item.index_url]]">
                [[item.id]]
              </template>
            </td>
            <td class="version">
              <template is="dom-if" if="[[item.version]]">
                [[item.version]]
              </template>
              <template is="dom-if" if="[[!item.version]]">
                <span class="placeholder">--</span>
              </template>
            </td>
            <td class="apiVersion">
              <template is="dom-if" if="[[item.api_version]]">
                [[item.api_version]]
              </template>
              <template is="dom-if" if="[[!item.api_version]]">
                <span class="placeholder">--</span>
              </template>
            </td>
            <td class="status">[[_status(item)]]</td>
          </tr>
        </template>
      </tbody>
    </table>
  </gr-list-view>
`;
