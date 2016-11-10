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
    is: 'gr-dashboard-editor',

    properties: {
      dashboardItems: Array,
      _newName: String,
    },

    _handleMoveUpButton: function(e) {
      var index = e.target.dataIndex;
      if (index === 0) { return; }
      var row = this.dashboardItems[index];
      var prev = this.dashboardItems[index - 1];
      this.splice('dashboardItems', index - 1, 2, row, prev);
    },

    _handleMoveDownButton: function(e) {
      var index = e.target.dataIndex;
      if (index === this.dashboardItems.length - 1) { return; }
      var row = this.dashboardItems[index];
      var next = this.dashboardItems[index + 1];
      this.splice('dashboardItems', index, 2, next, row);
    },

    _handleDeleteButton: function(e) {
      var index = e.target.dataIndex;
      this.splice('dashboardItems', index, 1);
    },

    _handleAddButton: function() {
      if (this._computeAddDisabled(this._newName)) { return; }

      this.splice('dashboardItems', this.dashboardItems.length, 0, {
        name: this._newName,
        target: '_blank',
      });

      this._newName = '';
    },

    _computeAddDisabled: function(newName) {
      return !newName.length;
    },

    _handleInputKeydown: function(e) {
      if (e.keyCode === 13) {
        e.stopPropagation();
        this._handleAddButton();
      }
    },    
  });
})();
