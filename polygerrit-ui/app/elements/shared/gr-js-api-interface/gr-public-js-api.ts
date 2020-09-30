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

import {getBaseUrl} from '../../../utils/url-util';
import {getSharedApiEl} from '../../../utils/dom-util';
import {GrAttributeHelper} from '../../plugins/gr-attribute-helper/gr-attribute-helper';
import {GrChangeActionsInterface} from './gr-change-actions-js-api';
import {GrChangeReplyInterface} from './gr-change-reply-js-api';
import {GrDomHooksManager} from '../../plugins/gr-dom-hooks/gr-dom-hooks';
import {GrThemeApi} from '../../plugins/gr-theme-api/gr-theme-api';
import {GrPopupInterface} from '../../plugins/gr-popup-interface/gr-popup-interface';
import {GrAdminApi} from '../../plugins/gr-admin-api/gr-admin-api';
import {GrAnnotationActionsInterface} from './gr-annotation-actions-js-api';
import {GrChangeMetadataApi} from '../../plugins/gr-change-metadata-api/gr-change-metadata-api';
import {GrEventHelper} from '../../plugins/gr-event-helper/gr-event-helper';
import {GrPluginRestApi} from './gr-plugin-rest-api';
import {GrRepoApi} from '../../plugins/gr-repo-api/gr-repo-api';
import {GrSettingsApi} from '../../plugins/gr-settings-api/gr-settings-api';
import {GrStylesApi} from '../../plugins/gr-styles-api/gr-styles-api';
import {getPluginEndpoints} from './gr-plugin-endpoints';

import {PRELOADED_PROTOCOL, getPluginNameFromUrl, send} from './gr-api-utils';
import {GrReporintJsApi} from './gr-reporting-js-api';
import {
  EventType,
  HookApi,
  PluginApi,
  RegisterOptions,
  TargetElement,
} from '../../plugins/gr-plugin-types';
import {RequestPayload} from '../../../types/common';
import {HttpMethod} from '../../../constants/constants';
import {JsApiService} from './gr-js-api-types';
import {GrChangeActions} from '../../change/gr-change-actions/gr-change-actions';

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
enum EndpointType {
  DECORATE = 'decorate',
  REPLACE = 'replace',
  STYLE = 'style',
}

const PLUGIN_NAME_NOT_SET = 'NULL';

export type SendCallback = (response: unknown) => void;

export class Plugin implements PluginApi {
  readonly _url?: URL;

  private _domHooks: GrDomHooksManager;

  private readonly _name: string = PLUGIN_NAME_NOT_SET;

  // TODO(TS): Change type to GrJsApiInterface
  private readonly sharedApiElement: JsApiService;

  constructor(url?: string) {
    this.sharedApiElement = getSharedApiEl();
    this._domHooks = new GrDomHooksManager(this);

    if (!url) {
      console.warn(
        'Plugin not being loaded from /plugins base path.',
        'Unable to determine name.'
      );
      return this;
    }

    this._url = new URL(url);
    this._name = getPluginNameFromUrl(this._url) ?? 'NULL';
  }

  getPluginName() {
    return this._name;
  }

  registerStyleModule(endpoint: string, moduleName: string) {
    getPluginEndpoints().registerModule(this, {
      endpoint,
      type: EndpointType.STYLE,
      moduleName,
    });
  }

