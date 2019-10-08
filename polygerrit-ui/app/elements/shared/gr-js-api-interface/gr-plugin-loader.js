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
(function(window) {
  'use strict';

  /** 
   * @enum {string}
   */
  const PluginState = {
    /**
     * State that indicates the plugin is pending to be loaded.
     */
    PENDING: 'PENDING',

    /**
     * State that indicates the plugin is already loaded.
     */
    LOADED: 'LOADED',

    /**
     * State that indicates the plugin failed to load.
     */
    LOAD_FAILED: 'LOAD_FAILED',
  };

  /** 
   * @typedef {{
   *            url:string,
   *            state:PluginState,
   *            plugin:Object
   *          }}
   */
  var PluginObject;

  /** 
   * @typedef {{
   *            sync:boolean,
   *          }}
   */
  var PluginOption;

  // The wait timeout on loading plugins.
  const PLUGIN_LOADING_TIMEOUT_MS = 10000;

  // Prefix for any unrecognized plugin urls.
  // Url should match following patterns:
  // /plugins/PLUGINNAME/static/SCRIPTNAME.html
  // /plugins/PLUGINNAME.html
  const UNKNOWN_PLUGIN_PREFIX = "__$$__";

  const PRELOADED_PROTOCOL = 'preloaded:';

  // Current API version for Plugin,
  // plugins with incompatible version will not be laoded.
  const API_VERSION = '0.1';

  /**
   * PluginLoader, responsible for:
   * 
   * Loading all plugins and handling errors etc.
   * Recording plugin state.
   * Reporting on plugin loading status.
   * Retrieve plugin.
   * Check plugin statua and if all plugins loaded.
   * 
   */
  class PluginLoader {
    // Internal state to track whether it has been initialized or not
    _initialized = false;

    /** @type {Map<string,PluginObject>} */
    _plugins = new Map();

    // Reference to gr-reporting
    _reporting = null;

    // Promise that resolves when all plugins loaded
    _loadingPromise = null;
    _loadingResolver = null;

    _getReporting () {
      if (!this._reporting) {
        this._reporting = document.createElement('gr-reporting');
      }
      return this._reporting;
    }

    _getPluginKeyFromUrl(url) {
      return Plugin.getPluginNameFromUrl(url) ||
        `${UNKNOWN_PLUGIN_PREFIX}${url}`;
    }

    /**
     * Load multiple plugins with certain options.
     * 
     * @param {Array<string>} plugins 
     * @param {Object<string, PluginOption>} opts 
     */
    loadPlugins(plugins = [], opts = {}) {
      // Mark as initialized, start enabling computing arePluginsLoaded.
      this._initialized = true;

      plugins.forEach(url => {
        const pluginKey = this._getPluginKeyFromUrl(url);
        // Skip if already installed.
        if (this._plugins.has(pluginKey)) return;

        this._plugins.set(pluginKey, {
          url,
          state: PluginState.PENDING,
          plugin: null,
        });

        if (url.endsWith('.html')) {
          this._importHtmlPlugin(url, opts && opts[url]);
        } else if (url.endsWith('.js')) {
          this._loadJsPlugin(url);
        }
      });

      this.awaitPluginsLoaded().then(() => {
        console.info('Plugins loaded');
        // TODO: report timeing on plugins loaded
      });
    }

    install(callback, opt_version, opt_src) {
      // HTML import polyfill adds __importElement pointing to the import tag.
      const script = document.currentScript &&
          (document.currentScript.__importElement || document.currentScript);

      let src = opt_src || (script && script.src);
      if (!src || src.startsWith('data:')) {
        src = script && script.baseURI;
      }
      if (opt_version && opt_version !== API_VERSION) {
        this._failToLoad(`Plugin ${src} install error: only version ` +
            API_VERSION + ' is supported in PolyGerrit. ' + opt_version +
            ' was given.', src);
        return;
      }

      let pluginObject = this.getPlugin(src);
      let plugin = pluginObject && pluginObject.plugin;
      if (!plugin) {
        plugin = new Plugin(src);
      }
      try {
        callback(plugin);
        this._pluginInstalled(src, plugin);
      } catch (e) {
        this._failToLoad(`${e.name}: ${e.message}`, src);
      }
    }

    get arePluginsLoaded() {
      // As the size of plugins is relatively small,
      // so the performance of this check should be reasonable
      if (!this._initialized) return false;
      for (const plugin of this._plugins.values()) {
        if (plugin.state === PluginState.PENDING) return false;
      }
      return true;
    }

    _checkIfCompleted() {
      if (this.arePluginsLoaded && this._loadingResolver) {
        this._loadingResolver();
        this._loadingResolver = null;
        this._loadingPromise = null;
      }
    }

    _timeout() {
      const pendingPlugins = [];
      for (const plugin of this._plugins.values()) {
        if (plugin.state === PluginState.PENDING) {
          this._updatePluginState(plugin.url, PluginState.LOAD_FAILED);
          pendingPlugins.push(plugin.url);
        };
      }

      return `Timeout when loading plugins: ${pendingPlugins.join(',')}`;
    }

    _failToLoad(message, pluginUrl) {
      // Show an alert with the error
      document.dispatchEvent(new CustomEvent('show-alert', {
        detail: {
          message: `Plugin install error: ${message}`,
        },
      }));

      this._updatePluginState(pluginUrl, PluginState.LOAD_FAILED);
    }

    _updatePluginState(pluginUrl, state) {
      const key = this._getPluginKeyFromUrl(pluginUrl);

      if (this._plugins.has(key)) {
        this._plugins.get(key).state = state;
      } else {
        // Plugin is not recorded for some reason.
        console.info(`Plugin loaded after init: ${pluginUrl}`);
        this._plugins.set(key, {
          url: pluginUrl,
          state,
          plugin: null,
        });
      }

      this._checkIfCompleted();
      return this._plugins.get(key);
    }

    _pluginInstalled(url, plugin) {
      const pluginObj = this._updatePluginState(url, PluginState.LOADED);
      pluginObj.plugin = plugin;
      const key = this._getPluginKeyFromUrl(url);
      this._getReporting().pluginLoaded(key);
      console.log(`Plugin ${key} installed.`);
    }

    _getBaseUrl() {
      // TODO(taoalpha): this getBaseUrl should just be a util
      return Gerrit.BaseUrlBehavior.getBaseUrl();
    }

    installPreloadedPlugins() {
      if (!window.Gerrit || !window.Gerrit._preloadedPlugins) { return; }
      const Gerrit = window.Gerrit;
      for (const name in Gerrit._preloadedPlugins) {
        if (!Gerrit._preloadedPlugins.hasOwnProperty(name)) { continue; }
        const callback = Gerrit._preloadedPlugins[name];
        this.install(callback, API_VERSION, PRELOADED_PROTOCOL + name);
      }
    }

    isPluginPreloaded(url) {
      const name = Plugin.getPluginNameFromUrl(url);
      if (name && window.Gerrit._preloadedPlugins) {
        return window.Gerrit._preloadedPlugins.hasOwnProperty(name);
      } else {
        return false;
      }
    }

    /**
     * Checks if given plugin url is enabled or not.
     * @param {string} url 
     */
    isPluginEnabled(url) {
      const key = this._getPluginKeyFromUrl(url);
      return this._plugins.has(key);
    }

    /**
     * Returns the plugin object with a given url.
     * @param {string} url 
     */
    getPlugin(url) {
      const key = this._getPluginKeyFromUrl(url);
      return this._plugins.get(key);
    }

    /**
     * Checks if given plugin url is loaded or not.
     * @param {string} url 
     */
    isPluginLoaded(url) {
      const key = this._getPluginKeyFromUrl(url);
      return this._plugins.has(key) ?
        this._plugins.get(key).state === PluginState.LOADED :
        false;
    }

    _importHtmlPlugin(pluginUrl, opts = {}) {
      // onload (second param) needs to be a function. When null or undefined
      // were passed, plugins were not loaded correctly.
      (Polymer.importHref || Polymer.Base.importHref)(
          this._urlFor(pluginUrl), () => {},
          () => this._failToLoad(`${pluginUrl} import error`, pluginUrl),
          !opts.sync);
    }

    _loadJsPlugin(pluginUrl) {
      this._createScriptTag(this._urlFor(pluginUrl));
    }

    _createScriptTag(url) {
      const el = document.createElement('script');
      el.defer = true;
      el.src = url;
      el.onerror = () => this._failToLoad(`${url} load error`, url);
      return document.body.appendChild(el);
    }

    _urlFor(pathOrUrl) {
      if (!pathOrUrl) {
        return pathOrUrl;
      }
      if (pathOrUrl.startsWith(PRELOADED_PROTOCOL) ||
          pathOrUrl.startsWith('http')) {
        // Plugins are loaded from another domain or preloaded.
        return pathOrUrl;
      }
      if (!pathOrUrl.startsWith('/')) {
        pathOrUrl = '/' + pathOrUrl;
      }
      return window.location.origin + this._getBaseUrl() + pathOrUrl;
    }

    awaitPluginsLoaded() {
      if (this.arePluginsLoaded) {
        return Promise.resolve();
      }

      if (!this._loadingPromise) {
        let timerId;
        this._loadingPromise =
          Promise.race([
            new Promise(resolve => this._loadingResolver = resolve),
            new Promise((_, reject) => timerId = setTimeout(
                () => {
                  reject(this._timeout());
                }, PLUGIN_LOADING_TIMEOUT_MS)),
          ]).then(() => {
            if (timerId) clearTimeout(timerId);
          });
      }
      return this._loadingPromise;
    }
  }

  window.PluginLoader = PluginLoader;
})(window);
