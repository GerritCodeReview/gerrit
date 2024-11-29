/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from '../../../../utils/url-util';
import {AuthService} from '../../../../services/gr-auth/gr-auth';
import {ParsedJSON, RequestPayload} from '../../../../types/common';
import {HttpMethod} from '../../../../constants/constants';
import {RpcLogEventDetail} from '../../../../types/events';
import {
  fire,
  fireNetworkError,
  fireServerError,
} from '../../../../utils/event-util';
import {
  AuthRequestInit,
  FetchRequest as FetchRequestBase,
} from '../../../../types/types';
import {ErrorCallback} from '../../../../api/rest';
import {Scheduler, Task} from '../../../../services/scheduler/scheduler';
import {RetryError} from '../../../../services/scheduler/retry-scheduler';

export const JSON_PREFIX = ")]}'";
export const REQUEST_ORIGIN_HEADER = 'X-Gerrit-Request-Origin';

export interface ResponsePayload {
  parsed: ParsedJSON;
  raw: string;
}

export async function readJSONResponsePayload(
  response: Response
): Promise<ResponsePayload> {
  const text = await response.text();
  let result: ParsedJSON;
  try {
    result = parsePrefixedJSON(text);
  } catch (_) {
    throw new Error(`Response payload is not prefixed json. Payload: ${text}`);
  }
  return {parsed: result!, raw: text};
}

export function parsePrefixedJSON(jsonWithPrefix: string): ParsedJSON {
  return JSON.parse(jsonWithPrefix.substring(JSON_PREFIX.length)) as ParsedJSON;
}

// Adds base url if not added in cache key
// or doesn't add it if it already is there.
function addBaseUrl(key: string) {
  if (!getBaseUrl()) return key;
  return key.startsWith(getBaseUrl()) ? key : getBaseUrl() + key;
}

/**
 * Wrapper around Map for caching server responses. Site-based so that
 * changes to CANONICAL_PATH will result in a different cache going into
 * effect.
 *
 * All methods operate on the cache for the current CANONICAL_PATH.
 * Accessing cache entries for older CANONICAL_PATH not supported.
 */
// TODO(kamilm): Seems redundant to have both this and FetchPromisesCache
//   consider joining their functionality into a single cache.
export class SiteBasedCache {
  private readonly data = new Map<string, Map<string, ParsedJSON>>();

  constructor() {
    if (window.INITIAL_DATA) {
      // Put all data shipped with index.html into the cache. This makes it
      // so that we spare more round trips to the server when the app loads
      // initially.
      // TODO(kamilm): This implies very strict format of what is stored in
      //   INITIAL_DATA which is not clear from the name, consider renaming.
      Object.entries(window.INITIAL_DATA).forEach(e =>
        this._cache().set(addBaseUrl(e[0]), e[1] as unknown as ParsedJSON)
      );
    }
  }

  // Returns the cache for the current canonical path.
  _cache(): Map<string, ParsedJSON> {
    if (!this.data.has(getBaseUrl())) {
      this.data.set(getBaseUrl(), new Map<string, ParsedJSON>());
    }
    return this.data.get(getBaseUrl())!;
  }

  has(key: string) {
    return this._cache().has(addBaseUrl(key));
  }

  get(key: string): ParsedJSON | undefined {
    return this._cache().get(addBaseUrl(key));
  }

  set(key: string, value: ParsedJSON) {
    this._cache().set(addBaseUrl(key), value);
  }

  delete(key: string) {
    this._cache().delete(addBaseUrl(key));
  }

  invalidatePrefix(prefix: string) {
    const newMap = new Map<string, ParsedJSON>();
    for (const [key, value] of this._cache().entries()) {
      if (!key.startsWith(addBaseUrl(prefix))) {
        newMap.set(key, value);
      }
    }
    this.data.set(getBaseUrl(), newMap);
  }
}

type FetchPromisesCacheData = {
  [url: string]: Promise<ParsedJSON | undefined> | undefined;
};

