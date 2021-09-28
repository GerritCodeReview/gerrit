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
import {getPluginLoader, PluginOptionMap} from './gr-plugin-loader';
import {send} from './gr-api-utils';
import {appContext} from '../../../services/app-context';
import {PluginApi} from '../../../api/plugin';
import {HttpMethod} from '../../../constants/constants';
import {RequestPayload} from '../../../types/common';
import {
  EventCallback,
  EventEmitterService,
} from '../../../services/gr-event-interface/gr-event-interface';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {Gerrit} from '../../../api/gerrit';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {tableStyles} from '../../../styles/gr-table-styles';

/**
 * These are the methods and properties that are exposed explicitly in the
 * public global `Gerrit` interface. In reality JavaScript plugins do depend
 * on some of this "internal" stuff. But we want to convert plugins to
 * TypeScript one by one and while doing that remove those dependencies.
 */
export interface GerritInternal extends EventEmitterService, Gerrit {
  css(rule: string): string;
  install(
    callback: (plugin: PluginApi) => void,
    opt_version?: string,
    src?: string
  ): void;
  getLoggedIn(): Promise<boolean>;
  get(url: string, callback?: (response: unknown) => void): void;
  post(
    url: string,
    payload?: RequestPayload,
    callback?: (response: unknown) => void
  ): void;
  put(
    url: string,
    payload?: RequestPayload,
    callback?: (response: unknown) => void
  ): void;
  delete(url: string, callback?: (response: unknown) => void): void;
  isPluginLoaded(pathOrUrl: string): boolean;
  awaitPluginsLoaded(): Promise<unknown>;
  _loadPlugins(plugins: string[], opts: PluginOptionMap): void;
  _arePluginsLoaded(): boolean;
  _isPluginEnabled(pathOrUrl: string): boolean;
  _isPluginLoaded(pathOrUrl: string): boolean;
  _customStyleSheet?: CSSStyleSheet;

  // exposed methods
  Nav: typeof GerritNav;
  Auth: typeof appContext.authService;
}

export function initGerritPluginApi() {
  window.Gerrit = window.Gerrit ?? new GerritImpl();
}

export function _testOnly_initGerritPluginApi(): GerritInternal {
  initGerritPluginApi();
  return window.Gerrit as GerritInternal;
}

export function deprecatedDelete(
  url: string,
  callback?: (response: Response) => void
) {
  console.warn('.delete() is deprecated! Use plugin.restApi().delete()');
  return appContext.restApiService
    .send(HttpMethod.DELETE, url)
    .then(response => {
      if (response.status !== 204) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(new Error(text));
          } else {
            return Promise.reject(new Error(`${response.status}`));
          }
        });
      }
      if (callback) callback(response);
      return response;
    });
}

const fakeApi = {
  getPluginName: () => 'global',
};

/**
 * TODO(brohlfs): Reduce this step by step until it only contains install().
 */
class GerritImpl implements GerritInternal {
  _customStyleSheet?: CSSStyleSheet;

  public readonly Nav = GerritNav;

  public readonly Auth = appContext.authService;

  public readonly styles = {
    font: fontStyles,
    form: formStyles,
    menuPage: menuPageStyles,
    spinner: spinnerStyles,
    subPage: subpageStyles,
    table: tableStyles,
  };

