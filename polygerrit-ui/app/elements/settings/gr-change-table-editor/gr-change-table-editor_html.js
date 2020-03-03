import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      #changeCols {
        width: auto;
      }
      #changeCols .visibleHeader {
        text-align: center;
      }
      .checkboxContainer {
        cursor: pointer;
        text-align: center;
      }
      .checkboxContainer input {
        cursor: pointer;
      }
      .checkboxContainer:hover {
        outline: 1px solid var(--border-color);
      }
    </style>
    <div class="gr-form-styles">
      <table id="changeCols">
        <thead>
          <tr>
            <th class="nameHeader">Column</th>
            <th class="visibleHeader">Visible</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Number</td>
            <td class="checkboxContainer" on-click="_handleCheckboxContainerClick">
              <input type="checkbox" name="number" on-click="_handleNumberCheckboxClick" checked\$="[[showNumber]]">
            </td>
          </tr>
          <template is="dom-repeat" items="[[columnNames]]">
            <tr>
              <td>[[item]]</td>
              <td class="checkboxContainer" on-click="_handleCheckboxContainerClick">
                <input type="checkbox" name="[[item]]" on-click="_handleTargetClick" checked\$="[[!isColumnHidden(item, displayedColumns)]]">
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
`;
