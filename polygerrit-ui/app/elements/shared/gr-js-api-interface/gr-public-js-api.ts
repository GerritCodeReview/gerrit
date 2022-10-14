/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from '../../../utils/url-util';
import {GrAttributeHelper} from '../../plugins/gr-attribute-helper/gr-attribute-helper';
import {GrChangeActionsInterface} from './gr-change-actions-js-api';
import {GrChangeReplyInterface} from './gr-change-reply-js-api';
import {GrDomHooksManager} from '../../plugins/gr-dom-hooks/gr-dom-hooks';
import {GrPopupInterface} from '../../plugins/gr-popup-interface/gr-popup-interface';
import {GrAdminApi} from '../../plugins/gr-admin-api/gr-admin-api';
import {GrAnnotationActionsInterface} from './gr-annotation-actions-js-api';
import {GrEventHelper} from '../../plugins/gr-event-helper/gr-event-helper';
import {GrPluginRestApi} from './gr-plugin-rest-api';
import {getPluginEndpoints} from './gr-plugin-endpoints';
import {getPluginNameFromUrl, send} from './gr-api-utils';
import {GrReportingJsApi} from './gr-reporting-js-api';
import {EventType, PluginApi, TargetElement} from '../../../api/plugin';
import {RequestPayload} from '../../../types/common';
import {HttpMethod} from '../../../constants/constants';
import {GrChangeActions} from '../../change/gr-change-actions/gr-change-actions';
import {GrChecksApi} from '../../plugins/gr-checks-api/gr-checks-api';
import {getAppContext} from '../../../services/app-context';
import {AdminPluginApi} from '../../../api/admin';
import {AnnotationPluginApi} from '../../../api/annotation';
import {EventHelperPluginApi} from '../../../api/event-helper';
import {PopupPluginApi} from '../../../api/popup';
import {ReportingPluginApi} from '../../../api/reporting';
import {ChangeActionsPluginApi} from '../../../api/change-actions';
import {ChangeReplyPluginApi} from '../../../api/change-reply';
import {RestPluginApi} from '../../../api/rest';
import {HookApi, PluginElement, RegisterOptions} from '../../../api/hook';
import {AttributeHelperPluginApi} from '../../../api/attribute-helper';

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

  private domHooks: GrDomHooksManager;

  private readonly _name: string = PLUGIN_NAME_NOT_SET;

  private readonly jsApi = getAppContext().jsApiService;

  private readonly report = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  constructor(url?: string) {
    this.domHooks = new GrDomHooksManager(this);

    if (!url) {
      this.report.error(
        'Plugin constructor',
        new Error(
          'Plugin not being loaded from /plugins base path. Unable to determine name.'
        )
      );
      return this;
    }

    this._url = new URL(url);
    this._name = getPluginNameFromUrl(this._url) ?? 'NULL';
    this.report.trackApi(this, 'plugin', 'constructor');
  }

  getPluginName() {
    return this._name;
  }

  registerStyleModule(endpoint: string, moduleName: string) {
    console.warn(
      `The deprecated plugin API 'registerStyleModule()' was called with parameters '${endpoint}' and '${moduleName}'.`
    );
    this.report.trackApi(this, 'plugin', 'registerStyleModule');
    getPluginEndpoints().registerModule(this, {
      endpoint,
      type: EndpointType.STYLE,
      moduleName,
    });
  }

  /**
   * Registers an endpoint for the plugin.
   */
  registerCustomComponent<T extends PluginElement>(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi<T> {
    this.report.trackApi(this, 'plugin', 'registerCustomComponent');
    return this._registerCustomComponent(endpointName, moduleName, options);
  }

  /**
   * Registers a dynamic endpoint for the plugin.
   *
   * Dynamic plugins are registered by specific prefix, such as
   * 'change-list-header'.
   */
  registerDynamicCustomComponent<T extends PluginElement>(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi<T> {
    this.report.trackApi(this, 'plugin', 'registerDynamicCustomComponent');
    const fullEndpointName = `${endpointName}-${this.getPluginName()}`;
    return this._registerCustomComponent(
      fullEndpointName,
      moduleName,
      options,
      endpointName
    );
  }

  _registerCustomComponent<T extends PluginElement>(
    endpoint: string,
    moduleName?: string,
    options?: RegisterOptions,
    dynamicEndpoint?: string
  ): HookApi<T> {
    const type = options?.replace
      ? EndpointType.REPLACE
      : EndpointType.DECORATE;
    const slot = options?.slot ?? '';
    const domHook = this.domHooks.getDomHook<T>(endpoint, moduleName);
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
  hook<T extends PluginElement>(
    endpointName: string,
    options?: RegisterOptions
  ): HookApi<T> {
    this.report.trackApi(this, 'plugin', 'hook');
    return this.registerCustomComponent(endpointName, undefined, options);
  }

  getServerInfo() {
    this.report.trackApi(this, 'plugin', 'getServerInfo');
    return this.restApiService.getConfig();
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  on(eventName: EventType, callback: (...args: any[]) => any) {
    this.report.trackApi(this, 'plugin', 'on');
    this.jsApi.addEventCallback(eventName, callback);
  }

  url(path?: string) {
    this.report.trackApi(this, 'plugin', 'url');
    if (!this._url) throw new Error('plugin url not set');
    const relPath = '/plugins/' + this._name + (path || '/');
    const sameOriginPath = window.location.origin + `${getBaseUrl()}${relPath}`;
    if (window.location.origin === this._url.origin) {
      // Plugin loaded from the same origin as gr-app, getBaseUrl in effect.
      return sameOriginPath;
    } else {
      // Plugin loaded from assets bundle, expect assets placed along with it.
      return this._url.href.split('/plugins/' + this._name)[0] + relPath;
    }
  }

  screenUrl(screenName?: string) {
    this.report.trackApi(this, 'plugin', 'screenUrl');
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
    return send(this.restApiService, method, this.url(url), callback, payload);
  }

  annotationApi(): AnnotationPluginApi {
    return new GrAnnotationActionsInterface(this);
  }

  changeActions(): ChangeActionsPluginApi {
    return new GrChangeActionsInterface(
      this,
      this.jsApi.getElement(
        TargetElement.CHANGE_ACTIONS
      ) as unknown as GrChangeActions
    );
  }

  changeReply(): ChangeReplyPluginApi {
    return new GrChangeReplyInterface(this, this.jsApi);
  }

  checks(): GrChecksApi {
    return new GrChecksApi(this);
  }

  reporting(): ReportingPluginApi {
    return new GrReportingJsApi(this);
  }

  admin(): AdminPluginApi {
    return new GrAdminApi(this);
  }

  restApi(prefix?: string): RestPluginApi {
    return new GrPluginRestApi(this, prefix);
  }

  attributeHelper(element: HTMLElement): AttributeHelperPluginApi {
    return new GrAttributeHelper(this, element);
  }

  eventHelper(element: HTMLElement): EventHelperPluginApi {
    return new GrEventHelper(this, element);
  }

  popup(): Promise<PopupPluginApi>;

  popup(moduleName: string): Promise<PopupPluginApi>;

  popup(moduleName?: string): Promise<PopupPluginApi | null> {
    if (moduleName !== undefined && typeof moduleName !== 'string') {
      console.error('.popup(element) deprecated, use .popup(moduleName)!');
      return Promise.resolve(null);
    }
    return new GrPopupInterface(this, moduleName).open();
  }

  screen(screenName: string, moduleName?: string) {
    this.report.trackApi(this, 'plugin', 'screen');
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
    this.report.trackApi(this, 'plugin', '_getScreenName');
    return `${this.getPluginName()}-screen-${screenName}`;
  }
}
