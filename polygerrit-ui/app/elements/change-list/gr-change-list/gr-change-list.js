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
       * ChangeInfo objects grouped into arrays. The sections and changes
       * properties should not be used together.
       *
       * @type {!Array<{
       *   sectionName: string,
       *   query: string,
       *   results: !Array<!Object>
       * }>}
       */
      sections: {
        type: Array,
        value() { return []; },
      },
      labelNames: {
        type: Array,
        computed: '_computeLabelNames(sections)',
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
      visibleChangeTableColumns: Array,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.ChangeTableBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    keyBindings: {
      'j': '_handleJKey',
      'k': '_handleKKey',
      'n ]': '_handleNKey',
      'o': '_handleOKey',
      'p [': '_handlePKey',
      'shift+r': '_handleRKey',
      's': '_handleSKey',
    },

    listeners: {
      keydown: '_scopedKeydownHandler',
    },

    /**
     * Iron-a11y-keys-behavior catches keyboard events globally. Some keyboard
     * events must be scoped to a component level (e.g. `enter`) in order to not
     * override native browser functionality.
     *
     * Context: Issue 7294
     */
    _scopedKeydownHandler(e) {
      if (e.keyCode === 13) {
        // Enter.
        this._handleOKey(e);
      }
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

    _computeLabelNames(sections) {
      if (!sections) { return []; }
      let labels = [];
      const nonExistingLabel = function(item) {
        return !labels.includes(item);
      };
      for (const section of sections) {
        if (!section.results) { continue; }
        for (const change of section.results) {
          if (!change.labels) { continue; }
          const currentLabels = Object.keys(change.labels);
          labels = labels.concat(currentLabels.filter(nonExistingLabel));
        }
      }
      return labels.sort();
    },

    _computeLabelShortcut(labelName) {
      return labelName.split('-').reduce((a, i) => {
        return a + i[0].toUpperCase();
      }, '');
    },

    _changesChanged(changes) {
      this.sections = changes ? [{results: changes}] : [];
    },

    _sectionHref(query) {
      return `${this.getBaseUrl()}/q/${this.encodeURL(query, true)}`;
    },

    /**
     * Maps an index local to a particular section to the absolute index
     * across all the changes on the page.
     *
     * @param sectionIndex {number} index of section
     * @param localIndex {number} index of row within section
     * @return {number} absolute index of row in the aggregate dashboard
     */
    _computeItemAbsoluteIndex(sectionIndex, localIndex) {
      let idx = 0;
      for (let i = 0; i < sectionIndex; i++) {
        idx += this.sections[i].results.length;
      }
      return idx + localIndex;
    },

    _computeItemSelected(sectionIndex, index, selectedIndex) {
      const idx = this._computeItemAbsoluteIndex(sectionIndex, index);
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

    _handleJKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      // Compute absolute index of item that would come after final item.
      const len = this._computeItemAbsoluteIndex(this.sections.length, 0);
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

    _handleOKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      Gerrit.Nav.navigateToChange(this._changeForIndex(this.selectedIndex));
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

    _handleSKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._toggleStarForIndex(this.selectedIndex);
    },

    _toggleStarForIndex(index) {
      const changeEls = this._getListItems();
      if (index >= changeEls.length || !changeEls[index]) {
        return;
      }

      const changeEl = changeEls[index];
      const change = changeEl.change;
      const newVal = !change.starred;
      changeEl.set('change.starred', newVal);
      this.$.restAPI.saveChangeStarred(change._number, newVal);
    },

    _changeForIndex(index) {
      const changeEls = this._getListItems();
      if (index < changeEls.length && changeEls[index]) {
        return changeEls[index].change;
      }
      return null;
    },

    _getListItems() {
      return Polymer.dom(this.root).querySelectorAll('gr-change-list-item');
    },
  });
})();
