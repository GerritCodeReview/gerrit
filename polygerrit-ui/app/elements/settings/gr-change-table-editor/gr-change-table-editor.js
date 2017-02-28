// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-change-table-editor',

    properties: {
      displayedColumns: {
        type: Array,
        notify: true,
      },
    },

    behaviors: [
      Gerrit.ChangeTableBehavior,
    ],

    _getButtonText: function(isShown) {
      return isShown ? 'Hide' : 'Show';
    },

    _updateDisplayedColumns: function(displayedColumns, name, checked) {
      if (!checked) {
        return displayedColumns.filter(function(column) {
          return name.toLowerCase() !== column.toLowerCase();
        });
      } else {
        return displayedColumns.concat([name]);
      }
    },

    /**
     * Handles tap on either the checkbox itself or the surrounding table cell.
     */
    _handleTargetTap: function(e) {
      var checkbox = Polymer.dom(e.target).querySelector('input');
      if (checkbox) {
        checkbox.click();
      } else {
        // The target is the checkbox itself.
        checkbox = Polymer.dom(e).rootTarget;
      }
      this.set('displayedColumns',
          this._updateDisplayedColumns(
              this.displayedColumns, checkbox.name, checkbox.checked));
    },
  });
})();
