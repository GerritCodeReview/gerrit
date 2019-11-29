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

(function(window) {
  'use strict';

  const PRELOADED_PROTOCOL = 'preloaded:';
  const PLUGIN_LOADING_TIMEOUT_MS = 10000;

  let _restAPI;
  function getRestAPI() {
    if (!_restAPI) {
      _restAPI = document.createElement('gr-rest-api-interface');
    }
    return _restAPI;
  }

  function getBaseUrl() {
    return Gerrit.BaseUrlBehavior.getBaseUrl();
  }

  /**
   * Retrieves the name of the plugin base on the url.
   * @param {string|URL} url
   */
  function getPluginNameFromUrl(url) {
    if (!(url instanceof URL)) {
      try {
        url = new URL(url);
      } catch (e) {
        console.warn(e);
        return null;
      }
    }
    if (url.protocol === PRELOADED_PROTOCOL) {
      return url.pathname;
    }
    const base = Gerrit.BaseUrlBehavior.getBaseUrl();
    let pathname = url.pathname.replace(base, '');
    // Load from ASSETS_PATH
    if (window.ASSETS_PATH && url.href.includes(window.ASSETS_PATH)) {
      pathname = url.href.replace(window.ASSETS_PATH, '');
    }
    // Site theme is server from predefined path.
    if (pathname === '/static/gerrit-theme.html') {
      return 'gerrit-theme';
    } else if (!pathname.startsWith('/plugins')) {
      console.warn('Plugin not being loaded from /plugins base path:',
          url.href, '— Unable to determine name.');
      return null;
    }

    // Pathname should normally look like this:
    // /plugins/PLUGINNAME/static/SCRIPTNAME.html
    // Or, for app/samples:
    // /plugins/PLUGINNAME.html
    // TODO(taoalpha): guard with a regex
    return pathname.split('/')[2].split('.')[0];
  }

  // TODO(taoalpha): to be deprecated.
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

  // TEST only methods / properties

  function testOnly_resetInternalState() {
    _restAPI = undefined;
  }

  window._apiUtils = {
    getPluginNameFromUrl,
    send,
    getRestAPI,
    getBaseUrl,
    PRELOADED_PROTOCOL,
    PLUGIN_LOADING_TIMEOUT_MS,

    // TEST only methods
    testOnly_resetInternalState,
  };
})(window);