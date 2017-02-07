// Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-import-style',

    properties: {
      urls: Array,
    },

    observers: [
      '_urlsObjserver(urls, isAttached)',
    ],

    _import: function(url) {
      return new Promise(
        function(resolve, reject) {
          this.importHref(url, resolve, reject);
        }.bind(this))
        .then(function(event) {
          Polymer.dom(this.root).appendChild(event.target);
        }.bind(this));
    },

    _apply: function(name) {
      var s = document.createElement('style', 'custom-style');
      s.setAttribute('include', name);
      Polymer.dom(this.root).appendChild(s);
    },

    _urlsObjserver: function(urls, isAttached) {
      if (!isAttached) {
        return;
      }
      Promise.all(urls.map(this._import.bind(this))).then(function() {
        urls.map(function(url) { // extract file name only.
          return url.split('/').pop().split('.html')[0];
        }).forEach(this._apply.bind(this));
      }.bind(this));
    },
  });
})();
