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
import {getRestAPI, send} from './gr-api-utils';
import {appContext} from '../../../services/app-context';
import {PluginApi} from '../../plugins/gr-plugin-types';
import {HttpMethod} from '../../../constants/constants';
import {RequestPayload} from '../../../types/common';
import {
  EventCallback,
  EventEmitterService,
} from '../../../services/gr-event-interface/gr-event-interface';

interface GerritGlobal extends EventEmitterService {
  flushPreinstalls?(): void;
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
  _isPluginPreloaded(pathOrUrl: string): boolean;
  _isPluginEnabled(pathOrUrl: string): boolean;
  _isPluginLoaded(pathOrUrl: string): boolean;
  _eventEmitter: EventEmitterService;
  _customStyleSheet: CSSStyleSheet;
}

/**
 * Trigger the preinstalls for bundled plugins.
 * This needs to happen before Gerrit as plugin bundle overrides the Gerrit.
 */
function flushPreinstalls() {
  const Gerrit = window.Gerrit as GerritGlobal;
  if (Gerrit.flushPreinstalls) {
    Gerrit.flushPreinstalls();
  }
}
export const _testOnly_flushPreinstalls = flushPreinstalls;

export function initGerritPluginApi() {
  window.Gerrit = {};
  flushPreinstalls();
  initGerritPluginsMethods(window.Gerrit as GerritGlobal);
  // Preloaded plugins should be installed after Gerrit.install() is set,
  // since plugin preloader substitutes Gerrit.install() temporarily.
  // (Gerrit.install() is set in initGerritPluginsMethods)
  getPluginLoader().installPreloadedPlugins();
}

export function _testOnly_initGerritPluginApi(): GerritGlobal {
  initGerritPluginApi();
  return window.Gerrit as GerritGlobal;
}

export function deprecatedDelete(
  url: string,
  callback?: (response: Response) => void
) {
  console.warn('.delete() is deprecated! Use plugin.restApi().delete()');
  return getRestAPI()
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

function initGerritPluginsMethods(globalGerritObj: GerritGlobal) {
  /**
   * @deprecated Use plugin.styles().css(rulesStr) instead. Please, consult
   * the documentation how to replace it accordingly.
   */
  globalGerritObj.css = (rulesStr: string) => {
    console.warn(
      'Gerrit.css(rulesStr) is deprecated!',
      'Use plugin.styles().css(rulesStr)'
    );
    if (!globalGerritObj._customStyleSheet) {
      const styleEl = document.createElement('style');
      document.head.appendChild(styleEl);
      globalGerritObj._customStyleSheet = styleEl.sheet!;
    }

    const name = `__pg_js_api_class_${globalGerritObj._customStyleSheet.cssRules.length}`;
    globalGerritObj._customStyleSheet.insertRule(
      '.' + name + '{' + rulesStr + '}',
      0
    );
    return name;
  };

  globalGerritObj.install = (callback, opt_version, opt_src) => {
    getPluginLoader().install(callback, opt_version, opt_src);
  };

  globalGerritObj.getLoggedIn = () => {
    console.warn(
      'Gerrit.getLoggedIn() is deprecated! ' +
        'Use plugin.restApi().getLoggedIn()'
    );
    return document.createElement('gr-rest-api-interface').getLoggedIn();
  };

  globalGerritObj.get = (
    url: string,
    callback?: (response: unknown) => void
  ) => {
    console.warn('.get() is deprecated! Use plugin.restApi().get()');
    send(HttpMethod.GET, url, callback);
  };

  globalGerritObj.post = (
    url: string,
    payload?: RequestPayload,
    callback?: (response: unknown) => void
  ) => {
    console.warn('.post() is deprecated! Use plugin.restApi().post()');
    send(HttpMethod.POST, url, callback, payload);
  };

  globalGerritObj.put = (
    url: string,
    payload?: RequestPayload,
    callback?: (response: unknown) => void
  ) => {
    console.warn('.put() is deprecated! Use plugin.restApi().put()');
    send(HttpMethod.PUT, url, callback, payload);
  };

  globalGerritObj.delete = (
    url: string,
    callback?: (response: Response) => void
  ) => {
    deprecatedDelete(url, callback);
  };

  globalGerritObj.awaitPluginsLoaded = () => {
    return getPluginLoader().awaitPluginsLoaded();
  };

  // TODO(taoalpha): consider removing these proxy methods
  // and using getPluginLoader() directly
  globalGerritObj._loadPlugins = (plugins, opt_option) => {
    getPluginLoader().loadPlugins(plugins, opt_option);
  };

  globalGerritObj._arePluginsLoaded = () => {
    return getPluginLoader().arePluginsLoaded();
  };

  globalGerritObj._isPluginPreloaded = url => {
    return getPluginLoader().isPluginPreloaded(url);
  };

  globalGerritObj._isPluginEnabled = pathOrUrl => {
    return getPluginLoader().isPluginEnabled(pathOrUrl);
  };

  globalGerritObj._isPluginLoaded = pathOrUrl => {
    return getPluginLoader().isPluginLoaded(pathOrUrl);
  };

  const eventEmitter = appContext.eventEmitter;

  // TODO(taoalpha): List all internal supported event names.
  // Also convert this to inherited class once we move Gerrit to class.
  globalGerritObj._eventEmitter = eventEmitter;
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
   * // Listen on your-special-event from pluignB
   * Gerrit.install(pluginB => {
   *   Gerrit.on("your-special-event", ({plugin}) => {
   *     // do something, plugin is pluginA
   *   });
   * });
   */
  globalGerritObj.addListener = (eventName: string, cb: EventCallback) =>
    eventEmitter.addListener(eventName, cb);
  globalGerritObj.dispatch = (eventName: string, detail: any) =>
    eventEmitter.dispatch(eventName, detail);
  globalGerritObj.emit = (eventName: string, detail: any) =>
    eventEmitter.emit(eventName, detail);
  globalGerritObj.off = (eventName: string, cb: EventCallback) =>
    eventEmitter.off(eventName, cb);
  globalGerritObj.on = (eventName: string, cb: EventCallback) =>
    eventEmitter.on(eventName, cb);
  globalGerritObj.once = (eventName: string, cb: EventCallback) =>
    eventEmitter.once(eventName, cb);
  globalGerritObj.removeAllListeners = (eventName: string) =>
    eventEmitter.removeAllListeners(eventName);
  globalGerritObj.removeListener = (eventName: string, cb: EventCallback) =>
    eventEmitter.removeListener(eventName, cb);
}
