import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      #agreements .nameColumn {
        min-width: 15em;
        width: auto;
      }
      #agreements .descriptionColumn {
        width: auto;
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="gr-form-styles">
      <table id="agreements">
        <thead>
          <tr>
            <th class="nameColumn">Name</th>
            <th class="descriptionColumn">Description</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_agreements]]">
            <tr>
              <td class="nameColumn">
                <a href\$="[[getUrlBase(item.url)]]" rel="external">
                  [[item.name]]
                </a>
              </td>
              <td class="descriptionColumn">[[item.description]]</td>
            </tr>
          </template>
        </tbody>
      </table>
      <a href\$="[[getUrl()]]">New Contributor Agreement</a>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
