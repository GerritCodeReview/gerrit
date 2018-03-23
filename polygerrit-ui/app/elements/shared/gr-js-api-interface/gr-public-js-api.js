// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window) {
  'use strict';

  const warnNotSupported = function(opt_name) {
    console.warn('Plugin API method ' + (opt_name || '') + ' is not supported');
  };

  /**
   * Hash of loaded and installed plugins, name to Plugin object.
   */
  const plugins = {};

  const stubbedMethods = ['_loadedGwt', 'screen', 'settingsScreen', 'panel'];
  const GWT_PLUGIN_STUB = {};
  for (const name of stubbedMethods) {
    GWT_PLUGIN_STUB[name] = warnNotSupported.bind(null, name);
  }

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

  const API_VERSION = '0.1';

  /**
   * Plugin-provided custom components can affect content in extension
   * points using one of following methods:
   * - DECORATE: custom component is set with `content` attribute and may
   *   decorate (e.g. style) DOM element.
   * - REPLACE: contents of extension point are replaced with the custom
   *   component.
   * - STYLE: custom component is a shared styles module that is inserted
   *   into the extension point.
   */
  const EndpointType = {
    DECORATE: 'decorate',
    REPLACE: 'replace',
    STYLE: 'style',
  };

  // GWT JSNI uses $wnd to refer to window.
  // http://www.gwtproject.org/doc/latest/DevGuideCodingBasicsJSNI.html
  window.$wnd = window;

  function getPluginNameFromUrl(url) {
    const base = Gerrit.BaseUrlBehavior.getBaseUrl();
    const pathname = url.pathname.replace(base, '');
    // Site theme is server from predefined path.
    if (pathname === '/static/gerrit-theme.html') {
      return 'gerrit-theme';
    } else if (!pathname.startsWith('/plugins')) {
      console.warn('Plugin not being loaded from /plugins base path:',
          url.href, '— Unable to determine name.');
      return;
    }
    return pathname.split('/')[2];
  }

  function Plugin(opt_url) {
    this._domHooks = new GrDomHooksManager(this);

    if (!opt_url) {
      console.warn('Plugin not being loaded from /plugins base path.',
          'Unable to determine name.');
      return;
    }
    this.deprecated = {
      install: deprecatedAPI.install.bind(this),
      popup: deprecatedAPI.popup.bind(this),
      onAction: deprecatedAPI.onAction.bind(this),
    };

    this._url = new URL(opt_url);
    this._name = getPluginNameFromUrl(this._url);
  }

  Plugin._sharedAPIElement = document.createElement('gr-js-api-interface');

  Plugin.prototype._name = '';

  Plugin.prototype.getPluginName = function() {
    return this._name;
  };

  Plugin.prototype.registerStyleModule = function(endpointName, moduleName) {
    Gerrit._endpoints.registerModule(
        this, endpointName, EndpointType.STYLE, moduleName);
  };

  Plugin.prototype.registerCustomComponent = function(
      endpointName, opt_moduleName, opt_options) {
    const type = opt_options && opt_options.replace ?
          EndpointType.REPLACE : EndpointType.DECORATE;
    const hook = this._domHooks.getDomHook(endpointName, opt_moduleName);
    const moduleName = opt_moduleName || hook.getModuleName();
    Gerrit._endpoints.registerModule(
        this, endpointName, type, moduleName, hook);
    return hook.getPublicAPI();
  };

  /**
   * Returns instance of DOM hook API for endpoint. Creates a placeholder
   * element for the first call.
   */
  Plugin.prototype.hook = function(endpointName, opt_options) {
    return this.registerCustomComponent(endpointName, undefined, opt_options);
  };

  Plugin.prototype.getServerInfo = function() {
    return document.createElement('gr-rest-api-interface').getConfig();
  };

  Plugin.prototype.on = function(eventName, callback) {
    Plugin._sharedAPIElement.addEventCallback(eventName, callback);
  };

  Plugin.prototype.url = function(opt_path) {
    const base = Gerrit.BaseUrlBehavior.getBaseUrl();
    return this._url.origin + base + '/plugins/' +
        this._name + (opt_path || '/');
  };

  Plugin.prototype._send = function(method, url, opt_callback, opt_payload) {
    return send(method, this.url(url), opt_callback, opt_payload);
  };

  Plugin.prototype.get = function(url, opt_callback) {
    console.warn('.get() is deprecated! Use .restApi().get()');
    return this._send('GET', url, opt_callback);
  };

  Plugin.prototype.post = function(url, payload, opt_callback) {
    console.warn('.post() is deprecated! Use .restApi().post()');
    return this._send('POST', url, opt_callback, payload);
  };

  Plugin.prototype.put = function(url, payload, opt_callback) {
    console.warn('.put() is deprecated! Use .restApi().put()');
    return this._send('PUT', url, opt_callback, payload);
  };

  Plugin.prototype.delete = function(url, opt_callback) {
    return Gerrit.delete(this.url(url), opt_callback);
  };

  Plugin.prototype.changeActions = function() {
    return new GrChangeActionsInterface(this,
      Plugin._sharedAPIElement.getElement(
          Plugin._sharedAPIElement.Element.CHANGE_ACTIONS));
  };

  Plugin.prototype.changeReply = function() {
    return new GrChangeReplyInterface(this,
      Plugin._sharedAPIElement.getElement(
          Plugin._sharedAPIElement.Element.REPLY_DIALOG));
  };

  Plugin.prototype.changeView = function() {
    return new GrChangeViewApi(this);
  };

  Plugin.prototype.theme = function() {
    return new GrThemeApi(this);
  };

  Plugin.prototype.project = function() {
    return new GrProjectApi(this);
  };

  /**
   * To make REST requests for plugin-provided endpoints, use
   * @example
   * const pluginRestApi = plugin.restApi(plugin.url());
   *
   * @param {string} Base url for subsequent .get(), .post() etc requests.
   */
  Plugin.prototype.restApi = function(opt_prefix) {
    return new GrPluginRestApi(opt_prefix);
  };

  Plugin.prototype.attributeHelper = function(element) {
    return new GrAttributeHelper(element);
  };

  Plugin.prototype.eventHelper = function(element) {
    return new GrEventHelper(element);
  };

  Plugin.prototype.popup = function(moduleName) {
    if (typeof moduleName !== 'string') {
      throw new Error('deprecated, use deprecated.popup');
    }
    const api = new GrPopupInterface(this, moduleName);
    return api.open();
  };

  const deprecatedAPI = {
    install() {
      console.log('Installing deprecated APIs is deprecated!');
      for (const method in this.deprecated) {
        if (method === 'install') continue;
        this[method] = this.deprecated[method];
      }
    },

    popup(el) {
      console.warn('plugin.deprecated.popup() is deprecated, ' +
          'use plugin.popup() insted!');
      if (!el) {
        throw new Error('Popup contents not found');
      }
      const api = new GrPopupInterface(this);
      api.open().then(api => api._getElement().appendChild(el));
      return api;
    },

    onAction(type, action, callback) {
      console.warn('plugin.deprecated.onAction() is deprecated,' +
          ' use plugin.changeActions() instead!');
      if (type !== 'change' && type !== 'revision') {
        console.warn(`${type} actions are not supported.`);
        return;
      }
      this.on('showchange', (change, revision) => {
        const details = this.changeActions().getActionDetails(action);
        this.changeActions().addTapListener(details.__key, () => {
          callback(new GrPluginActionContext(this, details, change, revision));
        });
      });
    },

  };

  const Gerrit = window.Gerrit || {};

  // Provide reset plugins function to clear installed plugins between tests.
  const app = document.querySelector('#app');
  if (!app) {
    // No gr-app found (running tests)
    Gerrit._resetPlugins = () => {
      for (const k of Object.keys(plugins)) {
        delete plugins[k];
      }
    };
  }

  // Number of plugins to initialize, -1 means 'not yet known'.
  Gerrit._pluginsPending = -1;

  Gerrit._endpoints = new GrPluginEndpoints();

  Gerrit.getPluginName = function() {
    console.warn('Gerrit.getPluginName is not supported in PolyGerrit.',
        'Please use plugin.getPluginName() instead.');
  };

  Gerrit.css = function(rulesStr) {
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
    if (opt_version && opt_version !== API_VERSION) {
      console.warn('Only version ' + API_VERSION +
          ' is supported in PolyGerrit. ' + opt_version + ' was given.');
      Gerrit._pluginInstalled();
      return;
    }

    const src = opt_src || (document.currentScript &&
         (document.currentScript.src || document.currentScript.baseURI));
    const name = getPluginNameFromUrl(new URL(src));
    const existingPlugin = plugins[name];
    const plugin = existingPlugin || new Plugin(src);
    try {
      callback(plugin);
      plugins[name] = plugin;
    } catch (e) {
      console.warn(`${name} install failed: ${e.name}: ${e.message}`);
    }
    // Don't double count plugins that may have an html and js install.
    if (!existingPlugin) {
      Gerrit._pluginInstalled();
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

  /**
   * Polyfill GWT API dependencies to avoid runtime exceptions when loading
   * GWT-compiled plugins.
   * @deprecated Not supported in PolyGerrit.
   */
  Gerrit.installGwt = function() {
    Gerrit._pluginInstalled();
    return GWT_PLUGIN_STUB;
  };

  Gerrit._allPluginsPromise = null;
  Gerrit._resolveAllPluginsLoaded = null;

  Gerrit.awaitPluginsLoaded = function() {
    if (!Gerrit._allPluginsPromise) {
      if (Gerrit._arePluginsLoaded()) {
        Gerrit._allPluginsPromise = Promise.resolve();
      } else {
        Gerrit._allPluginsPromise = new Promise(resolve => {
          Gerrit._resolveAllPluginsLoaded = resolve;
        });
      }
    }
    return Gerrit._allPluginsPromise;
  };

  Gerrit._setPluginsCount = function(count) {
    Gerrit._pluginsPending = count;
    if (Gerrit._arePluginsLoaded()) {
      document.createElement('gr-reporting').pluginsLoaded();
      if (Gerrit._resolveAllPluginsLoaded) {
        Gerrit._resolveAllPluginsLoaded();
      }
    }
  };

  Gerrit._pluginInstalled = function() {
    Gerrit._setPluginsCount(Gerrit._pluginsPending - 1);
  };

  Gerrit._arePluginsLoaded = function() {
    return Gerrit._pluginsPending === 0;
  };

  window.Gerrit = Gerrit;
})(window);
