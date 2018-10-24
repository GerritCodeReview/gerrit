/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  const INIT_PROPERTIES_TIMEOUT_MS = 10000;

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

    async _initDecoration(name, plugin) {
      const el = document.createElement(name);
      const decoratedEl = await this._initProperties(el, plugin,
          this.getContentChildren().find(
              el => el.nodeName !== 'GR-ENDPOINT-PARAM'));
      return this._appendChild(decoratedEl);
    },

    async _initReplacement(name, plugin) {
      this.getContentChildNodes()
          .filter(node => node.nodeName !== 'GR-ENDPOINT-PARAM')
          .forEach(node => node.remove());
      const el = document.createElement(name);
      const decoratedEl = await this._initProperties(el, plugin);
      return this._appendChild(decoratedEl);
    },

    _getEndpointParams() {
      return Polymer.dom(this).querySelectorAll('gr-endpoint-param');
    },

    /**
     * @param {!Element} el
     * @param {!Object} plugin
     * @param {!Element=} opt_content
     * @return {!Promise<Element>}
     */
    async _initProperties(el, plugin, opt_content) {
      el.plugin = plugin;
      if (opt_content) {
        el.content = opt_content;
      }
      const expectProperties = this._getEndpointParams().map(async paramEl => {
        const helper = plugin.attributeHelper(paramEl);
        const paramName = paramEl.getAttribute('name');
        await helper.get('value');
        helper.bind('value',
            value => plugin.attributeHelper(el).set(paramName, value));
      });
      let timeoutId;
      const timeout = new Promise(
        resolve => timeoutId = setTimeout(() => {
          console.warn(
              'Timeout waiting for endpoint properties initialization: ' +
              `plugin ${plugin.getPluginName()}, endpoint ${this.name}`);
        }, INIT_PROPERTIES_TIMEOUT_MS));
      await Promise.race([timeout, Promise.all(expectProperties)]);
      clearTimeout(timeoutId);
      return el;
    },

    _appendChild(el) {
      return Polymer.dom(this.root).appendChild(el);
    },

    async _initModule({moduleName, plugin, type, domHook}) {
      let initPromise;
      switch (type) {
        case 'decorate':
          initPromise = this._initDecoration(moduleName, plugin);
          break;
        case 'replace':
          initPromise = this._initReplacement(moduleName, plugin);
          break;
      }
      if (!initPromise) {
        console.warn('Unable to initialize module' +
            `${moduleName} from ${plugin.getPluginName()}`);
      }
      const el = await initPromise;
      domHook.handleInstanceAttached(el);
      this._domHooks.set(el, domHook);
    },

    async ready() {
      Gerrit._endpoints.onNewEndpoint(this.name, this._initModule.bind(this));
      await Gerrit.awaitPluginsLoaded();
      await Promise.all(
          Gerrit._endpoints.getPlugins(this.name).map(
              pluginUrl => this._import(pluginUrl)));
      Gerrit._endpoints
          .getDetails(this.name)
          .forEach(this._initModule, this);
    },
  });
})();
