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
      changeTableDisplayed: {
        type: Array,
        notify: true,
      },
      _changeTableColumnsWithState: {
        type: Array,
        computed: '_getColumnsWithState(changeTableDisplayed)',
      },
    },

    behaviors: [
      Gerrit.ChangeTableBehavior,
    ],

    _getButtonText: function(isShown) {
      return isShown ? 'Hide' : 'Show';
    },

    _getChangeTableColumnNames: function(changeTableColumnsWithState) {
      return changeTableColumnsWithState.filter(function(column) {
        return column.isShown === true;
      }).map(function(column) {
        return column.column;
      });
    },

    _getColumnsWithState: function(changeTableDisplayed) {
      return this.CHANGE_TABLE_COLUMNS.map(function(column) {
        return {
          column: column,
          isShown: changeTableDisplayed.indexOf(column) !== -1,
        };
      });
    },

    _handleButtonToggle: function(e) {
      var model = e.model;
      model.set('item.isShown', !model.item.isShown);
      this.set('changeTableDisplayed',
          this._getChangeTableColumnNames(this._changeTableColumnsWithState));
    },
  });
})();
