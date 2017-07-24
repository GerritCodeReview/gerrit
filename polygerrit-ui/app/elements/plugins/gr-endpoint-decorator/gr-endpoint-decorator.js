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
    is: 'gr-endpoint-decorator',

    properties: {
      name: String,
    },

    _import(url) {
      return new Promise((resolve, reject) => {
        this.importHref(url, resolve, reject);
      });
    },

    _initDecoration(name, plugin) {
      const el = document.createElement(name);
      el.plugin = plugin;
      el.content = this.getContentChildren()[0];
      return Polymer.dom(this.root).appendChild(el);
    },

    _initReplacement(name, plugin) {
      this.getContentChildren().forEach(node => node.remove());
      const el = document.createElement(name);
      el.plugin = plugin;
      return Polymer.dom(this.root).appendChild(el);
    },

    ready() {
      Gerrit.awaitPluginsLoaded().then(() => Promise.all(
          Gerrit._endpoints.getPlugins(this.name).map(
              pluginUrl => this._import(pluginUrl)))
      ).then(() => {
        const modulesData = Gerrit._endpoints.getDetails(this.name);
        for (const {moduleName, plugin, type} of modulesData) {
          switch (type) {
            case 'decorate':
              this._initDecoration(moduleName, plugin);
              break;
            case 'replace':
              this._initReplacement(moduleName, plugin);
              break;
          }
        }
      });
    },
  });
})();
