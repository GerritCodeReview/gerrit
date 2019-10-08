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
(function() {
  'use strict';

  /** BEGIN: deprecation code, to be removed. */
  let _restAPI;

  const getRestAPI = () => {
    if (!_restAPI) {
      _restAPI = document.createElement('gr-rest-api-interface');
    }
    return _restAPI;
  };

  // TODO (viktard): deprecate in favor of GrPluginRestApi.
  function send(method, url, opt_callback, opt_payload) {
    return getRestAPI().send(method, url, opt_payload).then(response => {
      if (response.status < 200 || response.status >= 300) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(text);
          } else {
            return Promise.reject(response.status);
          }
        });
      } else {
        return getRestAPI().getResponseObject(response);
      }
    }).then(response => {
      if (opt_callback) {
        opt_callback(response);
      }
      return response;
    });
  }
  /** END: deprecation code, to be removed. */
  
  window.Gerrit = window.Gerrit || {};
  const Gerrit = window.Gerrit;

  /** BEGIN: preinstall on bundling assets if has any. */
  function flushPreinstalls() {
    if (Gerrit.flushPreinstalls) {
      Gerrit.flushPreinstalls();
    }
  }

  flushPreinstalls();
  /** END: preinstall on bundling assets if has any. */

  // TODO: All Gerrit methods should be moved to a different place.
  Gerrit._endpoints = new GrPluginEndpoints();

  // Provide reset plugins function to clear installed plugins between tests.
  const app = document.querySelector('#app');
  if (!app) {
    // No gr-app found (running tests)
    Gerrit._installPreloadedPlugins = () => {
        Gerrit._pluginLoader = Gerrit._pluginLoader || new PluginLoader();
        Gerrit._pluginLoader.installPreloadedPlugins();
    }
    Gerrit._flushPreinstalls = flushPreinstalls;
    Gerrit._resetPlugins = () => {
      Gerrit._endpoints = new GrPluginEndpoints();
      Gerrit._pluginLoader = new PluginLoader();
    };
  }

  /** BEGIN: deprecation code, to be removed. */
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
  /** END: deprecation code, to be removed. */

  Gerrit._pluginLoader = Gerrit._pluginLoader || new PluginLoader();

  Gerrit.loadPlugins = function(plugins, plugin_opts) {
    Gerrit._pluginLoader.loadPlugins(plugins, plugin_opts);
  }

  Gerrit.install = function(callback, opt_version, opt_src) {
    Gerrit._pluginLoader.install(callback, opt_version, opt_src);
  };

  Gerrit.getPlugin = function(pluginUrl) {
    return Gerrit._pluginLoader.getPlugin(pluginUrl);
  };

  Gerrit.awaitPluginsLoaded = function() {
    return Gerrit._pluginLoader.awaitPluginsLoaded();
  };

  // TODO(taoalpha): use _pluginLoader directly
  Gerrit._arePluginsLoaded = function() {
    return Gerrit._pluginLoader.arePluginsLoaded;
  };

  // TODO(taoalpha): use _pluginLoader directly
  Gerrit._isPluginPreloaded = function(url) {
    return Gerrit._pluginLoader.isPluginPreloaded(url);
  };

  // Preloaded plugins should be installed after Gerrit.install() is set,
  // since plugin preloader substitutes Gerrit.install() temporarily.
  Gerrit._pluginLoader.installPreloadedPlugins();
})();