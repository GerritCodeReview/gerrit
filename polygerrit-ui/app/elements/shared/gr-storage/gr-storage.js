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
  var DRAFT_MAX_AGE = 24*60*60*1000;

  Polymer({
    is: 'gr-storage',

    properties: {
      _storage: {
        type: Object,
        value: function() {
          return window.localStorage;
        },
      },
    },

    getDraft: function(changeNum, patchNum, path, line) {
      this._cleanupDrafts();
      return this._getObject(
          this._getDraftKey(changeNum, patchNum, path, line));
    },

    setDraft: function(changeNum, patchNum, path, line, message) {
      var key = this._getDraftKey(changeNum, patchNum, path, line);
      this._setObject(key, {message: message, updated: Date.now()});
    },

    eraseDraft: function(changeNum, patchNum, path, line) {
      var key = this._getDraftKey(changeNum, patchNum, path, line);
      this._storage.removeItem(key);
    },

    _getDraftKey: function(changeNum, patchNum, path, line) {
      return ['draft', changeNum, patchNum, path, line].join(':');
    },

    _cleanupDrafts: function() {
      var draft;
      for (var key in this._storage) {
        if (key.indexOf('draft:') === 0) {
          draft = this._getObject(key);
          if (Date.now() - draft.updated > DRAFT_MAX_AGE) {
            this._storage.removeItem(key);
          }
        }
      }
    },

    _getObject: function(key) {
      var serial = this._storage.getItem(key);
      if (!serial) { return null; }
      return JSON.parse(serial);
    },

    _setObject: function(key, obj) {
      this._storage.setItem(key, JSON.stringify(obj));
    },
  });
})();
