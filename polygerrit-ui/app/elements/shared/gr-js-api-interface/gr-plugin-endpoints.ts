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

import {importHref} from '../../../scripts/import-href';

type Callback = (value: any) => void;

interface ModuleInfo {
  moduleName: string;
  // TODO(brohlfs): Convert type to GrPlugin.
  plugin: any;
  pluginUrl: URL;
  type?: string;
  // TODO(brohlfs): Convert type to GrDomHook.
  domHook: any;
  slot?: string;
}

interface Options {
  endpoint?: string;
  dynamicEndpoint?: string;
  slot?: string;
  type?: string;
  moduleName?: string;
  // TODO(brohlfs): Convert type to GrDomHook.
  domHook?: any;
}

export class GrPluginEndpoints {
  _endpoints = new Map<string, ModuleInfo[]>();

  _callbacks = new Map<string, ((value: any) => void)[]>();

  _dynamicPlugins = new Map<string, Set<string>>();

  _importedUrls = new Set();

  _pluginLoaded = false;

  constructor() {}

  setPluginsReady() {
    this._pluginLoaded = true;
  }

  onNewEndpoint(endpoint: string, callback: Callback) {
    if (!this._callbacks.has(endpoint)) {
      this._callbacks.set(endpoint, []);
    }
    this._callbacks.get(endpoint)!.push(callback);
  }

  onDetachedEndpoint(endpoint: string, callback: Callback) {
    if (this._callbacks.has(endpoint)) {
      const filteredCallbacks = this._callbacks
        .get(endpoint)!
        .filter((cb: Callback) => cb !== callback);
      this._callbacks.set(endpoint, filteredCallbacks);
    }
  }

  _getOrCreateModuleInfo(plugin: any, opts: Options): ModuleInfo {
    const {endpoint, slot, type, moduleName, domHook} = opts;
    const existingModule = this._endpoints
      .get(endpoint!)!
      .find(
        (info: ModuleInfo) =>
          info.plugin === plugin &&
          info.moduleName === moduleName &&
          info.domHook === domHook &&
          info.slot === slot
      );
    if (existingModule) {
      return existingModule;
    } else {
      const newModule: ModuleInfo = {
        moduleName: moduleName!,
        plugin,
        pluginUrl: plugin._url,
        type,
        domHook,
        slot,
      };
      this._endpoints.get(endpoint!)!.push(newModule);
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
  registerModule(plugin: any, opts: Options) {
    const endpoint = opts.endpoint!;
    const dynamicEndpoint = opts.dynamicEndpoint;
    if (dynamicEndpoint) {
      if (!this._dynamicPlugins.has(dynamicEndpoint)) {
        this._dynamicPlugins.set(dynamicEndpoint, new Set());
      }
      this._dynamicPlugins.get(dynamicEndpoint)!.add(endpoint);
    }
    if (!this._endpoints.has(endpoint)) {
      this._endpoints.set(endpoint, []);
    }
    const moduleInfo = this._getOrCreateModuleInfo(plugin, opts);
    // TODO: the logic below seems wrong when:
    // multiple plugins register to the same endpoint
    // one register before plugins ready
    // the other done after, then only the later one will have the callbacks
    // invoked.
    if (this._pluginLoaded && this._callbacks.has(endpoint)) {
      this._callbacks.get(endpoint)!.forEach(callback => callback(moduleInfo));
    }
  }

  getDynamicEndpoints(dynamicEndpoint: string): string[] {
    const plugins = this._dynamicPlugins.get(dynamicEndpoint);
    if (!plugins) return [];
    return Array.from(plugins);
  }

  /**
   * Get detailed information about modules registered with an extension
   * endpoint.
   */
  getDetails(name: string, options?: Options): ModuleInfo[] {
    const type = options && options.type;
    const moduleName = options && options.moduleName;
    if (!this._endpoints.has(name)) {
      return [];
    } else {
      return this._endpoints
        .get(name)!
        .filter(
          (item: ModuleInfo) =>
            (!type || item.type === type) &&
            (!moduleName || moduleName === item.moduleName)
        );
    }
  }

  /**
   * Get detailed module names for instantiating at the endpoint.
   */
  getModules(name: string, options?: Options): string[] {
    const modulesData = this.getDetails(name, options);
    if (!modulesData.length) {
      return [];
    }
    return modulesData.map(m => m.moduleName);
  }

  /**
   * Get plugin URLs with element and module definitions.
   */
  getPlugins(name: string, options?: Options): URL[] {
    const modulesData = this.getDetails(name, options);
    if (!modulesData.length) {
      return [];
    }
    return Array.from(new Set(modulesData.map(m => m.pluginUrl)));
  }

  importUrl(pluginUrl: URL) {
    let timerId: any;
    return Promise.race([
      new Promise((resolve, reject) => {
        this._importedUrls.add(pluginUrl.href);
        importHref(pluginUrl.href, resolve, reject);
      }),
      // Timeout after 3s
      new Promise(r => (timerId = setTimeout(r, 3000))),
    ]).finally(() => {
      if (timerId) clearTimeout(timerId);
    });
  }

  /**
   * Get plugin URLs with element and module definitions.
   */
  getAndImportPlugins(name: string, options?: Options) {
    return Promise.all(
      this.getPlugins(name, options).map(pluginUrl => {
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
