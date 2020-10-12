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
import {getBaseUrl} from '../../../../utils/url-util';
import {
  CancelConditionCallback,
  ErrorCallback,
  RestApiService,
} from '../../../../services/services/gr-rest-api/gr-rest-api';
import {
  AuthRequestInit,
  AuthService,
} from '../../../../services/gr-auth/gr-auth';
import {hasOwnProperty} from '../../../../utils/common-util';
import {
  AccountDetailInfo,
  EmailInfo,
  ParsedJSON,
  RequestPayload,
} from '../../../../types/common';
import {HttpMethod} from '../../../../constants/constants';

const JSON_PREFIX = ")]}'";

export interface ResponsePayload {
  // TODO(TS): readResponsePayload can assign null to the parsed property if
  // it can't parse input data. However polygerrit assumes in many places
  // that the parsed property can't be null. We should update
  // readResponsePayload method and reject a promise instead of assigning
  // null to the parsed property
  parsed: ParsedJSON; // Can be null!!! See comment above
  raw: string;
}

/**
 * Wrapper around Map for caching server responses. Site-based so that
 * changes to CANONICAL_PATH will result in a different cache going into
 * effect.
 */
export class SiteBasedCache {
  // TODO(TS): Type looks unusual. Fix it.
  // Container of per-canonical-path caches.
  private readonly _data = new Map<
    string | undefined,
    unknown | Map<string, ParsedJSON | null>
  >();

  constructor() {
    if (window.INITIAL_DATA) {
      // Put all data shipped with index.html into the cache. This makes it
      // so that we spare more round trips to the server when the app loads
      // initially.
      Object.entries(window.INITIAL_DATA).forEach(e =>
        this._cache().set(e[0], (e[1] as unknown) as ParsedJSON)
      );
    }
  }

  // Returns the cache for the current canonical path.
  _cache(): Map<string, unknown> {
    if (!this._data.has(window.CANONICAL_PATH)) {
      this._data.set(window.CANONICAL_PATH, new Map());
    }
    return this._data.get(window.CANONICAL_PATH) as Map<
      string,
      ParsedJSON | null
    >;
  }

  has(key: string) {
    return this._cache().has(key);
  }

  get(key: '/accounts/self/emails'): EmailInfo[] | null;

  get(key: '/accounts/self/detail'): AccountDetailInfo[] | null;

  get(key: string): ParsedJSON | null;

  get(key: string): unknown {
    return this._cache().get(key);
  }

  set(key: '/accounts/self/emails', value: EmailInfo[]): void;

  set(key: '/accounts/self/detail', value: AccountDetailInfo[]): void;

  set(key: string, value: ParsedJSON | null): void;

  set(key: string, value: unknown) {
    this._cache().set(key, value);
  }

  delete(key: string) {
    this._cache().delete(key);
  }

  invalidatePrefix(prefix: string) {
    const newMap = new Map();
    for (const [key, value] of this._cache().entries()) {
      if (!key.startsWith(prefix)) {
        newMap.set(key, value);
      }
    }
    this._data.set(window.CANONICAL_PATH, newMap);
  }
}

type FetchPromisesCacheData = {
  [url: string]: Promise<ParsedJSON | undefined> | undefined;
};

export class FetchPromisesCache {
  private _data: FetchPromisesCacheData;

  constructor() {
    this._data = {};
  }

  public testOnlyGetData() {
    return this._data;
  }

  /**
   * @return true only if a value for a key sets and it is not undefined
   */
  has(key: string): boolean {
    return !!this._data[key];
  }

  get(key: string) {
    return this._data[key];
  }

  /**
   * @param value a Promise to store in the cache. Pass undefined value to
   *     mark key as deleted.
   */
  set(key: string, value: Promise<ParsedJSON | undefined> | undefined) {
    this._data[key] = value;
  }

