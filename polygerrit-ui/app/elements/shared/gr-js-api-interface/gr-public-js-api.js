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

  function Plugin(opt_url) {
    this._generatedHookNames = [];
    this._domHooks = new GrDomHooks(this);

    if (!opt_url) {
      console.warn('Plugin not being loaded from /plugins base path.',
          'Unable to determine name.');
      return;
    }

    const base = Gerrit.BaseUrlBehavior.getBaseUrl();

    this._url = new URL(opt_url);
    if (!this._url.pathname.startsWith(base + '/plugins')) {
      console.warn('Plugin not being loaded from /plugins base path:',
          this._url.href, '— Unable to determine name.');
      return;
    }
    this._name = this._url.pathname.replace(base, '').split('/')[2];
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
      endpointName, moduleName, opt_options) {
    const type = opt_options && opt_options.replace ?
          EndpointType.REPLACE : EndpointType.DECORATE;
    Gerrit._endpoints.registerModule(
        this, endpointName, type, moduleName);
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
  };

  Plugin.prototype.get = function(url, opt_callback) {
    return this._send('GET', url, opt_callback);
  },

  Plugin.prototype.post = function(url, payload, opt_callback) {
    return this._send('POST', url, opt_callback, payload);
  },

  Plugin.prototype.changeActions = function() {
    return new GrChangeActionsInterface(Plugin._sharedAPIElement.getElement(
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

  Plugin.prototype.attributeHelper = function(element) {
    return new GrAttributeHelper(element);
  };

  Plugin.prototype.getDomHook = function(endpointName, opt_options) {
    const hook = this._domHooks.getDomHook(endpointName);
    const moduleName = hook.getModuleName();
    const type = opt_options && opt_options.type || EndpointType.DECORATE;
    Gerrit._endpoints.registerModule(this, endpointName, type, moduleName);
    return hook;
  };

  const Gerrit = window.Gerrit || {};

  // Number of plugins to initialize, -1 means 'not yet known'.
  Gerrit._pluginsPending = -1;

  Gerrit._endpoints = new GrPluginEndpoints();

  Gerrit.getPluginName = function() {
    console.warn('Gerrit.getPluginName is not supported in PolyGerrit.',
        'Please use self.getPluginName() instead.');
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

    // TODO(andybons): Polyfill currentScript for IE10/11 (edge supports it).
    const src = opt_src || (document.currentScript &&
         document.currentScript.src || document.currentScript.baseURI);
    const plugin = new Plugin(src);
    try {
      callback(plugin);
    } catch (e) {
      console.warn(plugin.getPluginName() + ' install failed: ' +
          e.name + ': ' + e.message);
    }
    Gerrit._pluginInstalled();
  };

  Gerrit.getLoggedIn = function() {
    return document.createElement('gr-rest-api-interface').getLoggedIn();
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
