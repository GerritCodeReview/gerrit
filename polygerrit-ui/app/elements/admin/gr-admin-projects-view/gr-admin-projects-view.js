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

  var NUMBER_FIXED_COLUMNS = 3;

  Polymer({
    is: 'gr-admin-projects-view',

    /**
     * Fired when next page key shortcut was pressed.
     *
     * @event next-page
     */

    /**
     * Fired when previous page key shortcut was pressed.
     *
     * @event previous-page
     */

    hostAttributes: {
      tabindex: 0,
    },

    properties: {
      /**
       * The logged-in user's account, or an empty object if no user is logged
       * in.
       */
      account: {
        type: Object,
        value: function() { return {}; },
      },
      /**
       * ChangeInfo objects grouped into arrays. The groups and changes
       * properties should not be used together.
       */
      groups: {
        type: Array,
        value: function() { return []; },
      },
      groupTitles: {
        type: Array,
        value: function() { return []; },
      },
      selectedIndex: {
        type: Number,
        notify: true,
      },
      showNumber: Boolean, // No default value to prevent flickering.
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },
    },

    behaviors: [
      Gerrit.AdminProjectsBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
    ],

    attached: function() {
      this._loadPreferences();
    },

    _lowerCase: function(column) {
      return column.toLowerCase();
    },

    _loadPreferences: function() {
      return this._getLoggedIn().then(function(loggedIn) {
        this.changeTableColumns = this.columnNames;

        if (!loggedIn) {
          this.showNumber = false;
          this.visibleChangeTableColumns = this.columnNames;
          return;
        }
        return this._getPreferences().then(function(preferences) {
          this.showNumber = !!(preferences &&
              preferences.legacycid_in_change_table);
          this.visibleChangeTableColumns = preferences.change_table.length > 0 ?
              preferences.change_table : this.columnNames;
        }.bind(this));
      }.bind(this));
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _getPreferences: function() {
      return this.$.restAPI.getPreferences();
    },

    _computeColspan: function(changeTableColumns, labelNames) {
      return changeTableColumns.length + labelNames.length +
          NUMBER_FIXED_COLUMNS;
    },

    _groupTitle: function(groupIndex) {
      if (groupIndex > this.groupTitles.length - 1) { return null; }
      return this.groupTitles[groupIndex];
    },

    _computeItemSelected: function(index, groupIndex, selectedIndex) {
      var idx = 0;
      for (var i = 0; i < groupIndex; i++) {
        idx += this.groups[i].length;
      }
      idx += index;
      return idx == selectedIndex;
    },

    _getAggregateGroupsLen: function(groups) {
      groups = groups || [];
      var len = 0;
      this.groups.forEach(function(group) {
        len += group.length;
      });
      return len;
    },

    _changeURLForIndex: function(index) {
      var changeEls = this._getListItems();
      if (index < changeEls.length && changeEls[index]) {
        return changeEls[index].changeURL;
      }
      return '';
    },

    _getListItems: function() {
      return Polymer.dom(this.root).querySelectorAll('gr-admin-project-view');
    },
  });
})();