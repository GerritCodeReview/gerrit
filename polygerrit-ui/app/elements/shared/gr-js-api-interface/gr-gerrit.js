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

import {pluginLoader} from './gr-plugin-loader.js';
import {getRestAPI, send} from './gr-api-utils.js';
import {appContext} from '../../../services/app-context.js';
import {initAppContext} from '../../../services/app-context-init.js';

/**
 * Trigger the preinstalls for bundled plugins.
 * This needs to happen before Gerrit as plugin bundle overrides the Gerrit.
 */
function flushPreinstalls() {
  if (window.Gerrit.flushPreinstalls) {
    window.Gerrit.flushPreinstalls();
  }
}
export const _testOnly_flushPreinstalls = flushPreinstalls;

export function initGerritPluginApi(appContext) {
  window.Gerrit = window.Gerrit || {};
  flushPreinstalls();
  initGerritPluginsMethods(window.Gerrit, appContext);
  // Preloaded plugins should be installed after Gerrit.install() is set,
  // since plugin preloader substitutes Gerrit.install() temporarily.
  // (Gerrit.install() is set in initGerritPluginsMethods)
  pluginLoader.installPreloadedPlugins();
}

export function _testOnly_initGerritPluginApi() {
  initAppContext();
  initGerritPluginApi(appContext);
  return window.Gerrit;
}

export function deprecatedDelete(url, opt_callback) {
  console.warn('.delete() is deprecated! Use plugin.restApi().delete()');
  return getRestAPI().send('DELETE', url)
      .then(response => {
        if (response.status !== 204) {
          return response.text().then(text => {
            if (text) {
              return Promise.reject(new Error(text));
            } else {
              return Promise.reject(new Error(response.status));
            }
          });
        }
        if (opt_callback) {
          opt_callback(response);
        }
        return response;
      });
}

function initGerritPluginsMethods(globalGerritObj, appContext) {
  /**
   * @deprecated Use plugin.styles().css(rulesStr) instead. Please, consult
   * the documentation how to replace it accordingly.
   */
  globalGerritObj.css = function(rulesStr) {
    console.warn('Gerrit.css(rulesStr) is deprecated!',
        'Use plugin.styles().css(rulesStr)');
    if (!globalGerritObj._customStyleSheet) {
      const styleEl = document.createElement('style');
      document.head.appendChild(styleEl);
      globalGerritObj._customStyleSheet = styleEl.sheet;
    }

    const name = '__pg_js_api_class_' +
        globalGerritObj._customStyleSheet.cssRules.length;
    globalGerritObj._customStyleSheet
        .insertRule('.' + name + '{' + rulesStr + '}', 0);
    return name;
  };

  globalGerritObj.install = function(callback, opt_version, opt_src) {
    pluginLoader.install(callback, opt_version, opt_src);
  };

  globalGerritObj.getLoggedIn = function() {
    console.warn('Gerrit.getLoggedIn() is deprecated! ' +
        'Use plugin.restApi().getLoggedIn()');
    return document.createElement('gr-rest-api-interface').getLoggedIn();
  };

  globalGerritObj.get = function(url, callback) {
    console.warn('.get() is deprecated! Use plugin.restApi().get()');
    send('GET', url, callback);
  };

  globalGerritObj.post = function(url, payload, callback) {
    console.warn('.post() is deprecated! Use plugin.restApi().post()');
    send('POST', url, callback, payload);
  };

  globalGerritObj.put = function(url, payload, callback) {
    console.warn('.put() is deprecated! Use plugin.restApi().put()');
    send('PUT', url, callback, payload);
  };

  globalGerritObj.delete = function(url, opt_callback) {
    deprecatedDelete(url, opt_callback);
  };

  globalGerritObj.awaitPluginsLoaded = function() {
    return pluginLoader.awaitPluginsLoaded();
  };

  // TODO(taoalpha): consider removing these proxy methods
  // and using pluginLoader directly
  globalGerritObj._loadPlugins = function(plugins, opt_option) {
    pluginLoader.loadPlugins(plugins, opt_option);
  };

  globalGerritObj._arePluginsLoaded = function() {
    return pluginLoader.arePluginsLoaded();
  };

  globalGerritObj._isPluginPreloaded = function(url) {
    return pluginLoader.isPluginPreloaded(url);
  };

  globalGerritObj._isPluginEnabled = function(pathOrUrl) {
    return pluginLoader.isPluginEnabled(pathOrUrl);
  };

  globalGerritObj._isPluginLoaded = function(pathOrUrl) {
    return pluginLoader.isPluginLoaded(pathOrUrl);
  };

  const gerritEventEmitter = appContext.gerritEventEmitter;

  // TODO(taoalpha): List all internal supported event names.
  // Also convert this to inherited class once we move Gerrit to class.
  globalGerritObj._eventEmitter = gerritEventEmitter;
  ['addListener',
    'dispatch',
    'emit',
    'off',
    'on',
    'once',
    'removeAllListeners',
    'removeListener',
  ].forEach(method => {
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
    globalGerritObj[method] = gerritEventEmitter[method]
        .bind(gerritEventEmitter);
  });
}
