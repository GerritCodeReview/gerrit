/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import {appContext} from '../../../services/app-context';
import {importHref} from '../../../scripts/import-href';
import {
  PLUGIN_LOADING_TIMEOUT_MS,
  PRELOADED_PROTOCOL,
  getPluginNameFromUrl,
} from './gr-api-utils';
import {Plugin} from './gr-public-js-api';
import {getBaseUrl} from '../../../utils/url-util';
import {getPluginEndpoints} from './gr-plugin-endpoints';
import {PluginApi} from '../../../api/plugin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {hasOwnProperty} from '../../../utils/common-util';
import {ShowAlertEventDetail} from '../../../types/events';

enum PluginState {
  /** State that indicates the plugin is pending to be loaded. */
  PENDING = 'PENDING',
  /** State that indicates the plugin is already loaded. */
  LOADED = 'LOADED',
  /** State that indicates the plugin failed to load. */
  LOAD_FAILED = 'LOAD_FAILED',
}

interface PluginObject {
  name: string;
  url: string;
  state: PluginState;
  plugin: PluginApi | null;
}

interface PluginOption {
  sync?: boolean;
}

export interface PluginOptionMap {
  [path: string]: PluginOption;
}

type GerritScriptElement = HTMLScriptElement & {
  __importElement: HTMLScriptElement;
};

type PluginCallback = (plugin: PluginApi) => void;

interface PluginCallbackMap {
  [name: string]: PluginCallback;
}

interface GerritGlobal {
  _preloadedPlugins?: PluginCallbackMap;
}

// Prefix for any unrecognized plugin urls.
// Url should match following patterns:
// /plugins/PLUGINNAME/static/SCRIPTNAME.(html|js)
// /plugins/PLUGINNAME.(js|html)
const UNKNOWN_PLUGIN_PREFIX = '__$$__';

// Current API version for Plugin,
// plugins with incompatible version will not be loaded.
const API_VERSION = '0.1';

/**
 * PluginLoader, responsible for:
 *
 * Loading all plugins and handling errors etc.
 * Recording plugin state.
 * Reporting on plugin loading status.
 * Retrieve plugin.
 * Check plugin status and if all plugins loaded.
 */
export class PluginLoader {
  _pluginListLoaded = false;

  _plugins = new Map<string, PluginObject>();

  _reporting: ReportingService | null = null;

  // Promise that resolves when all plugins loaded
  _loadingPromise: Promise<void> | null = null;

  // Resolver to resolve _loadingPromise once all plugins loaded
  _loadingResolver: (() => void) | null = null;

  _getReporting() {
    if (!this._reporting) {
      this._reporting = appContext.reportingService;
    }
    return this._reporting;
  }

  /**
   * Use the plugin name or use the full url if not recognized.
   */
  _getPluginKeyFromUrl(url: string) {
    return getPluginNameFromUrl(url) || `${UNKNOWN_PLUGIN_PREFIX}${url}`;
  }

  /**
   * Load multiple plugins with certain options.
   */
  loadPlugins(plugins: string[] = [], opts: PluginOptionMap = {}) {
    this._pluginListLoaded = true;

    plugins.forEach(path => {
      const url = this._urlFor(path, window.ASSETS_PATH);
      // Skip if preloaded, for bundling.
      if (this.isPluginPreloaded(url)) return;

      const pluginKey = this._getPluginKeyFromUrl(url);
      // Skip if already installed.
      if (this._plugins.has(pluginKey)) return;
      this._plugins.set(pluginKey, {
        name: pluginKey,
        url,
        state: PluginState.PENDING,
        plugin: null,
      });

      if (this._isPathEndsWith(url, '.html')) {
        this._importHtmlPlugin(path, opts && opts[path]);
      } else if (this._isPathEndsWith(url, '.js')) {
        this._loadJsPlugin(path);
      } else {
        this._failToLoad(`Unrecognized plugin path ${path}`, path);
      }
    });

    this.awaitPluginsLoaded().then(() => {
      this._getReporting().pluginsLoaded(this._getAllInstalledPluginNames());
    });
  }

  _isPathEndsWith(url: string | URL, suffix: string) {
    if (!(url instanceof URL)) {
      try {
        url = new URL(url);
      } catch (e) {
        console.warn(e);
        return false;
      }
    }

    return url.pathname && url.pathname.endsWith(suffix);
  }

  _getAllInstalledPluginNames() {
    const installedPlugins = [];
    for (const plugin of this._plugins.values()) {
      if (plugin.state === PluginState.LOADED) {
        installedPlugins.push(plugin.name);
      }
    }
    return installedPlugins;
  }

