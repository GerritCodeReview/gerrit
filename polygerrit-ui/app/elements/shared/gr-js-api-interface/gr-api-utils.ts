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
import {HttpMethod} from '../../../constants/constants';
import {RequestPayload} from '../../../types/common';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';

export const PLUGIN_LOADING_TIMEOUT_MS = 10000;

export const THEME_JS = '/static/gerrit-theme.js';

const THEME_NAME = 'gerrit-theme';

export function isThemeFile(path: string) {
  return path.endsWith(THEME_JS);
}

/**
 * Retrieves the name of the plugin base on the url.
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
  const base = getBaseUrl();
  let pathname = url.pathname.replace(base, '');
  // Load from ASSETS_PATH
  if (window.ASSETS_PATH && url.href.includes(window.ASSETS_PATH)) {
    pathname = url.href.replace(window.ASSETS_PATH, '');
  }

  if (isThemeFile(pathname)) return THEME_NAME;

  if (!pathname.startsWith('/plugins')) {
    console.warn(
      'Plugin not being loaded from /plugins base path:',
      url.href,
      '— Unable to determine name.'
    );
    return null;
  }

  // Pathname should normally look like this:
  // /plugins/PLUGINNAME/static/SCRIPTNAME.js
  // Or:
  // /plugins/PLUGINNAME.js
  // TODO(taoalpha): guard with a regex
  return pathname.split('/')[2].split('.')[0];
}

export function send(
  restApiService: RestApiService,
  method: HttpMethod,
  url: string,
  opt_callback?: (response: unknown) => void,
  opt_payload?: RequestPayload
) {
  return restApiService
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
        return restApiService.getResponseObject(response);
      }
    })
    .then(response => {
      if (opt_callback) {
        opt_callback(response);
      }
      return response;
    });
}
