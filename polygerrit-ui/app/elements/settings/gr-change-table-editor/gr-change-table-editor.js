/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  Polymer({
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
      window.Gerrit.ChangeTableBehavior,
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
    },
  });
})();
