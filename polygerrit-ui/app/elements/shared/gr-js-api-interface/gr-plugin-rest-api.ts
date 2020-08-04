/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import {
  ErrorCallback,
  RestApiService,
} from '../../../services/services/gr-rest-api/gr-rest-api';

/**
 * Enum for all http methods used in Gerrit.
 * TODO(TS): might move to common later.
 */
export enum HttpMethod {
  POST = 'POST',
  GET = 'GET',
  DELETE = 'DELETE',
  PUT = 'PUT',
}

let restApi: RestApiService | null = null;

export function _testOnlyResetRestApi() {
  restApi = null;
}

function getRestApi(): RestApiService {
  if (!restApi) {
    restApi = (document.createElement(
      'gr-rest-api-interface'
    ) as unknown) as RestApiService;
  }
  return restApi;
}

export class GrPluginRestApi {
  constructor(private readonly prefix = '') {}

  getLoggedIn() {
    return getRestApi().getLoggedIn();
  }

  getVersion() {
    return getRestApi().getVersion();
  }

  getConfig() {
    return getRestApi().getConfig();
  }

  invalidateReposCache() {
    getRestApi().invalidateReposCache();
  }

  getAccount() {
    return getRestApi().getAccount();
  }

  getAccountCapabilities(capabilities: string[]) {
    return getRestApi().getAccountCapabilities(capabilities);
  }

  getRepos(filter: string, reposPerPage: number, offset?: number) {
    return getRestApi().getRepos(filter, reposPerPage, offset);
  }

  /**
   * Fetch and return native browser REST API Response.
   */
  fetch(
    method: HttpMethod,
    url: string,
    payload?: unknown,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<Response> {
    return getRestApi().send(
      method,
      this.prefix + url,
      payload,
      errFn,
      contentType
    );
  }

  /**
   * Fetch and parse REST API response, if request succeeds.
   */
  send(
    method: HttpMethod,
    url: string,
    payload?: unknown,
    errFn?: ErrorCallback,
    contentType?: string
  ) {
    return this.fetch(method, url, payload, errFn, contentType).then(
      response => {
        if (response.status < 200 || response.status >= 300) {
          return response.text().then(text => {
            if (text) {
              return Promise.reject(new Error(text));
            } else {
              return Promise.reject(new Error(`${response.status}`));
            }
          });
        } else {
          return getRestApi().getResponseObject(response);
        }
      }
    );
  }

  get(url: string) {
    return this.send(HttpMethod.GET, url);
  }

  post(
    url: string,
    payload?: unknown,
    errFn?: ErrorCallback,
    contentType?: string
  ) {
    return this.send(HttpMethod.POST, url, payload, errFn, contentType);
  }

  put(
    url: string,
    payload?: unknown,
    errFn?: ErrorCallback,
    contentType?: string
  ) {
    return this.send(HttpMethod.PUT, url, payload, errFn, contentType);
  }

  delete(url: string) {
    return this.fetch(HttpMethod.DELETE, url).then(response => {
      if (response.status !== 204) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(new Error(text));
          } else {
            return Promise.reject(new Error(`${response.status}`));
          }
        });
      }
      return response;
    });
  }
}
