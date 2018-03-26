/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

  let _pluginsPendingCount = -1;

  const PANEL_ENDPOINTS_MAPPING = {
    CHANGE_SCREEN_BELOW_COMMIT_INFO_BLOCK: 'change-view-integration',
    CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK: 'change-metadata-item',
  };

  const PLUGIN_LOADING_TIMEOUT_MS = 10000;

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
    if (!(url instanceof URL)) {
      try {
        url = new URL(url);
      } catch (e) {
        console.warn(e);
        return null;
      }
    }
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
    // Pathname should normally look like this:
    // /plugins/PLUGINNAME/static/SCRIPTNAME.html
    // Or, for app/samples:
    // /plugins/PLUGINNAME.html
    return pathname.split('/')[2].split('.')[0];
  }

  function Plugin(opt_url) {
    this._domHooks = new GrDomHooksManager(this);

    if (!opt_url) {
      console.warn('Plugin not being loaded from /plugins base path.',
          'Unable to determine name.');
      return;
    }
    this.deprecated = {
      _loadedGwt: deprecatedAPI._loadedGwt.bind(this),
      install: deprecatedAPI.install.bind(this),
      onAction: deprecatedAPI.onAction.bind(this),
      panel: deprecatedAPI.panel.bind(this),
      popup: deprecatedAPI.popup.bind(this),
      screen: deprecatedAPI.screen.bind(this),
      settingsScreen: deprecatedAPI.settingsScreen.bind(this),
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

  Plugin.prototype.screenUrl = function(opt_screenName) {
    const origin = this._url.origin;
    const base = Gerrit.BaseUrlBehavior.getBaseUrl();
    const tokenPart = opt_screenName ? '/' + opt_screenName : '';
    return `${origin}${base}/x/${this.getPluginName()}${tokenPart}`;
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

  Plugin.prototype.annotationApi = function() {
    return new GrAnnotationActionsInterface(this);
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
    return new GrRepoApi(this);
  };

  Plugin.prototype.changeMetadata = function() {
    return new GrChangeMetadataApi(this);
  };

  Plugin.prototype.admin = function() {
    return new GrAdminApi(this);
  };

  Plugin.prototype.settings = function() {
    return new GrSettingsApi(this);
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
      console.error('.popup(element) deprecated, use .popup(moduleName)!');
      return;
    }
    const api = new GrPopupInterface(this, moduleName);
    return api.open();
  };

  Plugin.prototype.panel = function() {
    console.error('.panel() is deprecated! ' +
        'Use registerCustomComponent() instead.');
  };

  Plugin.prototype.settingsScreen = function() {
    console.error('.settingsScreen() is deprecated! ' +
        'Use .settings() instead.');
  };

  Plugin.prototype.screen = function(screenName, opt_moduleName) {
    if (opt_moduleName && typeof opt_moduleName !== 'string') {
      console.error('.screen(pattern, callback) deprecated, use ' +
          '.screen(screenName, opt_moduleName)!');
      return;
    }
    return this.registerCustomComponent(
        Gerrit._getPluginScreenName(this.getPluginName(), screenName),
        opt_moduleName);
  };

  const deprecatedAPI = {
    _loadedGwt: ()=> {},

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
        if (!details) {
          console.warn(
              `${this.getPluginName()} onAction error: ${action} not found!`);
          return;
        }
        this.changeActions().addTapListener(details.__key, () => {
          callback(new GrPluginActionContext(this, details, change, revision));
        });
      });
    },

    screen(pattern, callback) {
      console.warn('plugin.deprecated.screen is deprecated,' +
          ' use plugin.screen instead!');
      if (pattern instanceof RegExp) {
        console.error('deprecated.screen() does not support RegExp. ' +
            'Please use strings for patterns.');
        return;
      }
      this.hook(Gerrit._getPluginScreenName(this.getPluginName(), pattern))
          .onAttached(el => {
            el.style.display = 'none';
            callback({
              body: el,
              token: el.token,
              onUnload: () => {},
              setTitle: () => {},
              setWindowTitle: () => {},
              show: () => {
                el.style.display = 'initial';
              },
            });
          });
    },

    settingsScreen(path, menu, callback) {
      console.warn('.settingsScreen() is deprecated! Use .settings() instead.');
      const hook = this.settings()
          .title(menu)
          .token(path)
          .module('div')
          .build();
      hook.onAttached(el => {
        el.style.display = 'none';
        const body = el.querySelector('div');
        callback({
          body,
          onUnload: () => {},
          setTitle: () => {},
          setWindowTitle: () => {},
          show: () => {
            el.style.display = 'initial';
          },
        });
      });
    },

    panel(extensionpoint, callback) {
      console.warn('.panel() is deprecated! ' +
          'Use registerCustomComponent() instead.');
      const endpoint = PANEL_ENDPOINTS_MAPPING[extensionpoint];
      if (!endpoint) {
        console.warn(`.panel ${extensionpoint} not supported!`);
        return;
      }
      this.hook(endpoint).onAttached(el => callback({
        body: el,
        p: {
          CHANGE_INFO: el.change,
          REVISION_INFO: el.revision,
        },
        onUnload: () => {},
      }));
    },
  };

  const Gerrit = window.Gerrit || {};

  let _resolveAllPluginsLoaded = null;
  let _allPluginsPromise = null;

  Gerrit._endpoints = new GrPluginEndpoints();

  // Provide reset plugins function to clear installed plugins between tests.
  const app = document.querySelector('#app');
  if (!app) {
    // No gr-app found (running tests)
    Gerrit._resetPlugins = () => {
      _resolveAllPluginsLoaded = null;
      _allPluginsPromise = null;
      Gerrit._setPluginsPending([]);
      Gerrit._endpoints = new GrPluginEndpoints();
      for (const k of Object.keys(_plugins)) {
        delete _plugins[k];
      }
    };
  }

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
    const src = opt_src || (document.currentScript &&
         (document.currentScript.src || document.currentScript.baseURI));
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

  /**
   * Install "stepping stones" API for GWT-compiled plugins by default.
   * @deprecated best effort support, will be removed with GWT UI.
   */
  Gerrit.installGwt = function(url) {
    const name = getPluginNameFromUrl(url);
    let plugin;
    try {
      plugin = _plugins[name] || new Plugin(url);
      plugin.deprecated.install();
      Gerrit._pluginInstalled(url);
    } catch (e) {
      Gerrit._pluginInstallError(`${e.name}: ${e.message}`);
    }
    return plugin;
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
    document.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message: 'Plugins loading timeout. Check the console for errors.',
      },
    }));
    console.error(`Failed to load plugins: ${Object.keys(_pluginsPending)}`);
    Gerrit._setPluginsPending([]);
  };

  Gerrit._setPluginsPending = function(plugins) {
    _pluginsPending = plugins.reduce((o, url) => {
      o[getPluginNameFromUrl(url)] = url;
      return o;
    }, {});
    Gerrit._setPluginsCount(plugins.length);
  };

  Gerrit._setPluginsCount = function(count) {
    _pluginsPendingCount = count;
    if (Gerrit._arePluginsLoaded()) {
      document.createElement('gr-reporting').pluginsLoaded();
      if (_resolveAllPluginsLoaded) {
        _resolveAllPluginsLoaded();
      }
    }
  };

  Gerrit._pluginInstallError = function(message) {
    console.log(`Plugin install error: ${message}`);
    Gerrit._setPluginsCount(_pluginsPendingCount - 1);
  };

  Gerrit._pluginInstalled = function(url) {
    const name = getPluginNameFromUrl(url);
    if (name && !_pluginsPending[name]) {
      console.warn(`Unexpected plugin from ${url}!`);
    } else {
      if (name) {
        delete _pluginsPending[name];
        console.log(`Plugin ${name} installed`);
      } else {
        console.log(`Plugin installed from ${url}`);
      }
      Gerrit._setPluginsCount(_pluginsPendingCount - 1);
    }
  };

  Gerrit._arePluginsLoaded = function() {
    return _pluginsPendingCount === 0;
  };

  Gerrit._getPluginScreenName = function(pluginName, screenName) {
    return `${pluginName}-screen-${screenName}`;
  };

  window.Gerrit = Gerrit;
})(window);
