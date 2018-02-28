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

  const HLJS_PATH = 'bower_components/prism/prism.js';
  const LIB_ROOT_PATTERN = /(.+\/)elements\/gr-app\.html/;

  Polymer({
    is: 'gr-syntax-lib-loader',

    properties: {
      _state: {
        type: Object,

        // NOTE: intended singleton.
        value: {
          configured: false,
          loading: false,
          callbacks: [],
        },
      },
    },

    get() {
      return new Promise((resolve, reject) => {
        // If the lib is totally loaded, resolve immediately.
        if (this._getHighlightLib()) {
          resolve(this._getHighlightLib());
          return;
        }

        // If the library is not currently being loaded, then start loading it.
        if (!this._state.loading) {
          this._state.loading = true;
          this._loadHLJS().then(this._onLibLoaded.bind(this)).catch(reject);
        }

        this._state.callbacks.push(resolve);
      });
    },

    _onLibLoaded() {
      const lib = this._getHighlightLib();
      this._state.loading = false;
      for (const cb of this._state.callbacks) {
        cb(lib);
      }
      this._state.callbacks = [];
    },

    _getHighlightLib() {
      const lib = window.Prism;
      if (lib && !this._state.configured) {
        this._state.configured = true;

        //lib.configure({classPrefix: 'gr-diff gr-syntax gr-syntax-'});
      }
      return lib;
    },

    _getLibRoot() {
      if (this._cachedLibRoot) { return this._cachedLibRoot; }

      const appLink = document.head
        .querySelector('link[rel=import][href$="gr-app.html"]');

      if (!appLink) { return null; }

      return this._cachedLibRoot = appLink
          .href
          .match(LIB_ROOT_PATTERN)[1];
    },
    _cachedLibRoot: null,

    _loadHLJS() {
      return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        const src = this._getHLJSUrl();

        if (!src) {
          reject(new Error('Unable to load blank HLJS url.'));
          return;
        }

        script.src = src;
        script.onload = resolve;
        script.onerror = reject;
        Polymer.dom(document.head).appendChild(script);
      });
    },

    _getHLJSUrl() {
      const root = this._getLibRoot();
      if (!root) { return null; }
      return root + HLJS_PATH;
    },
  });
})();
