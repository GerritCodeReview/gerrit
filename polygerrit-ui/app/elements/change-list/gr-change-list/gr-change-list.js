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
    is: 'gr-change-list',

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
       * An array of ChangeInfo objects to render.
       * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info
       */
      changes: {
        type: Array,
        observer: '_changesChanged',
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
      labelNames: {
        type: Array,
        computed: '_computeLabelNames(groups)',
      },
      selectedIndex: {
        type: Number,
        notify: true,
      },
      showNumber: Boolean, // No default value to prevent flickering.
      showStar: {
        type: Boolean,
        value: false,
      },
      showReviewedState: {
        type: Boolean,
        value: false,
      },
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },
    },

    behaviors: [
      Gerrit.ChangeTableBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
    ],

    keyBindings: {
      'j': '_handleJKey',
      'k': '_handleKKey',
      'n ]': '_handleNKey',
      'o enter': '_handleEnterKey',
      'p [': '_handlePKey',
    },

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

    _computeLabelNames: function(groups) {
      if (!groups) { return []; }
      var labels = [];
      var nonExistingLabel = function(item) {
        return labels.indexOf(item) < 0;
      };
      for (var i = 0; i < groups.length; i++) {
        var group = groups[i];
        for (var j = 0; j < group.length; j++) {
          var change = group[j];
          if (!change.labels) { continue; }
          var currentLabels = Object.keys(change.labels);
          labels = labels.concat(currentLabels.filter(nonExistingLabel));
        }
      }
      return labels.sort();
    },

    _computeLabelShortcut: function(labelName) {
      return labelName.replace(/[a-z-]/g, '');
    },

    _changesChanged: function(changes) {
      this.groups = changes ? [changes] : [];
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

    _computeItemNeedsReview: function(account, change, showReviewedState) {
      return showReviewedState && !change.reviewed &&
          this.changeIsOpen(change.status) &&
          account._account_id != change.owner._account_id;
    },

    _computeItemAssigned: function(account, change) {
      if (!change.assignee) { return false; }
      return account._account_id === change.assignee._account_id;
    },

    _getAggregateGroupsLen: function(groups) {
      groups = groups || [];
      var len = 0;
      this.groups.forEach(function(group) {
        len += group.length;
      });
      return len;
    },

    _handleJKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      var len = this._getAggregateGroupsLen(this.groups);
      if (this.selectedIndex === len - 1) { return; }
      this.selectedIndex += 1;
    },

    _handleKKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      if (this.selectedIndex === 0) { return; }
      this.selectedIndex -= 1;
    },

    _handleEnterKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      page.show(this._changeURLForIndex(this.selectedIndex));
    },

    _handleNKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.fire('next-page');
    },

    _handlePKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.fire('previous-page');
    },

    _changeURLForIndex: function(index) {
      var changeEls = this._getListItems();
      if (index < changeEls.length && changeEls[index]) {
        return changeEls[index].changeURL;
      }
      return '';
    },

    _getListItems: function() {
      return Polymer.dom(this.root).querySelectorAll('gr-change-list-item');
    },
  });
})();
