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

  const NUMBER_FIXED_COLUMNS = 3;

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
        value() { return {}; },
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
        value() { return []; },
      },
      groupTitles: {
        type: Array,
        value() { return []; },
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
        value() { return document.body; },
      },
      changeTableColumns: Array,
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
      'shift+r': '_handleRKey',
    },

    attached() {
      this._loadPreferences();
    },

    _lowerCase(column) {
      return column.toLowerCase();
    },

    _loadPreferences() {
      return this._getLoggedIn().then(loggedIn => {
        this.changeTableColumns = this.columnNames;

        if (!loggedIn) {
          this.showNumber = false;
          this.visibleChangeTableColumns = this.columnNames;
          return;
        }
        return this._getPreferences().then(preferences => {
          this.showNumber = !!(preferences &&
              preferences.legacycid_in_change_table);
          this.visibleChangeTableColumns = preferences.change_table.length > 0 ?
              preferences.change_table : this.columnNames;
        });
      });
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _getPreferences() {
      return this.$.restAPI.getPreferences();
    },

    _computeColspan(changeTableColumns, labelNames) {
      return changeTableColumns.length + labelNames.length +
          NUMBER_FIXED_COLUMNS;
    },

    _computeLabelNames(groups) {
      if (!groups) { return []; }
      let labels = [];
      const nonExistingLabel = function(item) {
        return !labels.includes(item);
      };
      for (let i = 0; i < groups.length; i++) {
        const group = groups[i];
        for (let j = 0; j < group.length; j++) {
          const change = group[j];
          if (!change.labels) { continue; }
          const currentLabels = Object.keys(change.labels);
          labels = labels.concat(currentLabels.filter(nonExistingLabel));
        }
      }
      return labels.sort();
    },

    _computeLabelShortcut(labelName) {
      return labelName.replace(/[a-z-]/g, '');
    },

    _changesChanged(changes) {
      this.groups = changes ? [changes] : [];
    },

    _groupTitle(groupIndex) {
      if (groupIndex > this.groupTitles.length - 1) { return null; }
      return this.groupTitles[groupIndex];
    },

    _computeItemSelected(index, groupIndex, selectedIndex) {
      let idx = 0;
      for (let i = 0; i < groupIndex; i++) {
        idx += this.groups[i].length;
      }
      idx += index;
      return idx == selectedIndex;
    },

    _computeItemNeedsReview(account, change, showReviewedState) {
      return showReviewedState && !change.reviewed &&
          this.changeIsOpen(change.status) &&
          account._account_id != change.owner._account_id;
    },

    _computeItemAssigned(account, change) {
      if (!change.assignee) { return false; }
      return account._account_id === change.assignee._account_id;
    },

    _getAggregateGroupsLen(groups) {
      groups = groups || [];
      let len = 0;
      for (const group of this.groups) {
        len += group.length;
      }
      return len;
    },

    _handleJKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      const len = this._getAggregateGroupsLen(this.groups);
      if (this.selectedIndex === len - 1) { return; }
      this.selectedIndex += 1;
    },

    _handleKKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      if (this.selectedIndex === 0) { return; }
      this.selectedIndex -= 1;
    },

    _handleEnterKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      page.show(this._changeURLForIndex(this.selectedIndex));
    },

    _handleNKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.fire('next-page');
    },

    _handlePKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this.fire('previous-page');
    },

    _handleRKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) {
        return;
      }

      e.preventDefault();
      window.location.reload();
    },

    _changeURLForIndex(index) {
      const changeEls = this._getListItems();
      if (index < changeEls.length && changeEls[index]) {
        return changeEls[index].changeURL;
      }
      return '';
    },

    _getListItems() {
      return Polymer.dom(this.root).querySelectorAll('gr-change-list-item');
    },
  });
})();