  install(
    callback: (plugin: PluginApi) => void,
    version?: string,
    src?: string
  ) {
    // HTML import polyfill adds __importElement pointing to the import tag.
    const gerritScript = document.currentScript as GerritScriptElement | null;
    const script = gerritScript?.__importElement ?? gerritScript;
    if (!src && script && script.src) {
      src = script.src;
    }
    if ((!src || src.startsWith('data:')) && script && script.baseURI) {
      src = script && script.baseURI;
    }
    if (!src) {
      this._failToLoad('Failed to determine src.');
      return;
    }
    if (version && version !== API_VERSION) {
      this._failToLoad(
        `Plugin ${src} install error: only version ${API_VERSION} is supported in PolyGerrit. ${version} was given.`,
        src
      );
      return;
    }

    const url = this._urlFor(src);
    const pluginObject = this.getPlugin(url);
    let plugin = pluginObject && pluginObject.plugin;
    if (!plugin) {
      plugin = new Plugin(url);
    }
    try {
      callback(plugin);
      this._pluginInstalled(url, plugin);
    } catch (e) {
      this._failToLoad(`${e.name}: ${e.message}`, src);
    }
  }

  // The polygerrit uses version of sinon where you can't stub getter,
  // declare it as a function here
  arePluginsLoaded() {
    // As the size of plugins is relatively small,
    // so the performance of this check should be reasonable
    if (!this._pluginListLoaded) return false;
    for (const plugin of this._plugins.values()) {
      if (plugin.state === PluginState.PENDING) return false;
    }
    return true;
  }

  _checkIfCompleted() {
    if (this.arePluginsLoaded()) {
      getPluginEndpoints().setPluginsReady();
      if (this._loadingResolver) {
        this._loadingResolver();
        this._loadingResolver = null;
        this._loadingPromise = null;
      }
    }
  }

  _timeout() {
    const pendingPlugins = [];
    for (const plugin of this._plugins.values()) {
      if (plugin.state === PluginState.PENDING) {
        this._updatePluginState(plugin.url, PluginState.LOAD_FAILED);
        this._checkIfCompleted();
        pendingPlugins.push(plugin.url);
      }
    }
    return `Timeout when loading plugins: ${pendingPlugins.join(',')}`;
  }

  _failToLoad(message: string, pluginUrl?: string) {
    // Show an alert with the error
    document.dispatchEvent(
      new CustomEvent<ShowAlertEventDetail>('show-alert', {
        detail: {
          message: `Plugin install error: ${message} from ${pluginUrl}`,
        },
      })
    );
    if (pluginUrl) this._updatePluginState(pluginUrl, PluginState.LOAD_FAILED);
    this._checkIfCompleted();
  }

  _updatePluginState(pluginUrl: string, state: PluginState): PluginObject {
    const key = this._getPluginKeyFromUrl(pluginUrl);
    if (this._plugins.has(key)) {
      this._plugins.get(key)!.state = state;
    } else {
      // Plugin is not recorded for some reason.
      console.info(`Plugin loaded separately: ${pluginUrl}`);
      this._plugins.set(key, {
        name: key,
        url: pluginUrl,
        state,
        plugin: null,
      });
    }
    return this._plugins.get(key)!;
  }

  _pluginInstalled(url: string, plugin: PluginApi) {
    const pluginObj = this._updatePluginState(url, PluginState.LOADED);
    pluginObj.plugin = plugin;
    this._getReporting().pluginLoaded(plugin.getPluginName() || url);
    console.info(`Plugin ${plugin.getPluginName() || url} installed.`);
    this._checkIfCompleted();
  }

  installPreloadedPlugins() {
    const Gerrit = window.Gerrit as GerritGlobal;
    if (!Gerrit || !Gerrit._preloadedPlugins) {
      return;
    }
    for (const name of Object.keys(Gerrit._preloadedPlugins)) {
      const callback = Gerrit._preloadedPlugins[name];
      this.install(callback, API_VERSION, PRELOADED_PROTOCOL + name);
    }
  }

  isPluginPreloaded(pathOrUrl: string) {
    const url = this._urlFor(pathOrUrl);
    const name = getPluginNameFromUrl(url);
    const Gerrit = window.Gerrit as GerritGlobal;
    if (name && Gerrit?._preloadedPlugins) {
      return hasOwnProperty(Gerrit._preloadedPlugins, name);
    } else {
      return false;
    }
  }

  /**
   * Checks if given plugin path/url is enabled or not.
   */
  isPluginEnabled(pathOrUrl: string) {
    const url = this._urlFor(pathOrUrl);
    if (this.isPluginPreloaded(url)) return true;
    const key = this._getPluginKeyFromUrl(url);
    return this._plugins.has(key);
  }

