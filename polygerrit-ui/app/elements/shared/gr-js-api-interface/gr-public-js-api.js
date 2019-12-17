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

  const PANEL_ENDPOINTS_MAPPING = {
    CHANGE_SCREEN_BELOW_COMMIT_INFO_BLOCK: 'change-view-integration',
    CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK: 'change-metadata-item',
  };

  // Import utils methods
  const {
    PRELOADED_PROTOCOL,
    getPluginNameFromUrl,
    send,
  } = window._apiUtils;

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

  /**
   * Registers an endpoint for the plugin.
   */
  Plugin.prototype.registerCustomComponent = function(
      endpointName, opt_moduleName, opt_options) {
    return this._registerCustomComponent(endpointName, opt_moduleName,
        opt_options);
  };

  /**
   * Registers a dynamic endpoint for the plugin.
   *
   * Dynamic plugins are registered by specific prefix, such as
   * 'change-list-header'.
   */
  Plugin.prototype.registerDynamicCustomComponent = function(
      endpointName, opt_moduleName, opt_options) {
    const fullEndpointName = `${endpointName}-${this.getPluginName()}`;
    return this._registerCustomComponent(fullEndpointName, opt_moduleName,
        opt_options, endpointName);
  };

  Plugin.prototype._registerCustomComponent = function(
      endpointName, opt_moduleName, opt_options, dynamicEndpoint) {
    const type = opt_options && opt_options.replace ?
      EndpointType.REPLACE : EndpointType.DECORATE;
    const hook = this._domHooks.getDomHook(endpointName, opt_moduleName);
    const moduleName = opt_moduleName || hook.getModuleName();
    Gerrit._endpoints.registerModule(
        this, endpointName, type, moduleName, hook, dynamicEndpoint);
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
    const relPath = '/plugins/' + this._name + (opt_path || '/');
    const sameOriginPath = window.location.origin +
      `${Gerrit.BaseUrlBehavior.getBaseUrl()}${relPath}`;
    if (window.location.origin === this._url.origin) {
      // Plugin loaded from the same origin as gr-app, getBaseUrl in effect.
      return sameOriginPath;
    } else if (this._url.protocol === PRELOADED_PROTOCOL) {
      // Plugin is preloaded, load plugin with ASSETS_PATH or location.origin
      return window.ASSETS_PATH ? `${window.ASSETS_PATH}${relPath}` :
        sameOriginPath;
    } else {
      // Plugin loaded from assets bundle, expect assets placed along with it.
      return this._url.href.split('/plugins/' + this._name)[0] + relPath;
    }
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

  Plugin.prototype.styles = function() {
    return new GrStylesApi();
  };

  /**
   * To make REST requests for plugin-provided endpoints, use
   *
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
        this._getScreenName(screenName),
        opt_moduleName);
  };

  Plugin.prototype._getScreenName = function(screenName) {
    return `${this.getPluginName()}-screen-${screenName}`;
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
      this.hook(this._getScreenName(pattern))
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

  window.Plugin = Plugin;
})(window);
