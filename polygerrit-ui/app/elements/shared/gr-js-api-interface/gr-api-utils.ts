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

import {getBaseUrl} from '../../../utils/url-util';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';

export const PRELOADED_PROTOCOL = 'preloaded:';
export const PLUGIN_LOADING_TIMEOUT_MS = 10000;

let _restAPI: RestApiService | undefined;
export function getRestAPI() {
  if (!_restAPI) {
    _restAPI = (document.createElement(
      'gr-rest-api-interface'
    ) as unknown) as RestApiService;
  }
  return _restAPI;
}

/**
 * Retrieves the name of the plugin base on the url.
 *
 * @param {string|URL} url
 */
export function getPluginNameFromUrl(url: URL | string) {
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
  const base = getBaseUrl();
  let pathname = url.pathname.replace(base, '');
  // Load from ASSETS_PATH
  if (window.ASSETS_PATH && url.href.includes(window.ASSETS_PATH)) {
    pathname = url.href.replace(window.ASSETS_PATH, '');
  }
  // Site theme is server from predefined path.
  if (
    ['/static/gerrit-theme.html', '/static/gerrit-theme.js'].includes(pathname)
  ) {
    return 'gerrit-theme';
  } else if (!pathname.startsWith('/plugins')) {
    console.warn(
      'Plugin not being loaded from /plugins base path:',
      url.href,
      '— Unable to determine name.'
    );
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
export function send(
  method: string,
  url: string,
  opt_callback?: (response: unknown) => void,
  opt_payload?: unknown
) {
  return getRestAPI()
    .send(method, url, opt_payload)
    .then(response => {
      if (response.status < 200 || response.status >= 300) {
        return response.text().then((text: string | undefined) => {
          if (text) {
            return Promise.reject(new Error(text));
          } else {
            return Promise.reject(new Error(`${response.status}`));
          }
        });
      } else {
        return getRestAPI().getResponseObject(response);
      }
    })
    .then(response => {
      if (opt_callback) {
        opt_callback(response);
      }
      return response;
    });
}

// TEST only methods / properties

export function testOnly_resetInternalState() {
  _restAPI = undefined;
}
