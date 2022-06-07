/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getAppContext} from '../../../services/app-context';
import {
  PLUGIN_LOADING_TIMEOUT_MS,
  getPluginNameFromUrl,
  isThemeFile,
  THEME_JS,
} from './gr-api-utils';
import {Plugin} from './gr-public-js-api';
import {getBaseUrl} from '../../../utils/url-util';
import {getPluginEndpoints} from './gr-plugin-endpoints';
import {PluginApi} from '../../../api/plugin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {fireAlert} from '../../../utils/event-util';

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

// Prefix for any unrecognized plugin urls.
// Url should match following patterns:
// /plugins/PLUGINNAME/static/SCRIPTNAME.js
// /plugins/PLUGINNAME.js
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

  private instanceId?: string;

  _getReporting() {
    if (!this._reporting) {
      this._reporting = getAppContext().reportingService;
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
  loadPlugins(plugins: string[] = [], instanceId?: string) {
    this.instanceId = instanceId;
    this._pluginListLoaded = true;

    plugins.forEach(path => {
      const url = this._urlFor(path, window.ASSETS_PATH);
      const pluginKey = this._getPluginKeyFromUrl(url);
      // Skip if already installed.
      if (this._plugins.has(pluginKey)) return;
      this._plugins.set(pluginKey, {
        name: pluginKey,
        url,
        state: PluginState.PENDING,
        plugin: null,
      });

      if (this._isPathEndsWith(url, '.js')) {
        this._loadJsPlugin(path);
      } else {
        this._failToLoad(`Unrecognized plugin path ${path}`, path);
      }
    });

    this.awaitPluginsLoaded().then(() => {
      const loaded = this.getPluginsByState(PluginState.LOADED);
      const failed = this.getPluginsByState(PluginState.LOAD_FAILED);
      this._getReporting().pluginsLoaded(loaded.map(p => p.name));
      this._getReporting().pluginsFailed(failed.map(p => p.name));
    });
  }

  _isPathEndsWith(url: string | URL, suffix: string) {
    if (!(url instanceof URL)) {
      try {
        url = new URL(url);
      } catch (e: unknown) {
        this._getReporting().error(new Error('url parse error'), undefined, e);
        return false;
      }
    }

    return url.pathname && url.pathname.endsWith(suffix);
  }

  private getPluginsByState(state: PluginState) {
    return [...this._plugins.values()].filter(p => p.state === state);
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
    } catch (e: unknown) {
      if (e instanceof Error) {
        this._failToLoad(`${e.name}: ${e.message}`, src);
      } else {
        this._getReporting().error(
          new Error('plugin callback error'),
          undefined,
          e
        );
      }
    }
  }

  arePluginsLoaded() {
    if (!this._pluginListLoaded) return false;
    return this.getPluginsByState(PluginState.PENDING).length === 0;
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
    const pending = this.getPluginsByState(PluginState.PENDING);
    for (const plugin of pending) {
      this._updatePluginState(plugin.url, PluginState.LOAD_FAILED);
    }
    this._checkIfCompleted();
    const errorMessage = `Timeout when loading plugins: ${pending
      .map(p => p.name)
      .join(',')}`;
    fireAlert(document, errorMessage);
    return errorMessage;
  }

  _failToLoad(message: string, pluginUrl?: string) {
    // Show an alert with the error
    fireAlert(document, `Plugin install error: ${message} from ${pluginUrl}`);
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
    console.debug(`Plugin ${key} ${state}`);
    return this._plugins.get(key)!;
  }

  _pluginInstalled(url: string, plugin: PluginApi) {
    const pluginObj = this._updatePluginState(url, PluginState.LOADED);
    pluginObj.plugin = plugin;
    this._getReporting().pluginLoaded(plugin.getPluginName() || url);
    this._checkIfCompleted();
  }

  /**
   * Checks if given plugin path/url is enabled or not.
   */
  isPluginEnabled(pathOrUrl: string) {
    const url = this._urlFor(pathOrUrl);
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
    if (isThemeFile(pathOrUrl)) {
      if (assetsPath && this.instanceId) {
        return `${assetsPath}/hosts/${this.instanceId}${THEME_JS}`;
      }
      return window.location.origin + getBaseUrl() + THEME_JS;
    }

    if (pathOrUrl.startsWith('http')) {
      // Plugins are loaded from another domain or preloaded.
      if (pathOrUrl.includes(location.host) && assetsPath) {
        // if is loading from host server, try replace with cdn when assetsPath provided
        return pathOrUrl.replace(location.origin, assetsPath);
      }
      return pathOrUrl;
    }

    if (!pathOrUrl.startsWith('/')) {
      pathOrUrl = '/' + pathOrUrl;
    }
    if (assetsPath) {
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
