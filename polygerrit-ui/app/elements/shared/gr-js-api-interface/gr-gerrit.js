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
   * Hash of loaded and installed plugins, name to Plugin object.
   */
  const _plugins = {};

  /**
   * Array of plugin URLs to be loaded, name to url.
   */
  let _pluginsPending = {};

  let _pluginsInstalled = [];

  let _pluginsPendingCount = -1;

  const UNKNOWN_PLUGIN = 'unknown';
  const PRELOADED_PROTOCOL = 'preloaded:';

  const PLUGIN_LOADING_TIMEOUT_MS = 10000;

  let _reporting;
  const getReporting = () => {
    if (!_reporting) {
      _reporting = document.createElement('gr-reporting');
    }
    return _reporting;
  };

  // Import utils methods
  const {
      getPluginNameFromUrl,
      send,
      getRestAPI,
      resetInternalState,
  } = window._apiUtils;

  const API_VERSION = '0.1';

  // Trigger the preinstalls for bundled plugins
  // This needs to happen before Gerrit
  // as plugin bundle overrides the Gerrit
  function flushPreinstalls() {
    if (window.Gerrit.flushPreinstalls) {
      window.Gerrit.flushPreinstalls();
    }
  }

  flushPreinstalls();

  window.Gerrit = window.Gerrit || {};
  const Gerrit = window.Gerrit;

  let _resolveAllPluginsLoaded = null;
  let _allPluginsPromise = null;

  Gerrit._endpoints = new GrPluginEndpoints();

  // Provide reset plugins function to clear installed plugins between tests.
  const app = document.querySelector('#app');
  if (!app) {
    // No gr-app found (running tests)
    Gerrit._installPreloadedPlugins = installPreloadedPlugins;
    Gerrit._flushPreinstalls = flushPreinstalls;
    Gerrit._resetPlugins = () => {
      _allPluginsPromise = null;
      _pluginsInstalled = [];
      _pluginsPending = {};
      _pluginsPendingCount = -1;
      _reporting = null;
      _resolveAllPluginsLoaded = null;
      resetInternalState();
      Gerrit._endpoints = new GrPluginEndpoints();
      for (const k of Object.keys(_plugins)) {
        delete _plugins[k];
      }
    };
  }

  /**
   * @deprecated Use plugin.styles().css(rulesStr) instead. Please, consult
   * the documentation how to replace it accordingly.
   */
  Gerrit.css = function(rulesStr) {
    console.warn('Gerrit.css(rulesStr) is deprecated!',
        'Use plugin.styles().css(rulesStr)');
    if (!Gerrit._customStyleSheet) {
      const styleEl = document.createElement('style');
      document.head.appendChild(styleEl);
      Gerrit._customStyleSheet = styleEl.sheet;
    }

    const name = '__pg_js_api_class_' +
        Gerrit._customStyleSheet.cssRules.length;
    Gerrit._customStyleSheet.insertRule('.' + name + '{' + rulesStr + '}', 0);
    return name;
  };

  Gerrit.install = function(callback, opt_version, opt_src) {
    // HTML import polyfill adds __importElement pointing to the import tag.
    const script = document.currentScript &&
        (document.currentScript.__importElement || document.currentScript);

    let src = opt_src || (script && script.src);
    if (!src || src.startsWith('data:')) {
      src = script && script.baseURI;
    }
    const name = getPluginNameFromUrl(src);

    if (opt_version && opt_version !== API_VERSION) {
      Gerrit._pluginInstallError(`Plugin ${name} install error: only version ` +
          API_VERSION + ' is supported in PolyGerrit. ' + opt_version +
          ' was given.');
      return;
    }

    const existingPlugin = _plugins[name];
    const plugin = existingPlugin || new Plugin(src);
    try {
      callback(plugin);
      if (name) {
        _plugins[name] = plugin;
      }
      if (!existingPlugin) {
        Gerrit._pluginInstalled(src);
      }
    } catch (e) {
      Gerrit._pluginInstallError(`${e.name}: ${e.message}`);
    }
  };

  Gerrit.getLoggedIn = function() {
    console.warn('Gerrit.getLoggedIn() is deprecated! ' +
        'Use plugin.restApi().getLoggedIn()');
    return document.createElement('gr-rest-api-interface').getLoggedIn();
  };

  Gerrit.get = function(url, callback) {
    console.warn('.get() is deprecated! Use plugin.restApi().get()');
    send('GET', url, callback);
  };

  Gerrit.post = function(url, payload, callback) {
    console.warn('.post() is deprecated! Use plugin.restApi().post()');
    send('POST', url, callback, payload);
  };

  Gerrit.put = function(url, payload, callback) {
    console.warn('.put() is deprecated! Use plugin.restApi().put()');
    send('PUT', url, callback, payload);
  };

  Gerrit.delete = function(url, opt_callback) {
    console.warn('.delete() is deprecated! Use plugin.restApi().delete()');
    return getRestAPI().send('DELETE', url).then(response => {
      if (response.status !== 204) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(text);
          } else {
            return Promise.reject(response.status);
          }
        });
      }
      if (opt_callback) {
        opt_callback(response);
      }
      return response;
    });
  };

  Gerrit.awaitPluginsLoaded = function() {
    if (!_allPluginsPromise) {
      if (Gerrit._arePluginsLoaded()) {
        _allPluginsPromise = Promise.resolve();
      } else {
        let timeoutId;
        _allPluginsPromise =
          Promise.race([
            new Promise(resolve => _resolveAllPluginsLoaded = resolve),
            new Promise(resolve => timeoutId = setTimeout(
                Gerrit._pluginLoadingTimeout, PLUGIN_LOADING_TIMEOUT_MS)),
          ]).then(() => clearTimeout(timeoutId));
      }
    }
    return _allPluginsPromise;
  };

  Gerrit._pluginLoadingTimeout = function() {
    console.error(`Failed to load plugins: ${Object.keys(_pluginsPending)}`);
    Gerrit._setPluginsPending([]);
  };

  Gerrit._setPluginsPending = function(plugins) {
    _pluginsPending = plugins.reduce((o, url) => {
      // TODO(viktard): Remove guard (@see Issue 8962)
      o[getPluginNameFromUrl(url) || UNKNOWN_PLUGIN] = url;
      return o;
    }, {});
    Gerrit._setPluginsCount(Object.keys(_pluginsPending).length);
  };

  Gerrit._setPluginsCount = function(count) {
    _pluginsPendingCount = count;
    if (Gerrit._arePluginsLoaded()) {
      getReporting().pluginsLoaded(_pluginsInstalled);
      if (_resolveAllPluginsLoaded) {
        _resolveAllPluginsLoaded();
      }
    }
  };

  Gerrit._pluginInstallError = function(message) {
    document.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message: `Plugin install error: ${message}`,
      },
    }));
    console.info(`Plugin install error: ${message}`);
    Gerrit._setPluginsCount(_pluginsPendingCount - 1);
  };

  Gerrit._pluginInstalled = function(url) {
    const name = getPluginNameFromUrl(url) || UNKNOWN_PLUGIN;
    if (!_pluginsPending[name]) {
      console.warn(`Unexpected plugin ${name} installed from ${url}.`);
    } else {
      delete _pluginsPending[name];
      _pluginsInstalled.push(name);
      Gerrit._setPluginsCount(_pluginsPendingCount - 1);
      getReporting().pluginLoaded(name);
      console.log(`Plugin ${name} installed.`);
    }
  };

  Gerrit._arePluginsLoaded = function() {
    return _pluginsPendingCount === 0;
  };

  Gerrit._getPluginScreenName = function(pluginName, screenName) {
    return `${pluginName}-screen-${screenName}`;
  };

  Gerrit._isPluginPreloaded = function(url) {
    const name = getPluginNameFromUrl(url);
    if (name && Gerrit._preloadedPlugins) {
      return name in Gerrit._preloadedPlugins;
    } else {
      return false;
    }
  };

  function installPreloadedPlugins() {
    if (!Gerrit._preloadedPlugins) { return; }
    for (const name in Gerrit._preloadedPlugins) {
      if (!Gerrit._preloadedPlugins.hasOwnProperty(name)) { continue; }
      const callback = Gerrit._preloadedPlugins[name];
      Gerrit.install(callback, API_VERSION, PRELOADED_PROTOCOL + name);
    }
  }

  // Preloaded plugins should be installed after Gerrit.install() is set,
  // since plugin preloader substitutes Gerrit.install() temporarily.
  installPreloadedPlugins();
})(window);