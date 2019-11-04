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

  const NUMBER_FIXED_COLUMNS = 3;
  const CLOSED_STATUS = ['MERGED', 'ABANDONED'];
  const LABEL_PREFIX_INVALID_PROLOG = 'Invalid-Prolog-Rules-Label-Name--';
  const MAX_SHORTCUT_CHARS = 5;

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
        value: null,
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
       *   name: string,
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
      _dynamicHeaderEndpoints: {
        type: Array,
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
      preferences: Object,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.ChangeTableBehavior,
      Gerrit.FireBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    listeners: {
      keydown: '_scopedKeydownHandler',
    },

    observers: [
      '_sectionsChanged(sections.*)',
      '_computePreferences(account, preferences)',
    ],

    keyboardShortcuts() {
      return {
        [this.Shortcut.CURSOR_NEXT_CHANGE]: '_nextChange',
        [this.Shortcut.CURSOR_PREV_CHANGE]: '_prevChange',
        [this.Shortcut.NEXT_PAGE]: '_nextPage',
        [this.Shortcut.PREV_PAGE]: '_prevPage',
        [this.Shortcut.OPEN_CHANGE]: '_openChange',
        [this.Shortcut.TOGGLE_CHANGE_REVIEWED]: '_toggleChangeReviewed',
        [this.Shortcut.TOGGLE_CHANGE_STAR]: '_toggleChangeStar',
        [this.Shortcut.REFRESH_CHANGE_LIST]: '_refreshChangeList',
      };
    },

    attached() {
      Gerrit.awaitPluginsLoaded().then(() => {
        this._dynamicHeaderEndpoints = Gerrit._endpoints.getDynamicEndpoints(
            'change-list-header');
      });
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
        this._openChange(e);
      }
    },

    _lowerCase(column) {
      return column.toLowerCase();
    },

    _computePreferences(account, preferences) {
      // Polymer 2: check for undefined
      if ([account, preferences].some(arg => arg === undefined)) {
        return;
      }

      this.changeTableColumns = this.columnNames;

      if (account) {
        this.showNumber = !!(preferences &&
            preferences.legacycid_in_change_table);
        this.visibleChangeTableColumns = preferences.change_table.length > 0 ?
            this.getVisibleColumns(preferences.change_table) : this.columnNames;
      } else {
        // Not logged in.
        this.showNumber = false;
        this.visibleChangeTableColumns = this.columnNames;
      }
    },

    _computeColspan(changeTableColumns, labelNames) {
      if (!changeTableColumns || !labelNames) return;
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
      if (labelName.startsWith(LABEL_PREFIX_INVALID_PROLOG)) {
        labelName = labelName.slice(LABEL_PREFIX_INVALID_PROLOG.length);
      }
      return labelName.split('-')
          .reduce((a, i) => {
            if (!i) { return a; }
            return a + i[0].toUpperCase();
          }, '')
          .slice(0, MAX_SHORTCUT_CHARS);
    },

    _changesChanged(changes) {
      this.sections = changes ? [{results: changes}] : [];
    },

    _processQuery(query) {
      let tokens = query.split(' ');
      const invalidTokens = ['limit:', 'age:', '-age:'];
      tokens = tokens.filter(token => {
        return !invalidTokens.some(invalidToken => {
          return token.startsWith(invalidToken);
        });
      });
      return tokens.join(' ');
    },

    _sectionHref(query) {
      return Gerrit.Nav.getUrlForSearchQuery(this._processQuery(query));
    },

    /**
     * Maps an index local to a particular section to the absolute index
     * across all the changes on the page.
     *
     * @param {number} sectionIndex index of section
     * @param {number} localIndex index of row within section
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
          !change.work_in_progress &&
          this.changeIsOpen(change) &&
          (!account || account._account_id != change.owner._account_id);
    },

    _computeItemHighlight(account, change) {
      // Do not show the assignee highlight if the change is not open.
      if (!change ||!change.assignee ||
          !account ||
          CLOSED_STATUS.indexOf(change.status) !== -1) {
        return false;
      }
      return account._account_id === change.assignee._account_id;
    },

    _nextChange(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.cursor.next();
    },

    _prevChange(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.cursor.previous();
    },

    _openChange(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      Gerrit.Nav.navigateToChange(this._changeForIndex(this.selectedIndex));
    },

    _nextPage(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e) && !this.isModifierPressed(e, 'shiftKey')) {
        return;
      }

      e.preventDefault();
      this.fire('next-page');
    },

    _prevPage(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e) && !this.isModifierPressed(e, 'shiftKey')) {
        return;
      }

      e.preventDefault();
      this.fire('previous-page');
    },

    _toggleChangeReviewed(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._toggleReviewedForIndex(this.selectedIndex);
    },

    _toggleReviewedForIndex(index) {
      const changeEls = this._getListItems();
      if (index >= changeEls.length || !changeEls[index]) {
        return;
      }

      const changeEl = changeEls[index];
      changeEl.toggleReviewed();
    },

    _refreshChangeList(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this._reloadWindow();
    },

    _reloadWindow() {
      window.location.reload();
    },

    _toggleChangeStar(e) {
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
      changeEl.$$('gr-change-star').toggleStar();
    },

    _changeForIndex(index) {
      const changeEls = this._getListItems();
      if (index < changeEls.length && changeEls[index]) {
        return changeEls[index].change;
      }
      return null;
    },

    _getListItems() {
      return Array.from(
          Polymer.dom(this.root).querySelectorAll('gr-change-list-item'));
    },

    _sectionsChanged() {
      // Flush DOM operations so that the list item elements will be loaded.
      Polymer.RenderStatus.afterNextRender(this, () => {
        this.$.cursor.stops = this._getListItems();
        this.$.cursor.moveToStart();
      });
    },

    _isOutgoing(section) {
      return !!section.isOutgoing;
    },

    _isEmpty(section) {
      return !section.results.length;
    },
  });
})();
