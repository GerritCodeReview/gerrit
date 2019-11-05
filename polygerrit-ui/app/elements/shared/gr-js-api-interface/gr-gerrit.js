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

/**
  * This defines the Gerrit instance. All methods directly attached to Gerrit
  * should be defined or linked here.
  */

(function(window) {
  'use strict';

  // Import utils methods
  const {
    send,
    getRestAPI,
  } = window._apiUtils;

  /**
   * Trigger the preinstalls for bundled plugins.
   * This needs to happen before Gerrit as plugin bundle overrides the Gerrit.
   */
  function flushPreinstalls() {
    if (window.Gerrit.flushPreinstalls) {
      window.Gerrit.flushPreinstalls();
    }
  }
  flushPreinstalls();

  window.Gerrit = window.Gerrit || {};
  const Gerrit = window.Gerrit;
  Gerrit._pluginLoader = new PluginLoader();

  Gerrit._endpoints = new GrPluginEndpoints();

  // Provide reset plugins function to clear installed plugins between tests.
  const app = document.querySelector('#app');
  if (!app) {
    // No gr-app found (running tests)
    const {
      testOnly_resetInternalState,
    } = window._apiUtils;
    Gerrit._testOnly_installPreloadedPlugins = (...args) => Gerrit._pluginLoader
        .installPreloadedPlugins(...args);
    Gerrit._testOnly_flushPreinstalls = flushPreinstalls;
    Gerrit._testOnly_resetPlugins = () => {
      testOnly_resetInternalState();
      Gerrit._endpoints = new GrPluginEndpoints();
      Gerrit._pluginLoader = new PluginLoader();
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
    Gerrit._pluginLoader.install(callback, opt_version, opt_src);
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
    return Gerrit._pluginLoader.awaitPluginsLoaded();
  };

  // TODO(taoalpha): consider removing these proxy methods
  // and using _pluginLoader directly

  Gerrit._loadPlugins = function(plugins, opt_option) {
    Gerrit._pluginLoader.loadPlugins(plugins, opt_option);
  };

  Gerrit._arePluginsLoaded = function() {
    return Gerrit._pluginLoader.arePluginsLoaded;
  };

  Gerrit._isPluginPreloaded = function(url) {
    return Gerrit._pluginLoader.isPluginPreloaded(url);
  };

  Gerrit._isPluginEnabled = function(pathOrUrl) {
    return Gerrit._pluginLoader.isPluginEnabled(pathOrUrl);
  };

  Gerrit._isPluginLoaded = function(pathOrUrl) {
    return Gerrit._pluginLoader.isPluginLoaded(pathOrUrl);
  };

  // Preloaded plugins should be installed after Gerrit.install() is set,
  // since plugin preloader substitutes Gerrit.install() temporarily.
  Gerrit._pluginLoader.installPreloadedPlugins();
})(window);