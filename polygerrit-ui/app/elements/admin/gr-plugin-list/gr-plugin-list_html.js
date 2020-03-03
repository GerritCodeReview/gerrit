import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-table-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <gr-list-view filter="[[_filter]]" items-per-page="[[_pluginsPerPage]]" items="[[_plugins]]" loading="[[_loading]]" offset="[[_offset]]" path="[[_path]]">
      <table id="list" class="genericList">
        <tbody><tr class="headerRow">
          <th class="name topHeader">Plugin Name</th>
          <th class="version topHeader">Version</th>
          <th class="status topHeader">Status</th>
        </tr>
        <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        </tbody><tbody class\$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_shownPlugins]]">
            <tr class="table">
              <td class="name">
                <template is="dom-if" if="[[item.index_url]]">
                  <a href\$="[[_computePluginUrl(item.index_url)]]">[[item.id]]</a>
                </template>
                <template is="dom-if" if="[[!item.index_url]]">
                  [[item.id]]
                </template>
              </td>
              <td class="version">[[item.version]]</td>
              <td class="status">[[_status(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </gr-list-view>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
