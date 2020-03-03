import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      th {
        color: var(--deemphasized-text-color);
        text-align: left;
      }
      #emailTable .emailColumn {
        min-width: 32.5em;
        width: auto;
      }
      #emailTable .preferredHeader {
        text-align: center;
        width: 6em;
      }
      #emailTable .preferredControl {
        cursor: pointer;
        height: auto;
        text-align: center;
      }
      #emailTable .preferredControl .preferredRadio {
        height: auto;
      }
      .preferredControl:hover {
        outline: 1px solid var(--border-color);
      }
    </style>
    <div class="gr-form-styles">
      <table id="emailTable">
        <thead>
          <tr>
            <th class="emailColumn">Email</th>
            <th class="preferredHeader">Preferred</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_emails]]">
            <tr>
              <td class="emailColumn">[[item.email]]</td>
              <td class="preferredControl" on-click="_handlePreferredControlClick">
                <iron-input class="preferredRadio" type="radio" on-change="_handlePreferredChange" name="preferred" bind-value="[[item.email]]" checked\$="[[item.preferred]]">
                  <input is="iron-input" class="preferredRadio" type="radio" on-change="_handlePreferredChange" name="preferred" value="[[item.email]]" checked\$="[[item.preferred]]">
                </iron-input>
              </td>
              <td>
                <gr-button data-index\$="[[index]]" on-click="_handleDeleteButton" disabled="[[item.preferred]]" class="remove-button">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