  /**
   * Returns the plugin object with a given url.
   */
  getPlugin(pathOrUrl: string) {
    const url = this._urlFor(pathOrUrl);
    const key = this._getPluginKeyFromUrl(url);
    return this._plugins.get(key);
  }

  /**
   * Checks if given plugin path/url is loaded or not.
   */
  isPluginLoaded(pathOrUrl: string): boolean {
    const url = this._urlFor(pathOrUrl);
    const key = this._getPluginKeyFromUrl(url);
    return this._plugins.has(key)
      ? this._plugins.get(key)!.state === PluginState.LOADED
      : false;
  }

  _importHtmlPlugin(pluginUrl: string, opts: PluginOption = {}) {
    const urlWithAP = this._urlFor(pluginUrl, window.ASSETS_PATH);
    const urlWithoutAP = this._urlFor(pluginUrl);
    let onerror = undefined;
    this._getReporting().reportExecution('html-plugin', {pluginUrl});
    if (urlWithAP !== urlWithoutAP) {
      onerror = () => this._loadHtmlPlugin(urlWithoutAP, opts.sync);
    }
    this._loadHtmlPlugin(urlWithAP, opts.sync, onerror);
  }

  _loadHtmlPlugin(url: string, sync?: boolean, onerror?: (e: Event) => void) {
    if (!onerror) {
      onerror = () => {
        this._failToLoad(`${url} import error`, url);
      };
    }

    importHref(url, () => {}, onerror, !sync);
  }

  _loadJsPlugin(pluginUrl: string) {
    const urlWithAP = this._urlFor(pluginUrl, window.ASSETS_PATH);
    const urlWithoutAP = this._urlFor(pluginUrl);
    let onerror = undefined;
    if (urlWithAP !== urlWithoutAP) {
      onerror = () => this._createScriptTag(urlWithoutAP);
    }

    this._createScriptTag(urlWithAP, onerror);
  }

  _createScriptTag(url: string, onerror?: OnErrorEventHandler) {
    if (!onerror) {
      onerror = () => this._failToLoad(`${url} load error`, url);
    }

    const el = document.createElement('script');
    el.defer = true;
    el.setAttribute('src', url);
    // no credentials to send when fetch plugin js
    // and this will help provide more meaningful error than
    // 'Script error.'
    el.setAttribute('crossorigin', 'anonymous');
    el.onerror = onerror;
    return document.body.appendChild(el);
  }

  _urlFor(pathOrUrl: string, assetsPath?: string): string {
    // theme is per host, should always load from assetsPath
    const isThemeFile =
      pathOrUrl.endsWith('static/gerrit-theme.html') ||
      pathOrUrl.endsWith('static/gerrit-theme.js');
    const shouldTryLoadFromAssetsPathFirst = !isThemeFile && assetsPath;
    if (
      pathOrUrl.startsWith(PRELOADED_PROTOCOL) ||
      pathOrUrl.startsWith('http')
    ) {
      // Plugins are loaded from another domain or preloaded.
      if (
        pathOrUrl.includes(location.host) &&
        shouldTryLoadFromAssetsPathFirst &&
        assetsPath
      ) {
        // if is loading from host server, try replace with cdn when assetsPath provided
        return pathOrUrl.replace(location.origin, assetsPath);
      }
      return pathOrUrl;
    }

    if (!pathOrUrl.startsWith('/')) {
      pathOrUrl = '/' + pathOrUrl;
    }

    if (shouldTryLoadFromAssetsPathFirst && assetsPath) {
      return assetsPath + pathOrUrl;
    }

    return window.location.origin + getBaseUrl() + pathOrUrl;
  }

  awaitPluginsLoaded() {
    // Resolve if completed.
    this._checkIfCompleted();

    if (this.arePluginsLoaded()) {
      return Promise.resolve();
    }
    if (!this._loadingPromise) {
      // specify window here so that TS pulls the correct setTimeout method
      // if window is not specified, then the function is pulled from node
      // and the return type is NodeJS.Timeout object
      let timerId: number;
      this._loadingPromise = Promise.race([
        new Promise<void>(resolve => (this._loadingResolver = resolve)),
        new Promise(
          (_, reject) =>
            (timerId = window.setTimeout(() => {
              reject(new Error(this._timeout()));
            }, PLUGIN_LOADING_TIMEOUT_MS))
        ),
      ]).finally(() => {
        if (timerId) clearTimeout(timerId);
      }) as Promise<void>;
    }
    return this._loadingPromise;
  }
}

// TODO(dmfilippov): Convert to service and add to appContext
let pluginLoader = new PluginLoader();
export function _testOnly_resetPluginLoader() {
  pluginLoader = new PluginLoader();
  return pluginLoader;
}

export function getPluginLoader() {
  return pluginLoader;
}