  /**
   * Registers an endpoint for the plugin.
   */
  registerCustomComponent(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi {
    return this._registerCustomComponent(endpointName, moduleName, options);
  }

  /**
   * Registers a dynamic endpoint for the plugin.
   *
   * Dynamic plugins are registered by specific prefix, such as
   * 'change-list-header'.
   */
  registerDynamicCustomComponent(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi {
    const fullEndpointName = `${endpointName}-${this.getPluginName()}`;
    return this._registerCustomComponent(
      fullEndpointName,
      moduleName,
      options,
      endpointName
    );
  }

  _registerCustomComponent(
    endpoint: string,
    moduleName?: string,
    options?: RegisterOptions,
    dynamicEndpoint?: string
  ): HookApi {
    const type =
      options && options.replace ? EndpointType.REPLACE : EndpointType.DECORATE;
    const slot = (options && options.slot) || '';
    const domHook = this._domHooks.getDomHook(endpoint, moduleName);
    moduleName = moduleName || domHook.getModuleName();
    getPluginEndpoints().registerModule(this, {
      slot,
      endpoint,
      type,
      moduleName,
      domHook,
      dynamicEndpoint,
    });
    return domHook;
  }

  /**
   * Returns instance of DOM hook API for endpoint. Creates a placeholder
   * element for the first call.
   */
  hook(endpointName: string, options?: RegisterOptions) {
    return this.registerCustomComponent(endpointName, undefined, options);
  }

  getServerInfo() {
    return document.createElement('gr-rest-api-interface').getConfig();
  }

  on(eventName: EventType, callback: (...args: any[]) => any) {
    this.sharedApiElement.addEventCallback(eventName, callback);
  }

  url(path?: string) {
    if (!this._url) throw new Error('plugin url not set');
    const relPath = '/plugins/' + this._name + (path || '/');
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

  screenUrl(screenName?: string) {
    const origin = location.origin;
    const base = getBaseUrl();
    const tokenPart = screenName ? '/' + screenName : '';
    return `${origin}${base}/x/${this.getPluginName()}${tokenPart}`;
  }

  _send(
    method: HttpMethod,
    url: string,
    callback?: SendCallback,
    payload?: RequestPayload
  ) {
    return send(method, this.url(url), callback, payload);
  }

  get(url: string, callback?: SendCallback) {
    console.warn('.get() is deprecated! Use .restApi().get()');
    return this._send(HttpMethod.GET, url, callback);
  }

  post(url: string, payload: RequestPayload, callback?: SendCallback) {
    console.warn('.post() is deprecated! Use .restApi().post()');
    return this._send(HttpMethod.POST, url, callback, payload);
  }

  put(url: string, payload: RequestPayload, callback?: SendCallback) {
    console.warn('.put() is deprecated! Use .restApi().put()');
    return this._send(HttpMethod.PUT, url, callback, payload);
  }

  delete(url: string, callback?: SendCallback) {
    console.warn('.delete() is deprecated! Use plugin.restApi().delete()');
    return this.restApi()
      .delete(this.url(url))
      .then(res => {
        if (callback) callback(res);
        return res;
      });
  }

  annotationApi() {
    return new GrAnnotationActionsInterface(this);
  }

  changeActions() {
    return new GrChangeActionsInterface(
      this,
      (this.sharedApiElement.getElement(
        TargetElement.CHANGE_ACTIONS
      ) as unknown) as GrChangeActions
    );
  }

  changeReply() {
    return new GrChangeReplyInterface(this, this.sharedApiElement);
  }

  reporting() {
    return new GrReporintJsApi(this);
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
   * @param prefix url for subsequent .get(), .post() etc requests.
   */
  restApi(prefix?: string) {
    return new GrPluginRestApi(prefix);
  }

  attributeHelper(element: HTMLElement) {
    return new GrAttributeHelper(element);
  }

  eventHelper(element: HTMLElement) {
    return new GrEventHelper(element);
  }

  popup(): Promise<GrPopupInterface>;

  popup(moduleName: string): Promise<GrPopupInterface>;

  popup(moduleName?: string): Promise<GrPopupInterface | null> {
    if (moduleName !== undefined && typeof moduleName !== 'string') {
      console.error('.popup(element) deprecated, use .popup(moduleName)!');
      return Promise.resolve(null);
    }
    return new GrPopupInterface(this, moduleName).open();
  }

  screen(screenName: string, moduleName?: string) {
    if (moduleName && typeof moduleName !== 'string') {
      console.error(
        '.screen(pattern, callback) deprecated, use ' +
          '.screen(screenName, moduleName)!'
      );
      return;
    }
    return this.registerCustomComponent(
      this._getScreenName(screenName),
      moduleName
    );
  }

  _getScreenName(screenName: string) {
    return `${this.getPluginName()}-screen-${screenName}`;
  }
}
