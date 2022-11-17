/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  PLUGIN_LOADING_TIMEOUT_MS,
  getPluginNameFromUrl,
  isThemeFile,
  THEME_JS,
} from './gr-api-utils';
import {Plugin} from './gr-public-js-api';
import {getBaseUrl} from '../../../utils/url-util';
import {GrPluginEndpoints} from './gr-plugin-endpoints';
import {PluginApi} from '../../../api/plugin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {fireAlert} from '../../../utils/event-util';
import {JsApiService} from './gr-js-api-types';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {Finalizable} from '../../../services/registry';
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {Gerrit} from '../../../api/gerrit';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {iconStyles} from '../../../styles/gr-icon-styles';
import {GrJsApiInterface} from './gr-js-api-interface-element';
import {define} from '../../../models/dependency';
import {modalStyles} from '../../../styles/gr-modal-styles';

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

export const pluginLoaderToken = define<PluginLoader>('plugin-loader');

/**
 * PluginLoader, responsible for:
 *
 * Loading all plugins and handling errors etc.
 * Recording plugin state.
 * Reporting on plugin loading status.
 * Retrieve plugin.
 * Check plugin status and if all plugins loaded.
 */
export class PluginLoader implements Gerrit, Finalizable {
  public readonly styles = {
    font: fontStyles,
    form: formStyles,
    icon: iconStyles,
    menuPage: menuPageStyles,
    spinner: spinnerStyles,
    subPage: subpageStyles,
    table: tableStyles,
    modal: modalStyles,
  };

  private pluginListLoaded = false;

  private plugins = new Map<string, PluginObject>();

  // Promise that resolves when all plugins loaded
  private loadingPromise: Promise<void> | null = null;

  // Resolver to resolve loadingPromise once all plugins loaded
  private loadingResolver: (() => void) | null = null;

  private instanceId?: string;

  public readonly jsApiService: JsApiService;

  public readonly pluginsModel: PluginsModel;

  public pluginEndPoints: GrPluginEndpoints;

  constructor(
    private readonly reportingService: ReportingService,
    private readonly restApiService: RestApiService
  ) {
    this.jsApiService = new GrJsApiInterface(
      () => this.awaitPluginsLoaded(),
      this.reportingService
    );
    this.pluginsModel = new PluginsModel();
    this.pluginEndPoints = new GrPluginEndpoints();
  }

  finalize() {}

  /**
   * Use the plugin name or use the full url if not recognized.
   */
  private getPluginKeyFromUrl(url: string) {
    return getPluginNameFromUrl(url) || `${UNKNOWN_PLUGIN_PREFIX}${url}`;
  }

  /**
   * Load multiple plugins with certain options.
   */
  loadPlugins(plugins: string[] = [], instanceId?: string) {
    this.instanceId = instanceId;
    this.pluginListLoaded = true;

    plugins.forEach(path => {
      const url = this.urlFor(path, window.ASSETS_PATH);
      const pluginKey = this.getPluginKeyFromUrl(url);
      // Skip if already installed.
      if (this.plugins.has(pluginKey)) return;
      this.plugins.set(pluginKey, {
        name: pluginKey,
        url,
        state: PluginState.PENDING,
        plugin: null,
      });

      if (this.isPathEndsWith(url, '.js')) {
        this.loadJsPlugin(path);
      } else {
        this.failToLoad(`Unrecognized plugin path ${path}`, path);
      }
    });

    this.awaitPluginsLoaded().then(() => {
      const loaded = this.getPluginsByState(PluginState.LOADED);
      const failed = this.getPluginsByState(PluginState.LOAD_FAILED);
      this.reportingService.pluginsLoaded(loaded.map(p => p.name));
      this.reportingService.pluginsFailed(failed.map(p => p.name));
    });
  }

  private isPathEndsWith(url: string | URL, suffix: string) {
    if (!(url instanceof URL)) {
      try {
        url = new URL(url);
      } catch (e: unknown) {
        this.reportingService.error(
          'GrPluginLoader',
          new Error('url parse error'),
          e
        );
        return false;
      }
    }

    return url.pathname && url.pathname.endsWith(suffix);
  }

  private getPluginsByState(state: PluginState) {
    return [...this.plugins.values()].filter(p => p.state === state);
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
      this.failToLoad('Failed to determine src.');
      return;
    }
    if (version && version !== API_VERSION) {
      this.failToLoad(
        `Plugin ${src} install error: only version ${API_VERSION} is supported in PolyGerrit. ${version} was given.`,
        src
      );
      return;
    }

