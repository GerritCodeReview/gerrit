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

import {getBaseUrl} from '../../../utils/url-util.js';
import {getSharedApiEl} from '../../../utils/dom-util.js';
import {GrAttributeHelper} from '../../plugins/gr-attribute-helper/gr-attribute-helper.js';
import {GrChangeActionsInterface} from './gr-change-actions-js-api.js';
import {GrChangeReplyInterface} from './gr-change-reply-js-api.js';
import {GrDomHooksManager} from '../../plugins/gr-dom-hooks/gr-dom-hooks.js';
import {GrThemeApi} from '../../plugins/gr-theme-api/gr-theme-api.js';
import {GrPopupInterface} from '../../plugins/gr-popup-interface/gr-popup-interface.js';
import {GrAdminApi} from '../../plugins/gr-admin-api/gr-admin-api.js';
import {GrAnnotationActionsInterface} from './gr-annotation-actions-js-api.js';
import {GrChangeMetadataApi} from '../../plugins/gr-change-metadata-api/gr-change-metadata-api.js';
import {GrEventHelper} from '../../plugins/gr-event-helper/gr-event-helper.js';
import {GrPluginRestApi} from './gr-plugin-rest-api.js';
import {GrRepoApi} from '../../plugins/gr-repo-api/gr-repo-api.js';
import {GrSettingsApi} from '../../plugins/gr-settings-api/gr-settings-api.js';
import {GrStylesApi} from '../../plugins/gr-styles-api/gr-styles-api.js';
import {GrPluginActionContext} from './gr-plugin-action-context.js';
import {pluginEndpoints} from './gr-plugin-endpoints.js';

import {
  PRELOADED_PROTOCOL,
  getPluginNameFromUrl,
  send,
} from './gr-api-utils.js';

const PANEL_ENDPOINTS_MAPPING = {
  CHANGE_SCREEN_BELOW_COMMIT_INFO_BLOCK: 'change-view-integration',
  CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK: 'change-metadata-item',
};

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

