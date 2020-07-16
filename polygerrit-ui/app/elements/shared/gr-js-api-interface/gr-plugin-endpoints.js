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

import {importHref} from '../../../scripts/import-href.js';

/** @constructor */
export class GrPluginEndpoints {
  constructor() {
    this._endpoints = {};
    this._callbacks = {};
    this._dynamicPlugins = {};
    this._importedUrls = new Set();
    this._pluginLoaded = false;
  }

  setPluginsReady() {
    this._pluginLoaded = true;
  }

  onNewEndpoint(endpoint, callback) {
    if (!this._callbacks[endpoint]) {
      this._callbacks[endpoint] = [];
    }
    this._callbacks[endpoint].push(callback);
  }

  onDetachedEndpoint(endpoint, callback) {
    if (this._callbacks[endpoint]) {
      this._callbacks[endpoint] = this._callbacks[endpoint].filter(
          cb => cb !== callback
      );
    }
  }

  _getOrCreateModuleInfo(plugin, opts) {
    const {endpoint, slot, type, moduleName, domHook} = opts;
    const existingModule = this._endpoints[endpoint].find(
        info =>
          info.plugin === plugin &&
        info.moduleName === moduleName &&
        info.domHook === domHook &&
        info.slot === slot
    );
    if (existingModule) {
      return existingModule;
    } else {
      const newModule = {
        moduleName,
        plugin,
        pluginUrl: plugin._url,
        type,
        domHook,
        slot,
      };
      this._endpoints[endpoint].push(newModule);
      return newModule;
    }
  }

  /**
   * Register a plugin to an endpoint.
   *
   * Dynamic plugins are registered to a specific prefix, such as
   * 'change-list-header'. These plugins are then fetched by prefix to determine
   * which endpoints to dynamically add to the page.
   *
   * @param {Object} plugin
   * @param {Object} opts
   */
  registerModule(plugin, opts) {
    const {endpoint, dynamicEndpoint} = opts;
    if (dynamicEndpoint) {
      if (!this._dynamicPlugins[dynamicEndpoint]) {
        this._dynamicPlugins[dynamicEndpoint] = new Set();
      }
      this._dynamicPlugins[dynamicEndpoint].add(endpoint);
    }
    if (!this._endpoints[endpoint]) {
      this._endpoints[endpoint] = [];
    }
    const moduleInfo = this._getOrCreateModuleInfo(plugin, opts);
    // TODO: the logic below seems wrong when:
    // multiple plugins register to the same endpoint
    // one register before plugins ready
    // the other done after, then only the later one will have the callbacks
    // invoked.
    if (this._pluginLoaded && this._callbacks[endpoint]) {
      this._callbacks[endpoint].forEach(callback => callback(moduleInfo));
    }
  }

  getDynamicEndpoints(dynamicEndpoint) {
    const plugins = this._dynamicPlugins[dynamicEndpoint];
    if (!plugins) return [];
    return Array.from(plugins);
  }

  /**
   * Get detailed information about modules registered with an extension
   * endpoint.
   *
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<{
   *   moduleName: string,
   *   plugin: Plugin,
   *   pluginUrl: String,
   *   type: EndpointType,
   *   domHook: !Object
   * }>}
   */
  getDetails(name, opt_options) {
    const type = opt_options && opt_options.type;
    const moduleName = opt_options && opt_options.moduleName;
    if (!this._endpoints[name]) {
      return [];
    }
    return this._endpoints[name].filter(
        item =>
          (!type || item.type === type) &&
        (!moduleName || moduleName == item.moduleName)
    );
  }

  /**
   * Get detailed module names for instantiating at the endpoint.
   *
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<string>}
   */
  getModules(name, opt_options) {
    const modulesData = this.getDetails(name, opt_options);
    if (!modulesData.length) {
      return [];
    }
    return modulesData.map(m => m.moduleName);
  }

  /**
   * Get plugin URLs with element and module definitions.
   *
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<!URL>}
   */
  getPlugins(name, opt_options) {
    const modulesData = this.getDetails(name, opt_options);
    if (!modulesData.length) {
      return [];
    }
    return Array.from(new Set(modulesData.map(m => m.pluginUrl)));
  }

  importUrl(pluginUrl) {
    let timerId;
    return Promise
        .race([
          new Promise((resolve, reject) => {
            this._importedUrls.add(pluginUrl.href);
            importHref(pluginUrl, resolve, reject);
          }),
          // Timeout after 3s
          new Promise(r => timerId = setTimeout(r, 3000)),
        ])
        .finally(() => {
          if (timerId) clearTimeout(timerId);
        });
  }

  /**
   * Get plugin URLs with element and module definitions.
   *
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<!Promise<void>>}
   */
  getAndImportPlugins(name, opt_options) {
    return Promise.all(
        this.getPlugins(name, opt_options).map(pluginUrl => {
          if (this._importedUrls.has(pluginUrl.href)) {
            return Promise.resolve();
          }

          // TODO: we will deprecate html plugins entirely
          // for now, keep the original behavior and import
          // only for html ones
          if (pluginUrl && pluginUrl.pathname.endsWith('.html')) {
            return this.importUrl(pluginUrl);
          } else {
            return Promise.resolve();
          }
        })
    );
  }
}

// TODO(dmfilippov): Convert to service and add to appContext
export let pluginEndpoints = new GrPluginEndpoints();
export function _testOnly_resetEndpoints() {
  pluginEndpoints = new GrPluginEndpoints();
}
