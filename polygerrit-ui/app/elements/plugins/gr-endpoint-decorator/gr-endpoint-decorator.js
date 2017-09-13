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
      /** @type {!Map} */
      _domHooks: {
        type: Map,
        value() { return new Map(); },
      },
    },

    detached() {
      for (const [el, domHook] of this._domHooks) {
        domHook.handleInstanceDetached(el);
      }
    },

    _import(url) {
      return new Promise((resolve, reject) => {
        this.importHref(url, resolve, reject);
      });
    },

    _initDecoration(name, plugin) {
      const el = document.createElement(name);
      this._initProperties(el, plugin, this.getContentChildren().find(
          el => el.nodeName != 'GR-ENDPOINT-PARAM'));
      this._appendChild(el);
      return el;
    },

    _initReplacement(name, plugin) {
      this.getContentChildNodes().forEach(node => node.remove());
      const el = document.createElement(name);
      this._initProperties(el, plugin);
      this._appendChild(el);
      return el;
    },

    _getEndpointParams() {
      return Polymer.dom(this).querySelectorAll('gr-endpoint-param').map(el => {
        return {name: el.getAttribute('name'), value: el.value};
      });
    },

    /**
     * @param {!Element} el
     * @param {!Object} plugin
     * @param {!Element=} opt_content
     */
    _initProperties(el, plugin, opt_content) {
      el.plugin = plugin;
      if (opt_content) {
        el.content = opt_content;
      }
      for (const {name, value} of this._getEndpointParams()) {
        el[name] = value;
      }
    },

    _appendChild(el) {
      Polymer.dom(this.root).appendChild(el);
    },

    _initModule({moduleName, plugin, type, domHook}) {
      let el;
      switch (type) {
        case 'decorate':
          el = this._initDecoration(moduleName, plugin);
          break;
        case 'replace':
          el = this._initReplacement(moduleName, plugin);
          break;
      }
      if (el) {
        domHook.handleInstanceAttached(el);
      }
      this._domHooks.set(el, domHook);
    },

    ready() {
      Gerrit._endpoints.onNewEndpoint(this.name, this._initModule.bind(this));
      Gerrit.awaitPluginsLoaded().then(() => Promise.all(
          Gerrit._endpoints.getPlugins(this.name).map(
              pluginUrl => this._import(pluginUrl)))
      ).then(() =>
        Gerrit._endpoints
            .getDetails(this.name)
            .forEach(this._initModule, this)
      );
    },
  });
})();