export class Plugin {
  constructor(opt_url) {
    this._domHooks = new GrDomHooksManager(this);

    if (!opt_url) {
      console.warn(
          'Plugin not being loaded from /plugins base path.',
          'Unable to determine name.'
      );
      return this;
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
    this.sharedApiElement = getSharedApiEl();
  }

  getPluginName() {
    return this._name;
  }

  registerStyleModule(endpoint, moduleName) {
    pluginEndpoints.registerModule(this, {
      endpoint,
      type: EndpointType.STYLE,
      moduleName,
    });
  }

  /**
   * Registers an endpoint for the plugin.
   */
  registerCustomComponent(endpointName, opt_moduleName, opt_options) {
    return this._registerCustomComponent(
        endpointName,
        opt_moduleName,
        opt_options
    );
  }

  /**
   * Registers a dynamic endpoint for the plugin.
   *
   * Dynamic plugins are registered by specific prefix, such as
   * 'change-list-header'.
   */
  registerDynamicCustomComponent(endpointName, opt_moduleName, opt_options) {
    const fullEndpointName = `${endpointName}-${this.getPluginName()}`;
    return this._registerCustomComponent(
        fullEndpointName,
        opt_moduleName,
        opt_options,
        endpointName
    );
  }

  _registerCustomComponent(
      endpoint,
      opt_moduleName,
      opt_options,
      dynamicEndpoint
  ) {
    const type =
      opt_options && opt_options.replace
        ? EndpointType.REPLACE
        : EndpointType.DECORATE;
    const slot = (opt_options && opt_options.slot) || '';
    const domHook = this._domHooks.getDomHook(endpoint, opt_moduleName);
    const moduleName = opt_moduleName || domHook.getModuleName();
    pluginEndpoints.registerModule(this, {
      slot,
      endpoint,
      type,
      moduleName,
      domHook,
      dynamicEndpoint,
    });
    return domHook.getPublicAPI();
  }

  /**
   * Returns instance of DOM hook API for endpoint. Creates a placeholder
   * element for the first call.
   */
  hook(endpointName, opt_options) {
    return this.registerCustomComponent(endpointName, undefined, opt_options);
  }

  getServerInfo() {
    return document.createElement('gr-rest-api-interface').getConfig();
  }

  on(eventName, callback) {
    this.sharedApiElement.addEventCallback(eventName, callback);
  }

  url(opt_path) {
    const relPath = '/plugins/' + this._name + (opt_path || '/');
    const sameOriginPath = window.location.origin + `${getBaseUrl()}${relPath}`;
    if (window.location.origin === this._url.origin) {
      // Plugin loaded from the same origin as gr-app, getBaseUrl in effect.
      return sameOriginPath;
    } else if (this._url.protocol === PRELOADED_PROTOCOL) {
      // Plugin is preloaded, load plugin with ASSETS_PATH or location.origin
      return window.ASSETS_PATH
        ? `${window.ASSETS_PATH}${relPath}`
        : sameOriginPath;
    } else {
      // Plugin loaded from assets bundle, expect assets placed along with it.
      return this._url.href.split('/plugins/' + this._name)[0] + relPath;
    }
  }

  screenUrl(opt_screenName) {
    const origin = location.origin;
    const base = getBaseUrl();
    const tokenPart = opt_screenName ? '/' + opt_screenName : '';
    return `${origin}${base}/x/${this.getPluginName()}${tokenPart}`;
  }

  _send(method, url, opt_callback, opt_payload) {
    return send(method, this.url(url), opt_callback, opt_payload);
  }

  get(url, opt_callback) {
    console.warn('.get() is deprecated! Use .restApi().get()');
    return this._send('GET', url, opt_callback);
  }

  post(url, payload, opt_callback) {
    console.warn('.post() is deprecated! Use .restApi().post()');
    return this._send('POST', url, opt_callback, payload);
  }

  put(url, payload, opt_callback) {
    console.warn('.put() is deprecated! Use .restApi().put()');
    return this._send('PUT', url, opt_callback, payload);
  }

  delete(url, opt_callback) {
    console.warn('.delete() is deprecated! Use plugin.restApi().delete()');
    return this.restApi()
        .delete(this.url(url))
        .then(res => {
          if (opt_callback) {
            opt_callback(res);
          }
          return res;
        });
  }

  annotationApi() {
    return new GrAnnotationActionsInterface(this);
  }

  changeActions() {
    return new GrChangeActionsInterface(
        this,
        this.sharedApiElement.getElement(
            this.sharedApiElement.Element.CHANGE_ACTIONS
        )
    );
  }

  changeReply() {
    return new GrChangeReplyInterface(this, this.sharedApiElement);
  }

  theme() {
    return new GrThemeApi(this);
  }

  project() {
    return new GrRepoApi(this);
  }

  changeMetadata() {
    return new GrChangeMetadataApi(this);
  }

  admin() {
    return new GrAdminApi(this);
  }

  settings() {
    return new GrSettingsApi(this);
  }

  styles() {
    return new GrStylesApi();
  }

  /**
   * To make REST requests for plugin-provided endpoints, use
   *
   * @example
   * const pluginRestApi = plugin.restApi(plugin.url());
   *
   * @param {string=} opt_prefix url for subsequent .get(), .post() etc requests.
   */
  restApi(opt_prefix) {
    return new GrPluginRestApi(opt_prefix);
  }

  attributeHelper(element) {
    return new GrAttributeHelper(element);
  }

  eventHelper(element) {
    return new GrEventHelper(element);
  }

  popup(moduleName) {
    if (typeof moduleName !== 'string') {
      console.error('.popup(element) deprecated, use .popup(moduleName)!');
      return;
    }
    const api = new GrPopupInterface(this, moduleName);
    return api.open();
  }

  panel() {
    console.error(
        '.panel() is deprecated! ' + 'Use registerCustomComponent() instead.'
    );
  }

  settingsScreen() {
    console.error(
        '.settingsScreen() is deprecated! ' + 'Use .settings() instead.'
    );
  }

  screen(screenName, opt_moduleName) {
    if (opt_moduleName && typeof opt_moduleName !== 'string') {
      console.error(
          '.screen(pattern, callback) deprecated, use ' +
          '.screen(screenName, opt_moduleName)!'
      );
      return;
    }
    return this.registerCustomComponent(
        this._getScreenName(screenName),
        opt_moduleName
    );
  }

  _getScreenName(screenName) {
    return `${this.getPluginName()}-screen-${screenName}`;
  }
}

// TODO: should be removed soon after all core plugins moved away from it.
const deprecatedAPI = {
  _loadedGwt: () => {},

  install() {
    console.info('Installing deprecated APIs is deprecated!');
    for (const method in this.deprecated) {
      if (method === 'install') continue;
      this[method] = this.deprecated[method];
    }
  },

  popup(el) {
    console.warn(
        'plugin.deprecated.popup() is deprecated, '
        + 'use plugin.popup() insted!'
    );
    if (!el) {
      throw new Error('Popup contents not found');
    }
    const api = new GrPopupInterface(this);
    api.open().then(api => api._getElement().appendChild(el));
    return api;
  },

  onAction(type, action, callback) {
    console.warn(
        'plugin.deprecated.onAction() is deprecated,' +
        ' use plugin.changeActions() instead!'
    );
    if (type !== 'change' && type !== 'revision') {
      console.warn(`${type} actions are not supported.`);
      return;
    }
    this.on('showchange', (change, revision) => {
      const details = this.changeActions().getActionDetails(action);
      if (!details) {
        console.warn(
            `${this.getPluginName()} onAction error: ${action} not found!`
        );
        return;
      }
      this.changeActions().addTapListener(details.__key, () => {
        callback(new GrPluginActionContext(this, details, change, revision));
      });
    });
  },

  screen(pattern, callback) {
    console.warn(
        'plugin.deprecated.screen is deprecated,'
        + ' use plugin.screen instead!'
    );
    if (pattern instanceof RegExp) {
      console.error(
          'deprecated.screen() does not support RegExp. ' +
          'Please use strings for patterns.'
      );
      return;
    }
    this.hook(this._getScreenName(pattern)).onAttached(el => {
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
    const hook = this.settings().title(menu)
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
    console.warn(
        '.panel() is deprecated! ' + 'Use registerCustomComponent() instead.'
    );
    const endpoint = PANEL_ENDPOINTS_MAPPING[extensionpoint];
    if (!endpoint) {
      console.warn(`.panel ${extensionpoint} not supported!`);
      return;
    }
    this.hook(endpoint).onAttached(el =>
      callback({
        body: el,
        p: {
          CHANGE_INFO: el.change,
          REVISION_INFO: el.revision,
        },
        onUnload: () => {},
      })
    );
  },
};