    const url = this.urlFor(src);
    const pluginObject = this.getPlugin(url);
    let plugin = pluginObject && pluginObject.plugin;
    if (!plugin) {
      plugin = new Plugin(
        url,
        this.jsApiService,
        this.reportingService,
        this.restApiService,
        this.pluginsModel,
        this.pluginEndPoints
      );
    }
    try {
      callback(plugin);
      this.pluginInstalled(url, plugin);
    } catch (e: unknown) {
      if (e instanceof Error) {
        this.failToLoad(`${e.name}: ${e.message}`, src);
      } else {
        this.reportingService.error(
          'GrPluginLoader',
          new Error('plugin callback error'),
          e
        );
      }
    }
  }

  arePluginsLoaded() {
    if (!this.pluginListLoaded) return false;
    return this.getPluginsByState(PluginState.PENDING).length === 0;
  }

  private checkIfCompleted() {
    if (this.arePluginsLoaded()) {
      this.pluginEndPoints.setPluginsReady();
      if (this.loadingResolver) {
        this.loadingResolver();
        this.loadingResolver = null;
        this.loadingPromise = null;
      }
    }
  }

  private timeout() {
    const pending = this.getPluginsByState(PluginState.PENDING);
    for (const plugin of pending) {
      this.updatePluginState(plugin.url, PluginState.LOAD_FAILED);
    }
    this.checkIfCompleted();
    const errorMessage = `Timeout when loading plugins: ${pending
      .map(p => p.name)
      .join(',')}`;
    fireAlert(document, errorMessage);
    return errorMessage;
  }

  // Private but mocked in tests.
  failToLoad(message: string, pluginUrl?: string) {
    // Show an alert with the error
    fireAlert(document, `Plugin install error: ${message} from ${pluginUrl}`);
    if (pluginUrl) this.updatePluginState(pluginUrl, PluginState.LOAD_FAILED);
    this.checkIfCompleted();
  }

  private updatePluginState(
    pluginUrl: string,
    state: PluginState
  ): PluginObject {
    const key = this.getPluginKeyFromUrl(pluginUrl);
    if (this.plugins.has(key)) {
      this.plugins.get(key)!.state = state;
    } else {
      // Plugin is not recorded for some reason.
      console.info(`Plugin loaded separately: ${pluginUrl}`);
      this.plugins.set(key, {
        name: key,
        url: pluginUrl,
        state,
        plugin: null,
      });
    }
    console.debug(`Plugin ${key} ${state}`);
    return this.plugins.get(key)!;
  }

  private pluginInstalled(url: string, plugin: PluginApi) {
    const pluginObj = this.updatePluginState(url, PluginState.LOADED);
    pluginObj.plugin = plugin;
    this.reportingService.pluginLoaded(plugin.getPluginName() || url);
    this.checkIfCompleted();
  }

  /**
   * Checks if given plugin path/url is enabled or not.
   */
  isPluginEnabled(pathOrUrl: string) {
    const url = this.urlFor(pathOrUrl);
    const key = this.getPluginKeyFromUrl(url);
    return this.plugins.has(key);
  }

  /**
   * Returns the plugin object with a given url.
   */
  getPlugin(pathOrUrl: string) {
    const url = this.urlFor(pathOrUrl);
    const key = this.getPluginKeyFromUrl(url);
    return this.plugins.get(key);
  }

  /**
   * Checks if given plugin path/url is loaded or not.
   */
  isPluginLoaded(pathOrUrl: string): boolean {
    const url = this.urlFor(pathOrUrl);
    const key = this.getPluginKeyFromUrl(url);
    return this.plugins.has(key)
      ? this.plugins.get(key)!.state === PluginState.LOADED
      : false;
  }

  // Private but mocked in tests.
  loadJsPlugin(pluginUrl: string) {
    const urlWithAP = this.urlFor(pluginUrl, window.ASSETS_PATH);
    const urlWithoutAP = this.urlFor(pluginUrl);
    let onerror = undefined;
    if (urlWithAP !== urlWithoutAP) {
      onerror = () => this.createScriptTag(urlWithoutAP);
    }

    this.createScriptTag(urlWithAP, onerror);
  }

  // Private but mocked in tests.
  createScriptTag(url: string, onerror?: OnErrorEventHandler) {
    if (!onerror) {
      onerror = () => this.failToLoad(`${url} load error`, url);
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

  private urlFor(pathOrUrl: string, assetsPath?: string): string {
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
    this.checkIfCompleted();

    if (this.arePluginsLoaded()) {
      return Promise.resolve();
    }
    if (!this.loadingPromise) {
      // specify window here so that TS pulls the correct setTimeout method
      // if window is not specified, then the function is pulled from node
      // and the return type is NodeJS.Timeout object
      let timerId: number;
      this.loadingPromise = Promise.race([
        new Promise<void>(resolve => (this.loadingResolver = resolve)),
        new Promise(
          (_, reject) =>
            (timerId = window.setTimeout(() => {
              reject(new Error(this.timeout()));
            }, PLUGIN_LOADING_TIMEOUT_MS))
        ),
      ]).finally(() => {
        if (timerId) clearTimeout(timerId);
      }) as Promise<void>;
    }
    return this.loadingPromise;
  }
}
