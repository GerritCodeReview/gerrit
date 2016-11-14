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
      changeTableItems: Array,
      changeTableNotDisplayed: Array,
    },

    behaviors: [
      Gerrit.ChangeTableBehavior,
    ],

    _handleDeleteButton: function(e) {
      var index = e.target.dataIndex;
      this.splice('changeTableItems', index, 1);

      // Use the change table behavior to make sure ordering of unused
      // columns ends up in the correct order. If the removed item is appended
      // to the end, when it is saved, the unused column order may shift around.
      this.set('changeTableNotDisplayed',
          this.getComplementColumns(this.changeTableItems));

    },

    _handleAddButton: function(e) {
      var index = e.target.dataIndex;
      var newColumn = this.changeTableNotDisplayed[index];
      this.splice('changeTableNotDisplayed', index, 1);

      this.splice('changeTableItems', this.getComplementColumns(
          this.changeTableNotDisplayed).indexOf(newColumn), 0, newColumn);
    },
  });
})();
