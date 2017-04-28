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
    is: 'gr-external-style',

    properties: {
      name: String,
    },

    _import: function(url) {
      return new Promise((resolve, reject) => {
        this.importHref(url, resolve, reject);
      });
    },

    _applyStyle: function(name) {
      const s = document.createElement('style', 'custom-style');
      s.setAttribute('include', name);
      Polymer.dom(this.root).appendChild(s);
    },

    ready: function() {
      Gerrit.awaitPluginsLoaded().then(_=> {
        const sharedStyles = Gerrit._styleModules[this.name];
        if (sharedStyles) {
          const {pluginUrls, moduleNames} = sharedStyles.reduce(
            ({pluginUrls, moduleNames}, {pluginUrl, moduleName}) => {
              if (!pluginUrls.includes(pluginUrl)) {
                pluginUrls.push(pluginUrl);
              }
              moduleNames.push(moduleName);
              return {pluginUrls, moduleNames};
            }, {pluginUrls: [], moduleNames: []});
          Promise.all(pluginUrls.map(url=>this._import(url)))
            .then(_=>moduleNames.map(name=>this._applyStyle(name)));
        }
      });
    },
  });
})();
