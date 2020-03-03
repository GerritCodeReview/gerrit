import { html } from '@polymer/polymer/lib/utils/html-tag.js';

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
