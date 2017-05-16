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
      items: Array,
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
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    attached() {
      this.$.restAPI.getConfig().then(cfg => {
        this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);
      });
    },

    _handleDropdownTap(e) {
      // async is needed so that that the click event is fired before the
      // dropdown closes (This was a bug for touch devices).
      this.async(() => {
        this.$.dropdown.close();
      }, 1);
    },

    _showDropdownTapHandler(e) {
      this.$.dropdown.open();
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
      const item = this.items.find(item => {
        return item.id === id;
      });
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
  });
})();