/**
 * Stores promises for inflight requests, by url.
 */
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
    return !!this.data[addBaseUrl(key)];
  }

  get(key: string) {
    return this.data[addBaseUrl(key)];
  }

  /**
   * @param value a Promise to store in the cache. Pass undefined value to
   *     mark key as deleted.
   */
  set(key: string, value: Promise<ParsedJSON | undefined> | undefined) {
    this.data[addBaseUrl(key)] = value;
  }

  invalidatePrefix(prefix: string) {
    const newData: FetchPromisesCacheData = {};
    Object.entries(this.data).forEach(([key, value]) => {
      if (!key.startsWith(addBaseUrl(prefix))) {
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

export interface FetchRequest extends FetchRequestBase {
  /**
   * If neither this or anonymizedUrl specified no 'gr-rpc-log' event is fired.
   */
  reportUrlAsIs?: boolean;
  /** Extra url params to be encoded and added to the url. */
  params?: FetchParams;
  /**
   * Callback that is called, if an error was caught during fetch or if the
   * response was returned with a non-2xx status.
   */
  errFn?: ErrorCallback;
  /**
   * If true, response with non-200 status will cause an error to be reported
   * via server-error event or errFn, if provided.
   */
  // TODO(kamilm): Consider changing the default to true. It makes more sense to
  //   only skip the check if the caller wants to prosess status themselves.
  reportServerError?: boolean;
}

export interface FetchOptionsInit {
  method?: HttpMethod;
  body?: RequestPayload;
  contentType?: string;
  headers?: Record<string, string>;
  // Specifies if the call originated from core Gerrit UI, plugin or somewhere
  // else.
  requestOrigin?: string;
}

export function getFetchOptions(init: FetchOptionsInit): AuthRequestInit {
  const options: AuthRequestInit = {
    method: init.method,
    headers: new Headers(),
  };
  if (init.body) {
    options.headers!.set(
      'Content-Type',
      init.contentType || 'application/json'
    );
    options.body =
      typeof init.body === 'string' ? init.body : JSON.stringify(init.body);
  }
  if (init.requestOrigin) {
    options.headers!.set(REQUEST_ORIGIN_HEADER, init.requestOrigin);
  }
  // Copy headers after processing body, so that explicit headers can override
  // if necessary.
  if (init.headers) {
    for (const [name, value] of Object.entries(init.headers)) {
      options.headers!.set(name, value);
    }
  }
  return options;
}

export class GrRestApiHelper {
  constructor(
    private readonly _cache: SiteBasedCache,
    private readonly _auth: AuthService,
    private readonly _fetchPromisesCache: FetchPromisesCache,
    private readonly readScheduler: Scheduler<Response>,
    private readonly writeScheduler: Scheduler<Response>
  ) {}

  private schedule(method: string, task: Task<Response>): Promise<Response> {
    if (method === 'PUT' || method === 'POST' || method === 'DELETE') {
      return this.writeScheduler.schedule(task);
    } else {
      return this.readScheduler.schedule(task);
    }
  }

  /**
   * Wraps calls to the underlying authenticated fetch function (_auth.fetch)
   * with timing and logging.
   */
  private fetchImpl(req: FetchRequest): Promise<Response> {
    const method = req.fetchOptions?.method ?? HttpMethod.GET;
    const startTime = Date.now();
    const task = async () => {
      const res = await this._auth.fetch(req.url, req.fetchOptions);
      // Check for "too many requests" error and throw RetryError to cause a
      // retry in this case, if the scheduler attempts retries.
      if (!res.ok && res.status === 429) throw new RetryError<Response>(res);
      return res;
    };

    const resPromise = this.schedule(method, task).catch((err: unknown) => {
      if (err instanceof RetryError) {
        return err.payload;
      } else {
        throw err;
      }
    });

    // Log the call after it completes.
    resPromise.then(res => this.logCall(req, startTime, res.status));
    // Return the response directly (without the log).
    return resPromise;
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
  logCall(req: FetchRequest, startTime: number, status: number) {
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
      fire(document, 'gr-rpc-log', detail);
    }
  }

  /**
   * Fetch from url provided.
   *
   * Performs auth. Validates auth expiry errors.
   * Will report any errors (by firing a corresponding event or calling errFn)
   * that happen during the request, but doesn't inspect the status of the
   * received response unless req.reportServerError = true.
   *
   * @return Promise resolves to a native Response.
   *     If an error occurs when performing a request, promise rejects.
   */
  async fetch(req: FetchRequest): Promise<Response> {
    if (!req.fetchOptions) {
      req.fetchOptions = {};
    }
    if (!req.fetchOptions.headers) {
      req.fetchOptions.headers = new Headers();
    }
    if (!req.fetchOptions.headers.get(REQUEST_ORIGIN_HEADER)) {
      req.fetchOptions.headers.set(REQUEST_ORIGIN_HEADER, 'core-ui');
    }
    const urlWithParams = this.urlWithParams(req.url, req.params);
    const fetchReq: FetchRequest = {
      url: urlWithParams,
      fetchOptions: req.fetchOptions,
      anonymizedUrl: req.reportUrlAsIs ? urlWithParams : req.anonymizedUrl,
    };

    let resp: Response;
    try {
      resp = await this.fetchImpl(fetchReq);
    } catch (err) {
      // Wrap the error to get more information about the stack.
      const newErr = new Error(
        `Network error when trying to fetch. Cause: ${(err as Error).message}`
      );
      newErr.stack = (newErr.stack ?? '') + '\n' + ((err as Error).stack ?? '');
      if (req.errFn) {
        await req.errFn.call(undefined, null, newErr);
      } else {
        fireNetworkError(newErr);
      }
      throw newErr;
    }
    if (req.reportServerError && !resp.ok) {
      if (req.errFn) {
        await req.errFn.call(undefined, resp);
      } else {
        fireServerError(resp, req);
      }
    }
    return resp;
  }

  /**
   * Fetch JSON from url provided.
   *
   * Returned promise rejects if an error occurs when performing a request or
   * if the response payload doesn't contain a valid prefixed JSON.
   *
   * If response status is not 2xx, promise resolves to undefined and error is
   * reported, through errFn callback or via 'sever-error' event. The error can
   * be suppressed with req.reportServerError = false.
   *
   * If JSON parsing fails the promise rejects.
   *
   * @param noAcceptHeader - don't add default accept json header
   * @return Promise that resolves to a parsed response.
   */
  async fetchJSON(
    req: FetchRequest,
    noAcceptHeader?: boolean
  ): Promise<ParsedJSON | undefined> {
    if (!noAcceptHeader) {
      req = this.addAcceptJsonHeader(req);
    }
    req.reportServerError ??= true;
    const response = await this.fetch(req);
    if (!response.ok) {
      return undefined;
    }
    // TODO(kamilm): The parsing error should likely be reported via errFn or
    // gr-error-manager as well.
    return (await readJSONResponsePayload(response)).parsed;
  }

  /**
   * Add extra url params to the url.
   *
   * Params with values (not undefined) added as <key>=<value>. If value is an
   * array a separate <key>=<value> param is added for every value.
   */
  urlWithParams(url: string, fetchParams?: FetchParams): string {
    if (!fetchParams) {
      return getBaseUrl() + url;
    }

    const params: Array<string | number | boolean> = [];
    for (const [paramKey, paramValue] of Object.entries(fetchParams)) {
      if (paramValue === null || paramValue === undefined) {
        params.push(this.encodeRFC5987(paramKey));
        continue;
      }

      if (Array.isArray(paramValue)) {
        for (const value of paramValue) {
          params.push(
            `${this.encodeRFC5987(paramKey)}=${this.encodeRFC5987(value)}`
          );
        }
      } else {
        params.push(
          `${this.encodeRFC5987(paramKey)}=${this.encodeRFC5987(paramValue)}`
        );
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

  addAcceptJsonHeader(req: FetchRequest) {
    if (!req.fetchOptions) req.fetchOptions = {};
    if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();
    if (!req.fetchOptions.headers.has('Accept')) {
      req.fetchOptions.headers.append('Accept', 'application/json');
    }
    return req;
  }

  /**
   * Fetch JSON using cached value if available.
   *
   * If there is an in-flight request with the same url returns the promise for
   * the in-flight request. If previous call for the same url resulted in the
   * successful response it is returned. Otherwise a new request is sent.
   *
   * Only req.url with req.params is considered for the caching key;
   * headers or request body are not included in cache key.
   */
  fetchCacheJSON(req: FetchRequest): Promise<ParsedJSON | undefined> {
    const urlWithParams = this.urlWithParams(req.url, req.params);
    if (this._fetchPromisesCache.has(urlWithParams)) {
      return this._fetchPromisesCache.get(urlWithParams)!;
    }
    if (this._cache.has(urlWithParams)) {
      return Promise.resolve(this._cache.get(urlWithParams)!);
    }
    this._fetchPromisesCache.set(
      urlWithParams,
      this.fetchJSON(req)
        .then(response => {
          if (response !== undefined) {
            this._cache.set(urlWithParams, response);
          }
          this._fetchPromisesCache.set(urlWithParams, undefined);
          return response;
        })
        .catch(err => {
          this._fetchPromisesCache.set(urlWithParams, undefined);
          throw err;
        })
    );
    return this._fetchPromisesCache.get(urlWithParams)!;
  }

  invalidateFetchPromisesPrefix(prefix: string) {
    this._fetchPromisesCache.invalidatePrefix(prefix);
    this._cache.invalidatePrefix(prefix);
  }
}