  /**
   * @deprecated Use plugin.styles().css(rulesStr) instead. Please, consult
   * the documentation how to replace it accordingly.
   */
  css(rulesStr: string) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'css');
    console.warn(
      'Gerrit.css(rulesStr) is deprecated!',
      'Use plugin.styles().css(rulesStr)'
    );
    if (!this._customStyleSheet) {
      const styleEl = document.createElement('style');
      document.head.appendChild(styleEl);
      this._customStyleSheet = styleEl.sheet!;
    }

    const name = `__pg_js_api_class_${this._customStyleSheet.cssRules.length}`;
    this._customStyleSheet.insertRule('.' + name + '{' + rulesStr + '}', 0);
    return name;
  }

  install(
    callback: (plugin: PluginApi) => void,
    version?: string,
    src?: string
  ) {
    getPluginLoader().install(callback, version, src);
  }

  getLoggedIn() {
    appContext.reportingService.trackApi(fakeApi, 'global', 'getLoggedIn');
    console.warn(
      'Gerrit.getLoggedIn() is deprecated! ' +
        'Use plugin.restApi().getLoggedIn()'
    );
    return appContext.restApiService.getLoggedIn();
  }

  get(url: string, callback?: (response: unknown) => void) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'get');
    console.warn('.get() is deprecated! Use plugin.restApi().get()');
    send(HttpMethod.GET, url, callback);
  }

  post(
    url: string,
    payload?: RequestPayload,
    callback?: (response: unknown) => void
  ) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'post');
    console.warn('.post() is deprecated! Use plugin.restApi().post()');
    send(HttpMethod.POST, url, callback, payload);
  }

  put(
    url: string,
    payload?: RequestPayload,
    callback?: (response: unknown) => void
  ) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'put');
    console.warn('.put() is deprecated! Use plugin.restApi().put()');
    send(HttpMethod.PUT, url, callback, payload);
  }

  delete(url: string, callback?: (response: Response) => void) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'delete');
    deprecatedDelete(url, callback);
  }

  awaitPluginsLoaded() {
    appContext.reportingService.trackApi(
      fakeApi,
      'global',
      'awaitPluginsLoaded'
    );
    return getPluginLoader().awaitPluginsLoaded();
  }

  // TODO(taoalpha): consider removing these proxy methods
  // and using getPluginLoader() directly
  _loadPlugins(plugins: string[] = []) {
    appContext.reportingService.trackApi(fakeApi, 'global', '_loadPlugins');
    getPluginLoader().loadPlugins(plugins);
  }

  _arePluginsLoaded() {
    appContext.reportingService.trackApi(
      fakeApi,
      'global',
      '_arePluginsLoaded'
    );
    return getPluginLoader().arePluginsLoaded();
  }

  _isPluginEnabled(pathOrUrl: string) {
    appContext.reportingService.trackApi(fakeApi, 'global', '_isPluginEnabled');
    return getPluginLoader().isPluginEnabled(pathOrUrl);
  }

  isPluginLoaded(pathOrUrl: string) {
    return this._isPluginLoaded(pathOrUrl);
  }

  _isPluginLoaded(pathOrUrl: string) {
    appContext.reportingService.trackApi(fakeApi, 'global', '_isPluginLoaded');
    return getPluginLoader().isPluginLoaded(pathOrUrl);
  }

  /**
   * Enabling EventEmitter interface on Gerrit.
   *
   * This will enable to signal across different parts of js code without relying on DOM,
   * including core to core, plugin to plugin and also core to plugin.
   *
   * @example
   *
   * // Emit this event from pluginA
   * Gerrit.install(pluginA => {
   *   fetch("some-api").then(() => {
   *     Gerrit.on("your-special-event", {plugin: pluginA});
   *   });
   * });
   *
   * // Listen on your-special-event from pluginB
   * Gerrit.install(pluginB => {
   *   Gerrit.on("your-special-event", ({plugin}) => {
   *     // do something, plugin is pluginA
   *   });
   * });
   */
  addListener(eventName: string, cb: EventCallback) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'addListener');
    return appContext.eventEmitter.addListener(eventName, cb);
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  dispatch(eventName: string, detail: any) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'dispatch');
    return appContext.eventEmitter.dispatch(eventName, detail);
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  emit(eventName: string, detail: any) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'emit');
    return appContext.eventEmitter.emit(eventName, detail);
  }

  off(eventName: string, cb: EventCallback) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'off');
    return appContext.eventEmitter.off(eventName, cb);
  }

  on(eventName: string, cb: EventCallback) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'on');
    return appContext.eventEmitter.on(eventName, cb);
  }

  once(eventName: string, cb: EventCallback) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'once');
    return appContext.eventEmitter.once(eventName, cb);
  }

  removeAllListeners(eventName: string) {
    appContext.reportingService.trackApi(
      fakeApi,
      'global',
      'removeAllListeners'
    );
    return appContext.eventEmitter.removeAllListeners(eventName);
  }

  removeListener(eventName: string, cb: EventCallback) {
    appContext.reportingService.trackApi(fakeApi, 'global', 'removeListener');
    return appContext.eventEmitter.removeListener(eventName, cb);
  }
}
