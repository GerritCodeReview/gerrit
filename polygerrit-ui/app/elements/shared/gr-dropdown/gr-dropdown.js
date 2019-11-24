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

  const REL_NOOPENER = 'noopener';
  const REL_EXTERNAL = 'external';

  Polymer({
    is: 'gr-dropdown',

    /**
     * Fired when a non-link dropdown item with the given ID is tapped.
     *
     * @event tap-item-<id>
     */

    /**
     * Fired when a non-link dropdown item is tapped.
     *
     * @event tap-item
     */

    properties: {
      items: {
        type: Array,
        observer: '_resetCursorStops',
      },
      downArrow: Boolean,
      topContent: Object,
      horizontalAlign: {
        type: String,
        value: 'left',
      },

      /**
       * Style the dropdown trigger as a link (rather than a button).
       */
      link: {
        type: Boolean,
        value: false,
      },

      verticalOffset: {
        type: Number,
        value: 40,
      },

      /**
       * List the IDs of dropdown buttons to be disabled. (Note this only
       * diisables bittons and not link entries.)
       */
      disabledIds: {
        type: Array,
        value() { return []; },
      },

      /**
       * The elements of the list.
       */
      _listElements: {
        type: Array,
        value() { return []; },
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      'down': '_handleDown',
      'enter space': '_handleEnter',
      'tab': '_handleTab',
      'up': '_handleUp',
    },

    /**
     * Handle the up key.
     * @param {!Event} e
     */
    _handleUp(e) {
      if (this.$.dropdown.opened) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.previous();
      } else {
        this._open();
      }
    },

    /**
     * Handle the down key.
     * @param {!Event} e
     */
    _handleDown(e) {
      if (this.$.dropdown.opened) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.next();
      } else {
        this._open();
      }
    },

    /**
     * Handle the tab key.
     * @param {!Event} e
     */
    _handleTab(e) {
      if (this.$.dropdown.opened) {
        // Tab in a native select is a no-op. Emulate this.
        e.preventDefault();
        e.stopPropagation();
      }
    },

    /**
     * Handle the enter key.
     * @param {!Event} e
     */
    _handleEnter(e) {
      e.preventDefault();
      e.stopPropagation();
      if (this.$.dropdown.opened) {
        // TODO(kaspern): This solution will not work in Shadow DOM, and
        // is not particularly robust in general. Find a better solution
        // when page.js has been abstracted away from components.
        const el = this.$.cursor.target.querySelector(':not([hidden])');
        if (el) { el.click(); }
      } else {
        this._open();
      }
    },

    /**
     * Handle a click on the iron-dropdown element.
     * @param {!Event} e
     */
    _handleDropdownClick(e) {
      this._close();
    },

    /**
     * Hanlde a click on the button to open the dropdown.
     * @param {!Event} e
     */
    _dropdownTriggerTapHandler(e) {
      e.preventDefault();
      e.stopPropagation();
      if (this.$.dropdown.opened) {
        this._close();
      } else {
        this._open();
      }
    },

    /**
     * Open the dropdown and initialize the cursor.
     */
    _open() {
      this.$.dropdown.open();
      this.$.cursor.setCursorAtIndex(0);
      Polymer.dom.flush();
      this.$.cursor.target.focus();
    },

    _close() {
      // async is needed so that that the click event is fired before the
      // dropdown closes (This was a bug for touch devices).
      this.async(() => {
        this.$.dropdown.close();
      }, 1);
    },

    /**
     * Get the class for a top-content item based on the given boolean.
     * @param {boolean} bold Whether the item is bold.
     * @return {string} The class for the top-content item.
     */
    _getClassIfBold(bold) {
      return bold ? 'bold-text' : '';
    },

    /**
     * Build a URL for the given host and path. The base URL will be only added,
     * if it is not already included in the path.
     * @param {!string} host
     * @param {!string} path
     * @return {!string} The scheme-relative URL.
     */
    _computeURLHelper(host, path) {
      const base = path.startsWith(this.getBaseUrl()) ?
          '' : this.getBaseUrl();
      return '//' + host + base + path;
    },

    /**
     * Build a scheme-relative URL for the current host. Will include the base
     * URL if one is present. Note: the URL will be scheme-relative but absolute
     * with regard to the host.
     * @param {!string} path The path for the URL.
     * @return {!string} The scheme-relative URL.
     */
    _computeRelativeURL(path) {
      const host = window.location.host;
      return this._computeURLHelper(host, path);
    },

    /**
     * Compute the URL for a link object.
     * @param {!Object} link The object describing the link.
     * @return {!string} The URL.
     */
    _computeLinkURL(link) {
      if (typeof link.url === 'undefined') {
        return '';
      }
      if (link.target || !link.url.startsWith('/')) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    },

    /**
     * Compute the value for the rel attribute of an anchor for the given link
     * object. If the link has a target value, then the rel must be "noopener"
     * for security reasons.
     * @param {!Object} link The object describing the link.
     * @return {?string} The rel value for the link.
     */
    _computeLinkRel(link) {
      // Note: noopener takes precedence over external.
      if (link.target) { return REL_NOOPENER; }
      if (link.external) { return REL_EXTERNAL; }
      return null;
    },

    /**
     * Handle a click on an item of the dropdown.
     * @param {!Event} e
     */
    _handleItemTap(e) {
      const id = e.target.getAttribute('data-id');
      const item = this.items.find(item => item.id === id);
      if (id && !this.disabledIds.includes(id)) {
        if (item) {
          this.dispatchEvent(new CustomEvent('tap-item', {detail: item}));
        }
        this.dispatchEvent(new CustomEvent('tap-item-' + id));
      }
    },

    /**
     * If a dropdown item is shown as a button, get the class for the button.
     * @param {string} id
     * @param {!Object} disabledIdsRecord The change record for the disabled IDs
     *     list.
     * @return {!string} The class for the item button.
     */
    _computeDisabledClass(id, disabledIdsRecord) {
      return disabledIdsRecord.base.includes(id) ? 'disabled' : '';
    },

    /**
     * Recompute the stops for the dropdown item cursor.
     */
    _resetCursorStops() {
      Polymer.dom.flush();
      this._listElements = Polymer.dom(this.root).querySelectorAll('li');
    },

    _computeHasTooltip(tooltip) {
      return !!tooltip;
    },

    _computeIsDownload(link) {
      return !!link.download;
    },
  });
})();
