/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from '../../../../utils/url-util';
import {CancelConditionCallback} from '../../../../services/gr-rest-api/gr-rest-api';
import {
  AuthRequestInit,
  AuthService,
} from '../../../../services/gr-auth/gr-auth';
import {
  AccountDetailInfo,
  EmailInfo,
  ParsedJSON,
  RequestPayload,
} from '../../../../types/common';
import {HttpMethod} from '../../../../constants/constants';
import {RpcLogEventDetail} from '../../../../types/events';
import {fireNetworkError, fireServerError} from '../../../../utils/event-util';
import {FetchRequest} from '../../../../types/types';
import {ErrorCallback} from '../../../../api/rest';
import {Scheduler, Task} from '../../../../services/scheduler/scheduler';
import {RetryError} from '../../../../services/scheduler/retry-scheduler';

export const JSON_PREFIX = ")]}'";

export interface ResponsePayload {
  // TODO(TS): readResponsePayload can assign null to the parsed property if
  // it can't parse input data. However polygerrit assumes in many places
  // that the parsed property can't be null. We should update
  // readResponsePayload method and reject a promise instead of assigning
  // null to the parsed property
  parsed: ParsedJSON; // Can be null!!! See comment above
  raw: string;
}

export function readResponsePayload(
  response: Response
): Promise<ResponsePayload> {
  return response.text().then(text => {
    let result;
    try {
      result = parsePrefixedJSON(text);
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

export function parsePrefixedJSON(jsonWithPrefix: string): ParsedJSON {
  return JSON.parse(jsonWithPrefix.substring(JSON_PREFIX.length)) as ParsedJSON;
}

/**
 * Wrapper around Map for caching server responses. Site-based so that
 * changes to CANONICAL_PATH will result in a different cache going into
 * effect.
 */
export class SiteBasedCache {
  // TODO(TS): Type looks unusual. Fix it.
  // Container of per-canonical-path caches.
  private readonly data = new Map<
    string | undefined,
    unknown | Map<string, ParsedJSON | null>
  >();

  constructor() {
    if (window.INITIAL_DATA) {
      // Put all data shipped with index.html into the cache. This makes it
      // so that we spare more round trips to the server when the app loads
      // initially.
      Object.entries(window.INITIAL_DATA).forEach(e =>
        this._cache().set(e[0], e[1] as unknown as ParsedJSON)
      );
    }
  }

  // Returns the cache for the current canonical path.
  _cache(): Map<string, unknown> {
    if (!this.data.has(window.CANONICAL_PATH)) {
      this.data.set(
        window.CANONICAL_PATH,
        new Map<string, ParsedJSON | null>()
      );
    }
    return this.data.get(window.CANONICAL_PATH) as Map<
      string,
      ParsedJSON | null
    >;
  }

  has(key: string) {
    return this._cache().has(key);
  }

  get(key: '/accounts/self/emails'): EmailInfo[] | null;

  get(key: '/accounts/self/detail'): AccountDetailInfo | null;

  get(key: string): ParsedJSON | null;

  get(key: string): unknown {
    return this._cache().get(key);
  }

  set(key: '/accounts/self/emails', value: EmailInfo[]): void;

  set(key: '/accounts/self/detail', value: AccountDetailInfo): void;

  set(key: string, value: ParsedJSON | null): void;

  set(key: string, value: unknown) {
    this._cache().set(key, value);
  }

  delete(key: string) {
    this._cache().delete(key);
  }

  invalidatePrefix(prefix: string) {
    const newMap = new Map<string, unknown>();
    for (const [key, value] of this._cache().entries()) {
      if (!key.startsWith(prefix)) {
        newMap.set(key, value);
      }
    }
    this.data.set(window.CANONICAL_PATH, newMap);
  }
}

type FetchPromisesCacheData = {
  [url: string]: Promise<ParsedJSON | undefined> | undefined;
};

export class FetchPromisesCache {
  private data: FetchPromisesCacheData;

  constructor() {
    this.data = {};
  }

  public testOnlyGetData() {
    return this.data;
  }

  /**
   * @return true only if a value for a key sets and it is not undefined
   */
  has(key: string): boolean {
    return !!this.data[key];
  }

  get(key: string) {
    return this.data[key];
  }

  /**
   * @param value a Promise to store in the cache. Pass undefined value to
   *     mark key as deleted.
   */
  set(key: string, value: Promise<ParsedJSON | undefined> | undefined) {
    this.data[key] = value;
  }

  invalidatePrefix(prefix: string) {
    const newData: FetchPromisesCacheData = {};
    Object.entries(this.data).forEach(([key, value]) => {
      if (!key.startsWith(prefix)) {
        newData[key] = value;
      }
    });
    this.data = newData;
  }
}
export type FetchParams = {
  [name: string]: string[] | string | number | boolean | undefined | null;
};

/**
 * Error callback that throws an error.
 *
 * Pass into REST API methods as errFn to make the returned Promises reject on
 * error.
 *
 * If error is provided, it's thrown.
 * Otherwise if response with error is provided the promise that will throw an
 * error is returned.
 */
export function throwingErrorCallback(
  response?: Response | null,
  err?: Error
): void | Promise<void> {
  if (err) throw err;
  if (!response) return;

  return response.text().then(errorText => {
    let message = `Error ${response.status}`;
    if (response.statusText) {
      message += ` (${response.statusText})`;
    }
    if (errorText) {
      message += `: ${errorText}`;
    }
    throw new Error(message);
  });
}

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
    private readonly readScheduler: Scheduler<Response>,
    private readonly writeScheduler: Scheduler<Response>
  ) {}

  private schedule(method: string, task: Task<Response>) {
    if (method === 'PUT' || method === 'POST' || method === 'DELETE') {
      return this.writeScheduler.schedule(task);
    } else {
      return this.readScheduler.schedule(task);
    }
  }

  /**
   * Wraps calls to the underlying authenticated fetch function (_auth.fetch)
   * with timing and logging.
s   */
  fetch(req: FetchRequest): Promise<Response> {
    const method =
      req.fetchOptions && req.fetchOptions.method
        ? req.fetchOptions.method
        : 'GET';
    const start = Date.now();
    const task = async () => {
      const res = await this._auth.fetch(req.url, req.fetchOptions);
      if (!res.ok && res.status === 429) throw new RetryError<Response>(res);
      return res;
    };

    const xhr = this.schedule(method, task).catch((err: unknown) => {
      if (err instanceof RetryError) {
        return err.payload;
      } else {
        throw err;
      }
    });

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
   * Private, but used in tests.
   *
   * @param startTime the time that the request was started.
   * @param status the HTTP status of the response. The status value
   *     is used here rather than the response object so there is no way this
   *     method can read the body stream.
   */
  _logCall(req: FetchRequest, startTime: number, status: number | null) {
    const method =
      req.fetchOptions && req.fetchOptions.method
        ? req.fetchOptions.method
        : 'GET';
    const endTime = Date.now();
    const elapsed = endTime - startTime;
    const startAt = new Date(startTime);
    const endAt = new Date(endTime);
    console.debug(
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
      const detail: RpcLogEventDetail = {
        status,
        method,
        elapsed,
        anonymizedUrl: req.anonymizedUrl,
      };
      document.dispatchEvent(
        new CustomEvent('gr-rpc-log', {
          detail,
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
          fireNetworkError(err);
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
  async fetchJSON(
    req: FetchJSONRequest,
    noAcceptHeader?: boolean
  ): Promise<ParsedJSON | undefined> {
    if (!noAcceptHeader) {
      req = this.addAcceptJsonHeader(req);
    }
    const response = await this.fetchRawJSON(req);
    if (!response) {
      return;
    }
    if (!response.ok) {
      if (req.errFn) {
        await req.errFn.call(undefined, response);
        return;
      }
      fireServerError(response, req);
      return;
    }
    return this.getResponseObject(response);
  }

  urlWithParams(url: string, fetchParams?: FetchParams): string {
    if (!fetchParams) {
      return getBaseUrl() + url;
    }

    const params: Array<string | number | boolean> = [];
    for (const [p, paramValue] of Object.entries(fetchParams)) {
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
    return readResponsePayload(response).then(payload => payload.parsed);
  }

  addAcceptJsonHeader(req: FetchJSONRequest) {
    if (!req.fetchOptions) req.fetchOptions = {};
    if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();
    if (!req.fetchOptions.headers.has('Accept')) {
      req.fetchOptions.headers.append('Accept', 'application/json');
    }
    return req;
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
   *     (i.e. no exception and response.ok is true). If response fails then
   *     promise resolves either to void if errFn is set or rejects if errFn
   *     is not set   */
  async send(req: SendRequest): Promise<Response | ParsedJSON | undefined> {
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
      for (const [name, value] of Object.entries(req.headers)) {
        options.headers.set(name, value);
      }
    }
    const url = req.url.startsWith('http') ? req.url : getBaseUrl() + req.url;
    const fetchReq: FetchRequest = {
      url,
      fetchOptions: options,
      anonymizedUrl: req.reportUrlAsIs ? url : req.anonymizedUrl,
    };
    let xhr;
    try {
      xhr = await this.fetch(fetchReq);
    } catch (err) {
      fireNetworkError(err as Error);
      if (req.errFn) {
        await req.errFn.call(undefined, null, err as Error);
        xhr = undefined;
      } else {
        throw err;
      }
    }
    if (xhr && !xhr.ok) {
      if (req.errFn) {
        await req.errFn.call(undefined, xhr);
      } else {
        fireServerError(xhr, fetchReq);
      }
    }

    if (req.parseResponse) {
      xhr = xhr && this.getResponseObject(xhr);
    }
    return xhr;
  }

  invalidateFetchPromisesPrefix(prefix: string) {
    this._fetchPromisesCache.invalidatePrefix(prefix);
    this._cache.invalidatePrefix(prefix);
  }
}