  invalidatePrefix(prefix: string) {
    const newData: FetchPromisesCacheData = {};
    Object.entries(this._data).forEach(([key, value]) => {
      if (!key.startsWith(prefix)) {
        newData[key] = value;
      }
    });
    this._data = newData;
  }
}
export type FetchParams = {
  [name: string]: string[] | string | number | boolean | undefined | null;
};

interface SendRequestBase {
  method: HttpMethod | undefined;
  body?: RequestPayload;
  contentType?: string;
  headers?: Record<string, string>;
  url: string;
  reportUrlAsIs?: boolean;
  anonymizedUrl?: string;
  errFn?: ErrorCallback;
}

export interface SendRawRequest extends SendRequestBase {
  parseResponse?: false | null;
}

export interface SendJSONRequest extends SendRequestBase {
  parseResponse: true;
}

export type SendRequest = SendRawRequest | SendJSONRequest;

export interface FetchRequest {
  url: string;
  fetchOptions?: AuthRequestInit;
  anonymizedUrl?: string;
}

export interface FetchJSONRequest extends FetchRequest {
  reportUrlAsIs?: boolean;
  params?: FetchParams;
  cancelCondition?: CancelConditionCallback;
  errFn?: ErrorCallback;
}

// export function isRequestWithCancel<T extends FetchJSONRequest>(
//   x: T
// ): x is T & RequestWithCancel {
//   return !!(x as RequestWithCancel).cancelCondition;
// }
//
// export function isRequestWithErrFn<T extends FetchJSONRequest>(
//   x: T
// ): x is T & RequestWithErrFn {
//   return !!(x as RequestWithErrFn).errFn;
// }

export class GrRestApiHelper {
  constructor(
    private readonly _cache: SiteBasedCache,
    private readonly _auth: AuthService,
    private readonly _fetchPromisesCache: FetchPromisesCache,
    private readonly _restApiInterface: RestApiService
  ) {}

  /**
   * Wraps calls to the underlying authenticated fetch function (_auth.fetch)
   * with timing and logging.
s   */
  fetch(req: FetchRequest): Promise<Response> {
    const start = Date.now();
    const xhr = this._auth.fetch(req.url, req.fetchOptions);

    // Log the call after it completes.
    xhr.then(res => this._logCall(req, start, res ? res.status : null));

    // Return the XHR directly (without the log).
    return xhr;
  }

  /**
   * Log information about a REST call. Because the elapsed time is determined
   * by this method, it should be called immediately after the request
   * finishes.
   *
   * @param startTime the time that the request was started.
   * @param status the HTTP status of the response. The status value
   *     is used here rather than the response object so there is no way this
   *     method can read the body stream.
   */
  private _logCall(
    req: FetchRequest,
    startTime: number,
    status: number | null
  ) {
    const method =
      req.fetchOptions && req.fetchOptions.method
        ? req.fetchOptions.method
        : 'GET';
    const endTime = Date.now();
    const elapsed = endTime - startTime;
    const startAt = new Date(startTime);
    const endAt = new Date(endTime);
    console.info(
      [
        'HTTP',
        status,
        method,
        `${elapsed}ms`,
        req.anonymizedUrl || req.url,
        `(${startAt.toISOString()}, ${endAt.toISOString()})`,
      ].join(' ')
    );
    if (req.anonymizedUrl) {
      this.dispatchEvent(
        new CustomEvent('rpc-log', {
          detail: {status, method, elapsed, anonymizedUrl: req.anonymizedUrl},
          composed: true,
          bubbles: true,
        })
      );
    }
  }

