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

  // Date cutoff is one day:
  const CLEANUP_MAX_AGE = 24 * 60 * 60 * 1000;

  // Clean up old entries no more frequently than one day.
  const CLEANUP_THROTTLE_INTERVAL = 24 * 60 * 60 * 1000;

  const CLEANUP_PREFIXES = [
    'draft:',
    'editablecontent:',
  ];

  Polymer({
    is: 'gr-storage',

    properties: {
      _lastCleanup: Number,
      /** @type {?Storage} */
      _storage: {
        type: Object,
        value() {
          return window.localStorage;
        },
      },
      _exceededQuota: {
        type: Boolean,
        value: false,
      },
    },

    getDraftComment(location) {
      this._cleanupItems();
      return this._getObject(this._getDraftKey(location));
    },

    setDraftComment(location, message) {
      const key = this._getDraftKey(location);
      this._setObject(key, {message, updated: Date.now()});
    },

    eraseDraftComment(location) {
      const key = this._getDraftKey(location);
      this._storage.removeItem(key);
    },

    getEditableContentItem(key) {
      this._cleanupItems();
      return this._getObject(this._getEditableContentKey(key));
    },

    setEditableContentItem(key, message) {
      this._setObject(this._getEditableContentKey(key),
          {message, updated: Date.now()});
    },

    eraseEditableContentItem(key) {
      this._storage.removeItem(this._getEditableContentKey(key));
    },

    _getDraftKey(location) {
      const range = location.range ?
        `${location.range.start_line}-${location.range.start_character}` +
              `-${location.range.end_character}-${location.range.end_line}` :
        null;
      let key = ['draft', location.changeNum, location.patchNum, location.path,
        location.line || ''].join(':');
      if (range) {
        key = key + ':' + range;
      }
      return key;
    },

    _getEditableContentKey(key) {
      return `editablecontent:${key}`;
    },

    _cleanupItems() {
      // Throttle cleanup to the throttle interval.
      if (this._lastCleanup &&
          Date.now() - this._lastCleanup < CLEANUP_THROTTLE_INTERVAL) {
        return;
      }
      this._lastCleanup = Date.now();

      let item;
      for (const key in this._storage) {
        if (!this._storage.hasOwnProperty(key)) { continue; }
        for (const prefix of CLEANUP_PREFIXES) {
          if (key.startsWith(prefix)) {
            item = this._getObject(key);
            if (Date.now() - item.updated > CLEANUP_MAX_AGE) {
              this._storage.removeItem(key);
            }
            break;
          }
        }
      }
    },

    _getObject(key) {
      const serial = this._storage.getItem(key);
      if (!serial) { return null; }
      return JSON.parse(serial);
    },

    _setObject(key, obj) {
      if (this._exceededQuota) { return; }
      try {
        this._storage.setItem(key, JSON.stringify(obj));
      } catch (exc) {
        // Catch for QuotaExceededError and disable writes on local storage the
        // first time that it occurs.
        if (exc.code === 22) {
          this._exceededQuota = true;
          console.warn('Local storage quota exceeded: disabling');
          return;
        } else {
          throw exc;
        }
      }
    },
  });
})();
