/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {HttpMethod} from '../../../constants/constants';
import {RequestPayload} from '../../../types/common';
import {ErrorCallback, RestPluginApi} from '../../../api/rest';
import {PluginApi} from '../../../api/plugin';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {
  readJSONResponsePayload,
  throwingErrorCallback,
} from '../gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

async function getErrorMessage(response: Response): Promise<string> {
  const text = await response.text();
  return text || `${response.status}`;
}

export class GrPluginRestApi implements RestPluginApi {
  constructor(
    private readonly restApi: RestApiService,
    private readonly reporting: ReportingService,
    readonly plugin: PluginApi,
    private readonly prefix = ''
  ) {
    this.reporting.trackApi(this.plugin, 'rest', 'constructor');
  }

  getLoggedIn() {
    this.reporting.trackApi(this.plugin, 'rest', 'getLoggedIn');
    return this.restApi.getLoggedIn();
  }

  getVersion() {
    this.reporting.trackApi(this.plugin, 'rest', 'getVersion');
    return this.restApi.getVersion();
  }

  getConfig() {
    this.reporting.trackApi(this.plugin, 'rest', 'getConfig');
    return this.restApi.getConfig();
  }

  invalidateReposCache() {
    this.reporting.trackApi(this.plugin, 'rest', 'invalidateReposCache');
    this.restApi.invalidateReposCache();
  }

  getAccount() {
    this.reporting.trackApi(this.plugin, 'rest', 'getAccount');
    return this.restApi.getAccount();
  }

  getRepos(filter: string, reposPerPage: number, offset?: number) {
    this.reporting.trackApi(this.plugin, 'rest', 'getRepos');
    return this.restApi.getRepos(filter, reposPerPage, offset);
  }

  fetch(
    method: HttpMethod,
    url: string,
    payload?: RequestPayload,
    errFn?: undefined,
    contentType?: string
  ): Promise<Response>;

  fetch(
    method: HttpMethod,
    url: string,
    payload: RequestPayload | undefined,
    errFn: ErrorCallback,
    contentType?: string
  ): Promise<Response | void>;

  fetch(
    method: HttpMethod,
    url: string,
    payload: RequestPayload | undefined,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<Response | void>;

  /**
   * Fetch and return native browser REST API Response.
   */
  fetch(
    method: HttpMethod,
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<Response | void> {
    this.reporting.trackApi(this.plugin, 'rest', 'fetch');
    return this.restApi.send(
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
  send<T>(
    method: HttpMethod,
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ) {
    this.reporting.trackApi(this.plugin, 'rest', 'send');
    return this.fetch(
      method,
      url,
      payload,
      errFn ?? throwingErrorCallback,
      contentType
    ).then(response => {
      // Will typically not happen. The response can only be unset, if the
      // errFn handles the error and then returns void or undefined or null.
      // But the errFn above always throws.
      if (!response) {
        throw new Error('plugin rest-api call failed');
      }
      // Will typically not happen. errFn will have dealt with that and the
      // caller will get a rejected promise already.
      if (response.status < 200 || response.status >= 300) {
        return getErrorMessage(response).then(msg =>
          Promise.reject(new Error(msg))
        );
      } else {
        return readJSONResponsePayload(response).then(
          obj => obj.parsed
        ) as Promise<T>;
      }
    });
  }

  get<T>(url: string) {
    this.reporting.trackApi(this.plugin, 'rest', 'get');
    return this.send<T>(HttpMethod.GET, url);
  }

  post<T>(
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ) {
    this.reporting.trackApi(this.plugin, 'rest', 'post');
    return this.send<T>(HttpMethod.POST, url, payload, errFn, contentType);
  }

  put<T>(
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ) {
    this.reporting.trackApi(this.plugin, 'rest', 'put');
    return this.send<T>(HttpMethod.PUT, url, payload, errFn, contentType);
  }

  delete(url: string) {
    this.reporting.trackApi(this.plugin, 'rest', 'delete');
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
