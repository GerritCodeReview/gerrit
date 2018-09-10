/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../behaviors/gr-change-table-behavior/gr-change-table-behavior.js';

import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import '../../../styles/gr-form-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
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
            <td class="checkboxContainer" on-tap="_handleTargetTap">
              <input type="checkbox" name="number" checked\$="[[showNumber]]">
            </td>
          </tr>
          <template is="dom-repeat" items="[[columnNames]]">
            <tr>
              <td>[[item]]</td>
              <td class="checkboxContainer" on-tap="_handleTargetTap">
                <input type="checkbox" name="[[item]]" checked\$="[[!isColumnHidden(item, displayedColumns)]]">
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
`,

  is: 'gr-change-table-editor',

  properties: {
    displayedColumns: {
      type: Array,
      notify: true,
    },
    showNumber: {
      type: Boolean,
      notify: true,
    },
  },

  behaviors: [
    Gerrit.ChangeTableBehavior,
  ],

  _getButtonText(isShown) {
    return isShown ? 'Hide' : 'Show';
  },

  _updateDisplayedColumns(displayedColumns, name, checked) {
    if (!checked) {
      return displayedColumns.filter(column => {
        return name.toLowerCase() !== column.toLowerCase();
      });
    } else {
      return displayedColumns.concat([name]);
    }
  },

  /**
   * Handles tap on either the checkbox itself or the surrounding table cell.
   */
  _handleTargetTap(e) {
    let checkbox = Polymer.dom(e.target).querySelector('input');
    if (checkbox) {
      checkbox.click();
    } else {
      // The target is the checkbox itself.
      checkbox = Polymer.dom(e).rootTarget;
    }

    if (checkbox.name === 'number') {
      this.showNumber = checkbox.checked;
      return;
    }

    this.set('displayedColumns',
        this._updateDisplayedColumns(
            this.displayedColumns, checkbox.name, checkbox.checked));
  }
});