  /**
   * Fetch JSON from url provided.
   * Returns a Promise that resolves to a native Response.
   * Doesn't do error checking. Supports cancel condition. Performs auth.
   * Validates auth expiry errors.
   *
   * @return Promise which resolves to undefined if cancelCondition returns true
   *     and resolves to Response otherwise
   */
  fetchRawJSON(req: FetchJSONRequest): Promise<Response | undefined> {
    const urlWithParams = this.urlWithParams(req.url, req.params);
    const fetchReq: FetchRequest = {
      url: urlWithParams,
      fetchOptions: req.fetchOptions,
      anonymizedUrl: req.reportUrlAsIs ? urlWithParams : req.anonymizedUrl,
    };
    return this.fetch(fetchReq)
      .then((res: Response) => {
        if (req.cancelCondition && req.cancelCondition()) {
          if (res.body) {
            res.body.cancel();
          }
          return;
        }
        return res;
      })
      .catch(err => {
        if (req.errFn) {
          req.errFn.call(undefined, null, err);
        } else {
          this.dispatchEvent(
            new CustomEvent('network-error', {
              detail: {error: err},
              composed: true,
              bubbles: true,
            })
          );
        }
        throw err;
      });
  }

  /**
   * Fetch JSON from url provided.
   * Returns a Promise that resolves to a parsed response.
   * Same as {@link fetchRawJSON}, plus error handling.
   *
   * @param noAcceptHeader - don't add default accept json header
   */
  fetchJSON(
    req: FetchJSONRequest,
    noAcceptHeader?: boolean
  ): Promise<ParsedJSON | undefined> {
    if (!noAcceptHeader) {
      req = this.addAcceptJsonHeader(req);
    }
    return this.fetchRawJSON(req).then(response => {
      if (!response) {
        return;
      }
      if (!response.ok) {
        if (req.errFn) {
          req.errFn.call(null, response);
          return;
        }
        this.dispatchEvent(
          new CustomEvent('server-error', {
            detail: {request: req, response},
            composed: true,
            bubbles: true,
          })
        );
        return;
      }
      return this.getResponseObject(response);
    });
  }

  urlWithParams(url: string, fetchParams?: FetchParams): string {
    if (!fetchParams) {
      return getBaseUrl() + url;
    }

    const params: Array<string | number | boolean> = [];
    for (const p in fetchParams) {
      if (!hasOwnProperty(fetchParams, p)) {
        continue;
      }
      const paramValue = fetchParams[p];
      // TODO(TS): Replace == null with === and check for null and undefined
      // eslint-disable-next-line eqeqeq
      if (paramValue == null) {
        params.push(this.encodeRFC5987(p));
        continue;
      }
      // TODO(TS): Unclear, why do we need the following code.
      // If paramValue can be array - we should either fix FetchParams type
      // or convert the array to a string before calling urlWithParams method.
      const paramValueAsArray = ([] as Array<string | number | boolean>).concat(
        paramValue
      );
      for (const value of paramValueAsArray) {
        params.push(`${this.encodeRFC5987(p)}=${this.encodeRFC5987(value)}`);
      }
    }
    return getBaseUrl() + url + '?' + params.join('&');
  }

