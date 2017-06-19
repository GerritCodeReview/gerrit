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

      _hasAvatars: String,

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

    attached() {
      this.$.restAPI.getConfig().then(cfg => {
        this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);
      });
    },

    _handleUp(e) {
      if (this.$.dropdown.opened) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.previous();
      } else {
        this._open();
      }
    },

    _handleDown(e) {
      if (this.$.dropdown.opened) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.next();
      } else {
        this._open();
      }
    },

    _handleTab(e) {
      if (this.$.dropdown.opened) {
        // Tab in a native select is a no-op. Emulate this.
        e.preventDefault();
        e.stopPropagation();
      }
    },

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

    _handleDropdownTap(e) {
      // async is needed so that that the click event is fired before the
      // dropdown closes (This was a bug for touch devices).
      this.async(() => {
        this.$.dropdown.close();
      }, 1);
    },

    _showDropdownTapHandler(e) {
      this._open();
    },

    _open() {
      this.$.dropdown.open();
      this.$.cursor.setCursorAtIndex(0);
      Polymer.dom.flush();
      this.$.cursor.target.focus();
    },

    _getClassIfBold(bold) {
      return bold ? 'bold-text' : '';
    },

    _computeURLHelper(host, path) {
      return '//' + host + this.getBaseUrl() + path;
    },

    _computeRelativeURL(path) {
      const host = window.location.host;
      return this._computeURLHelper(host, path);
    },

    _computeLinkURL(link) {
      if (typeof link.url === 'undefined') {
        return '';
      }
      if (link.target) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    },

    _computeLinkRel(link) {
      return link.target ? 'noopener' : null;
    },

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

    _computeDisabledClass(id, disabledIdsRecord) {
      return disabledIdsRecord.base.includes(id) ? 'disabled' : '';
    },

    _resetCursorStops() {
      Polymer.dom.flush();
      // TODO(kaspern): This is broken in shadow DOM.
      this._listElements = this.querySelectorAll('li');
    },
  });
})();