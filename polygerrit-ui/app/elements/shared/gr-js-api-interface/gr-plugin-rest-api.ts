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

type ErrorFunction<T extends Response> = (response: T, error: Error) => void;

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

// TODO(TS): use GrRestApiInterface once gr-rest-api-interface migrated
let restApi: any;

export function _testOnlyResetRestApi() {
  restApi = null;
}

function getRestApi() {
  if (!restApi) {
    restApi = document.createElement('gr-rest-api-interface');
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
  fetch<T extends Response, K>(
    method: HttpMethod,
    url: string,
    payload?: K,
    errFn?: ErrorFunction<T>,
    contentType?: string
  ): Promise<T> {
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
  send<T extends Response, K>(
    method: HttpMethod,
    url: string,
    payload?: K,
    errFn?: ErrorFunction<T>,
    contentType?: string
  ) {
    return this.fetch<T, K>(method, url, payload, errFn, contentType).then(
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

  get<T extends Response>(url: string) {
    return this.send<T, undefined>(HttpMethod.GET, url);
  }

  post<T extends Response, K>(
    url: string,
    payload?: K,
    errFn?: ErrorFunction<T>,
    contentType?: string
  ) {
    return this.send<T, K>(HttpMethod.POST, url, payload, errFn, contentType);
  }

  put<T extends Response, K>(
    url: string,
    payload?: K,
    errFn?: ErrorFunction<T>,
    contentType?: string
  ) {
    return this.send<T, K>(HttpMethod.PUT, url, payload, errFn, contentType);
  }

  delete<T extends Response>(url: string) {
    return this.fetch<T, undefined>(HttpMethod.DELETE, url).then(response => {
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
