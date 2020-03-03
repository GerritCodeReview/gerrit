import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
        #groups .nameColumn {
          min-width: 11em;
          width: auto;
        }
        .descriptionHeader {
          min-width: 21.5em;
        }
        .visibleCell {
          text-align: center;
          width: 6em;
        }
      </style>
    <div class="gr-form-styles">
      <table id="groups">
        <thead>
          <tr>
            <th class="nameHeader">Name</th>
            <th class="descriptionHeader">Description</th>
            <th class="visibleCell">Visible to all</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_groups]]">
            <tr>
              <td class="nameColumn">
                <a href\$="[[_computeGroupPath(item)]]">
                  [[item.name]]
                </a>
              </td>
              <td>[[item.description]]</td>
              <td class="visibleCell">[[_computeVisibleToAll(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
