import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      #watchedProjects .notifType {
        text-align: center;
        padding: 0 var(--spacing-s);
      }
      .notifControl {
        cursor: pointer;
        text-align: center;
      }
      .notifControl:hover {
        outline: 1px solid var(--border-color);
      }
      .projectFilter {
        color: var(--deemphasized-text-color);
        font-style: italic;
        margin-left: var(--spacing-l);
      }
      .newFilterInput {
        width: 100%;
      }
    </style>
    <div class="gr-form-styles">
      <table id="watchedProjects">
        <thead>
          <tr>
            <th>Repo</th>
            <template is="dom-repeat" items="[[_getTypes()]]">
              <th class="notifType">[[item.name]]</th>
            </template>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_projects]]" as="project" index-as="projectIndex">
            <tr>
              <td>
                [[project.project]]
                <template is="dom-if" if="[[project.filter]]">
                  <div class="projectFilter">[[project.filter]]</div>
                </template>
              </td>
              <template is="dom-repeat" items="[[_getTypes()]]" as="type">
                <td class="notifControl" on-click="_handleNotifCellClick">
                  <input type="checkbox" data-index\$="[[projectIndex]]" data-key\$="[[type.key]]" on-change="_handleCheckboxChange" checked\$="[[_computeCheckboxChecked(project, type.key)]]">
                </td>
              </template>
              <td>
                <gr-button link="" data-index\$="[[projectIndex]]" on-click="_handleRemoveProject">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
        <tfoot>
          <tr>
            <th>
              <gr-autocomplete id="newProject" query="[[_query]]" threshold="1" allow-non-suggested-values="" tab-complete="" placeholder="Repo"></gr-autocomplete>
            </th>
            <th colspan\$="[[_getTypeCount()]]">
              <iron-input class="newFilterInput" placeholder="branch:name, or other search expression">
                <input id="newFilter" class="newFilterInput" is="iron-input" placeholder="branch:name, or other search expression">
              </iron-input>
            </th>
            <th>
              <gr-button link="" on-click="_handleAddProject">Add</gr-button>
            </th>
          </tr>
        </tfoot>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
