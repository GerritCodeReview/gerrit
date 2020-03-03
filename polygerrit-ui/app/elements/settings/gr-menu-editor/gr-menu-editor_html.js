import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .buttonColumn {
        width: 2em;
      }
      .moveUpButton,
      .moveDownButton {
        width: 100%
      }
      tbody tr:first-of-type td .moveUpButton,
      tbody tr:last-of-type td .moveDownButton {
        display: none;
      }
      td.urlCell {
        word-break: break-word;
      }
      .newUrlInput {
        min-width: 23em;
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="gr-form-styles">
      <table>
        <thead>
          <tr>
            <th class="nameHeader">Name</th>
            <th class="url-header">URL</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[menuItems]]">
            <tr>
              <td>[[item.name]]</td>
              <td class="urlCell">[[item.url]]</td>
              <td class="buttonColumn">
                <gr-button link="" data-index\$="[[index]]" on-click="_handleMoveUpButton" class="moveUpButton">↑</gr-button>
              </td>
              <td class="buttonColumn">
                <gr-button link="" data-index\$="[[index]]" on-click="_handleMoveDownButton" class="moveDownButton">↓</gr-button>
              </td>
              <td>
                <gr-button link="" data-index\$="[[index]]" on-click="_handleDeleteButton" class="remove-button">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
        <tfoot>
          <tr>
            <th>
              <iron-input placeholder="New Title" on-keydown="_handleInputKeydown" bind-value="{{_newName}}">
                <input is="iron-input" placeholder="New Title" on-keydown="_handleInputKeydown" bind-value="{{_newName}}">
              </iron-input>
            </th>
            <th>
              <iron-input class="newUrlInput" placeholder="New URL" on-keydown="_handleInputKeydown" bind-value="{{_newUrl}}">
                <input class="newUrlInput" is="iron-input" placeholder="New URL" on-keydown="_handleInputKeydown" bind-value="{{_newUrl}}">
              </iron-input>
            </th>
            <th></th>
            <th></th>
            <th>
              <gr-button link="" disabled\$="[[_computeAddDisabled(_newName, _newUrl)]]" on-click="_handleAddButton">Add</gr-button>
            </th>
          </tr>
        </tfoot>
      </table>
    </div>
`;
