/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {HookApi, PluginElement} from '../../../api/hook';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Callback = (value: any) => void;

export interface ModuleInfo {
  moduleName: string;
  plugin: PluginApi;
  pluginUrl?: URL;
  type?: EndpointType;
  domHook?: HookApi<PluginElement>;
  slot?: string;
}

/**
 * Plugin-provided custom components can affect content in extension
 * points using one of following methods:
 * - DECORATE: custom component is set with `content` attribute and may
 *   decorate (e.g. style) DOM element.
 * - REPLACE: contents of extension point are replaced with the custom
 *   component.
 */
export enum EndpointType {
  DECORATE = 'decorate',
  REPLACE = 'replace',
}

interface Options {
  endpoint: string;
  dynamicEndpoint?: string;
  slot?: string;
  type?: EndpointType;
  moduleName?: string;
  domHook?: HookApi<PluginElement>;
}

export class GrPluginEndpoints {
  private readonly _endpoints = new Map<string, ModuleInfo[]>();

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private readonly _callbacks = new Map<string, ((value: any) => void)[]>();

  private readonly _dynamicPlugins = new Map<string, Set<string>>();

  private pluginLoaded = false;

  setPluginsReady() {
    this.pluginLoaded = true;
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

  _getOrCreateModuleInfo(plugin: PluginApi, opts: Options): ModuleInfo {
    const {endpoint, slot, type, moduleName, domHook} = opts;
    const existingModule = this._endpoints
      .get(endpoint)!
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
      this._endpoints.get(endpoint)!.push(newModule);
      return newModule;
    }
  }

  /**
   * Register a plugin to an endpoint.
   *
   * Dynamic plugins are registered to a specific prefix, such as
   * 'change-list-header'. These plugins are then fetched by prefix to determine
   * which endpoints to dynamically add to the page.
   */
  registerModule(plugin: PluginApi, opts: Options) {
    const endpoint = opts.endpoint;
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
    if (this.pluginLoaded && this._callbacks.has(endpoint)) {
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
  getDetails(name: string): ModuleInfo[] {
    return (this._endpoints.get(name) ?? []).sort((m1, m2) =>
      m1.plugin.getPluginName().localeCompare(m2.plugin.getPluginName())
    );
  }
}