  // Backend encode url in RFC5987 and frontend needs to do same to match
  // queries for preloading queries
  encodeRFC5987(uri: string | number | boolean) {
    return encodeURIComponent(uri).replace(
      /['()*]/g,
      c => '%' + c.charCodeAt(0).toString(16)
    );
  }

  getResponseObject(response: Response): Promise<ParsedJSON> {
    return this.readResponsePayload(response).then(payload => payload.parsed);
  }

  readResponsePayload(response: Response): Promise<ResponsePayload> {
    return response.text().then(text => {
      let result;
      try {
        result = this.parsePrefixedJSON(text);
      } catch (_) {
        result = null;
      }
      // TODO(TS): readResponsePayload can assign null to the parsed property if
      // it can't parse input data. However polygerrit assumes in many places
      // that the parsed property can't be null. We should update
      // readResponsePayload method and reject a promise instead of assigning
      // null to the parsed property
      return {parsed: result!, raw: text};
    });
  }

  parsePrefixedJSON(jsonWithPrefix: string): ParsedJSON {
    return JSON.parse(
      jsonWithPrefix.substring(JSON_PREFIX.length)
    ) as ParsedJSON;
  }

  addAcceptJsonHeader(req: FetchJSONRequest) {
    if (!req.fetchOptions) req.fetchOptions = {};
    if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();
    if (!req.fetchOptions.headers.has('Accept')) {
      req.fetchOptions.headers.append('Accept', 'application/json');
    }
    return req;
  }

  dispatchEvent(type: Event, detail?: unknown): boolean {
    return this._restApiInterface.dispatchEvent(type, detail);
  }

  fetchCacheURL(req: FetchJSONRequest): Promise<ParsedJSON | undefined> {
    if (this._fetchPromisesCache.has(req.url)) {
      return this._fetchPromisesCache.get(req.url)!;
    }
    // TODO(andybons): Periodic cache invalidation.
    if (this._cache.has(req.url)) {
      return Promise.resolve(this._cache.get(req.url)!);
    }
    this._fetchPromisesCache.set(
      req.url,
      this.fetchJSON(req)
        .then(response => {
          if (response !== undefined) {
            this._cache.set(req.url, response);
          }
          this._fetchPromisesCache.set(req.url, undefined);
          return response;
        })
        .catch(err => {
          this._fetchPromisesCache.set(req.url, undefined);
          throw err;
        })
    );
    return this._fetchPromisesCache.get(req.url)!;
  }

  // if errFn is not set, then only Response possible
  send(req: SendRawRequest & {errFn?: undefined}): Promise<Response>;

  send(req: SendRawRequest): Promise<Response | undefined>;

  send(req: SendJSONRequest): Promise<ParsedJSON>;

  send(req: SendRequest): Promise<Response | ParsedJSON | undefined>;

  /**
   * Send an XHR.
   *
   * @return Promise resolves to Response/ParsedJSON only if the request is successful
   *     (i.e. no exception and response.ok is trsue). If response fails then
   *     promise resolves either to void if errFn is set or rejects if errFn
   *     is not set   */
  send(req: SendRequest): Promise<Response | ParsedJSON | undefined> {
    const options: AuthRequestInit = {method: req.method};
    if (req.body) {
      options.headers = new Headers();
      options.headers.set(
        'Content-Type',
        req.contentType || 'application/json'
      );
      options.body =
        typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    }
    if (req.headers) {
      if (!options.headers) {
        options.headers = new Headers();
      }
      for (const header in req.headers) {
        if (!hasOwnProperty(req.headers, header)) {
          continue;
        }
        options.headers.set(header, req.headers[header]);
      }
    }
    const url = req.url.startsWith('http') ? req.url : getBaseUrl() + req.url;
    const fetchReq: FetchRequest = {
      url,
      fetchOptions: options,
      anonymizedUrl: req.reportUrlAsIs ? url : req.anonymizedUrl,
    };
    const xhr = this.fetch(fetchReq)
      .then(response => {
        if (!response.ok) {
          if (req.errFn) {
            req.errFn.call(undefined, response);
            return;
          }
          this.dispatchEvent(
            new CustomEvent('server-error', {
              detail: {request: fetchReq, response},
              composed: true,
              bubbles: true,
            })
          );
        }
        return response;
      })
      .catch(err => {
        this.dispatchEvent(
          new CustomEvent('network-error', {
            detail: {error: err},
            composed: true,
            bubbles: true,
          })
        );
        if (req.errFn) {
          return req.errFn.call(undefined, null, err);
        } else {
          throw err;
        }
      });

    if (req.parseResponse) {
      // TODO(TS): remove as Response and fix error.
      // Javascript code allows returning of a Response object from errFn.
      // This can be a mistake and we should add check here or it can be used
      // somewhere - in this case we should fix it carefully (define
      // different type of callback if parseResponse is true, etc...).
      return xhr.then(res => this.getResponseObject(res as Response));
    }
    // The actual xhr type is Promise<Response|undefined|void> because of the
    // catch callback
    return xhr as Promise<Response | undefined>;
  }

  invalidateFetchPromisesPrefix(prefix: string) {
    this._fetchPromisesCache.invalidatePrefix(prefix);
    this._cache.invalidatePrefix(prefix);
  }
}
