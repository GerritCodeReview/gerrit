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

  var HLJS_PATH = 'bower_components/highlightjs/highlight.min.js';
  var LIB_ROOT_PATTERN = /(.+\/)elements\/gr-app\.html/;

  Polymer({
    is: 'gr-syntax-lib-loader',

    properties: {
      _state: {
        type: Object,

        // NOTE: intended singleton.
        value: {
          loaded: false,
          loading: false,
          callbacks: [],
        },
      }
    },

    get: function() {
      return new Promise(function(resolve) {
        // If the lib is totally loaded, resolve immediately.
        if (this._state.loaded) {
          resolve(this._getHighlightLib());
          return;
        }

        // If the library is not currently being loaded, then start loading it.
        if (!this._state.loading) {
          this._state.loading = true;
          this._loadHLJS().then(this._onLibLoaded.bind(this));
        }

        this._state.callbacks.push(resolve);
      }.bind(this));
    },

    _onLibLoaded: function() {
      var lib = this._getHighlightLib();
      this._state.loaded = true;
      this._state.loading = false;
      this._state.callbacks.forEach(function(cb) { cb(lib); });
      this._state.callbacks = [];
    },

    _getHighlightLib: function() {
      return window.hljs;
    },

    _configureHighlightLib: function() {
      this._getHighlightLib().configure(
          {classPrefix: 'gr-diff gr-syntax gr-syntax-'});
    },

    _getLibRoot: function() {
      if (this._cachedLibRoot) { return this._cachedLibRoot; }

      return this._cachedLibRoot = document.head
          .querySelector('link[rel=import][href$="gr-app.html"]')
          .href
          .match(LIB_ROOT_PATTERN)[1];
    },
    _cachedLibRoot: null,

    _loadHLJS: function() {
      return new Promise(function(resolve) {
        var script = document.createElement('script');
        script.src = this._getLibRoot() + HLJS_PATH;
        script.onload = function() {
          this._configureHighlightLib();
          resolve();
        }.bind(this);
        Polymer.dom(document.head).appendChild(script);
      }.bind(this));
    },
  });
})();
