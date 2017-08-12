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

  // Date cutoff is one day:
  const DRAFT_MAX_AGE = 24 * 60 * 60 * 1000;

  // Clean up old entries no more frequently than one day.
  const CLEANUP_THROTTLE_INTERVAL = 24 * 60 * 60 * 1000;

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
      this._cleanupDrafts();
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

    getPreferences() {
      return this._getObject('localPrefs');
    },

    savePreferences(localPrefs) {
      this._setObject('localPrefs', localPrefs || null);
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

    _cleanupDrafts() {
      // Throttle cleanup to the throttle interval.
      if (this._lastCleanup &&
          Date.now() - this._lastCleanup < CLEANUP_THROTTLE_INTERVAL) {
        return;
      }
      this._lastCleanup = Date.now();

      let draft;
      for (const key in this._storage) {
        if (key.startsWith('draft:')) {
          draft = this._getObject(key);
          if (Date.now() - draft.updated > DRAFT_MAX_AGE) {
            this._storage.removeItem(key);
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
