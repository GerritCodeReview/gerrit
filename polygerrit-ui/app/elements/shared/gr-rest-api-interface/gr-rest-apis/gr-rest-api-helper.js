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

  const JSON_PREFIX = ')]}\'';
  const FAILED_TO_FETCH_ERROR = 'Failed to fetch';

  /**
   * Wrapper around Map for caching server responses. Site-based so that
   * changes to CANONICAL_PATH will result in a different cache going into
   * effect.
   */
  class SiteBasedCache {
    constructor() {
      // Container of per-canonical-path caches.
      this._data = new Map();
      if (window.INITIAL_DATA != undefined) {
        // Put all data shipped with index.html into the cache. This makes it
        // so that we spare more round trips to the server when the app loads
        // initially.
        Object
            .entries(window.INITIAL_DATA)
            .forEach(e => this._cache().set(e[0], e[1]));
      }
    }

    // Returns the cache for the current canonical path.
    _cache() {
      if (!this._data.has(window.CANONICAL_PATH)) {
        this._data.set(window.CANONICAL_PATH, new Map());
      }
      return this._data.get(window.CANONICAL_PATH);
    }

    has(key) {
      return this._cache().has(key);
    }

    get(key) {
      return this._cache().get(key);
    }

    set(key, value) {
      this._cache().set(key, value);
    }

    delete(key) {
      this._cache().delete(key);
    }

    invalidatePrefix(prefix) {
      const newMap = new Map();
      for (const [key, value] of this._cache().entries()) {
        if (!key.startsWith(prefix)) {
          newMap.set(key, value);
        }
      }
      this._data.set(window.CANONICAL_PATH, newMap);
    }
  }

  class FetchPromisesCache {
    constructor() {
      this._data = {};
    }

    has(key) {
      return !!this._data[key];
    }

    get(key) {
      return this._data[key];
    }

    set(key, value) {
      this._data[key] = value;
    }

    invalidatePrefix(prefix) {
      const newData = {};
      Object.entries(this._data).forEach(([key, value]) => {
        if (!key.startsWith(prefix)) {
          newData[key] = value;
        }
      });
      this._data = newData;
    }
  }

  class GrRestApiHelper {
    /**
     * @param {SiteBasedCache} cache
     * @param {object} auth
     * @param {FetchPromisesCache} fetchPromisesCache
     * @param {object} credentialCheck
     * @param {object} restApiInterface
     */
    constructor(cache, auth, fetchPromisesCache, credentialCheck,
        restApiInterface) {
      this._cache = cache;// TODO: make it public
      this._auth = auth;
      this._fetchPromisesCache = fetchPromisesCache;
      this._credentialCheck = credentialCheck;
      this._restApiInterface = restApiInterface;
    }

    /**
     * Wraps calls to the underlying authenticated fetch function (_auth.fetch)
     * with timing and logging.
     *
     * @param {Gerrit.FetchRequest} req
     */
    fetch(req) {
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
     * @param {Gerrit.FetchRequest} req
     * @param {number} startTime the time that the request was started.
     * @param {number} status the HTTP status of the response. The status value
     *     is used here rather than the response object so there is no way this
     *     method can read the body stream.
     */
    _logCall(req, startTime, status) {
      const method = (req.fetchOptions && req.fetchOptions.method) ?
        req.fetchOptions.method : 'GET';
      const endTime = Date.now();
      const elapsed = (endTime - startTime);
      const startAt = new Date(startTime);
      const endAt = new Date(endTime);
      console.log([
        'HTTP',
        status,
        method,
        elapsed + 'ms',
        req.anonymizedUrl || req.url,
        `(${startAt.toISOString()}, ${endAt.toISOString()})`,
      ].join(' '));
      if (req.anonymizedUrl) {
        this.fire('rpc-log',
            {status, method, elapsed, anonymizedUrl: req.anonymizedUrl});
      }
    }

    /**
     * Fetch JSON from url provided.
     * Returns a Promise that resolves to a native Response.
     * Doesn't do error checking. Supports cancel condition. Performs auth.
     * Validates auth expiry errors.
     *
     * @param {Gerrit.FetchJSONRequest} req
     */
    fetchRawJSON(req) {
      const urlWithParams = this.urlWithParams(req.url, req.params);
      const fetchReq = {
        url: urlWithParams,
        fetchOptions: req.fetchOptions,
        anonymizedUrl: req.reportUrlAsIs ? urlWithParams : req.anonymizedUrl,
      };
      return this.fetch(fetchReq).then(res => {
        if (req.cancelCondition && req.cancelCondition()) {
          res.body.cancel();
          return;
        }
        return res;
      }).catch(err => {
        const isLoggedIn = !!this._cache.get('/accounts/self/detail');
        if (isLoggedIn && err && err.message === FAILED_TO_FETCH_ERROR) {
          this.checkCredentials();
        } else {
          if (req.errFn) {
            req.errFn.call(undefined, null, err);
          } else {
            this.fire('network-error', {error: err});
          }
        }
        throw err;
      });
    }

    /**
     * Fetch JSON from url provided.
     * Returns a Promise that resolves to a parsed response.
     * Same as {@link fetchRawJSON}, plus error handling.
     *
     * @param {Gerrit.FetchJSONRequest} req
     */
    fetchJSON(req) {
      req = this.addAcceptJsonHeader(req);
      return this.fetchRawJSON(req).then(response => {
        if (!response) {
          return;
        }
        if (!response.ok) {
          if (req.errFn) {
            req.errFn.call(null, response);
            return;
          }
          this.fire('server-error', {request: req, response});
          return;
        }
        return response && this.getResponseObject(response);
      });
    }

    /**
     * @param {string} url
     * @param {?Object|string=} opt_params URL params, key-value hash.
     * @return {string}
     */
    urlWithParams(url, opt_params) {
      if (!opt_params) { return this.getBaseUrl() + url; }

      const params = [];
      for (const p in opt_params) {
        if (!opt_params.hasOwnProperty(p)) { continue; }
        if (opt_params[p] == null) {
          params.push(encodeURIComponent(p));
          continue;
        }
        for (const value of [].concat(opt_params[p])) {
          params.push(`${encodeURIComponent(p)}=${encodeURIComponent(value)}`);
        }
      }
      return this.getBaseUrl() + url + '?' + params.join('&');
    }

    /**
     * @param {!Object} response
     * @return {?}
     */
    getResponseObject(response) {
      return this.readResponsePayload(response)
          .then(payload => payload.parsed);
    }

    /**
     * @param {!Object} response
     * @return {!Object}
     */
    readResponsePayload(response) {
      return response.text().then(text => {
        let result;
        try {
          result = this.parsePrefixedJSON(text);
        } catch (_) {
          result = null;
        }
        return {parsed: result, raw: text};
      });
    }

    /**
     * @param {string} source
     * @return {?}
     */
    parsePrefixedJSON(source) {
      return JSON.parse(source.substring(JSON_PREFIX.length));
    }

    /**
     * @param {Gerrit.FetchJSONRequest} req
     * @return {Gerrit.FetchJSONRequest}
     */
    addAcceptJsonHeader(req) {
      if (!req.fetchOptions) req.fetchOptions = {};
      if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();
      if (!req.fetchOptions.headers.has('Accept')) {
        req.fetchOptions.headers.append('Accept', 'application/json');
      }
      return req;
    }

    getBaseUrl() {
      return this._restApiInterface.getBaseUrl();
    }

    fire(type, detail, options) {
      return this._restApiInterface.fire(type, detail, options);
    }

    /**
     * @param {Gerrit.FetchJSONRequest} req
     */
    fetchCacheURL(req) {
      if (this._fetchPromisesCache.has(req.url)) {
        return this._fetchPromisesCache.get(req.url);
      }
      // TODO(andybons): Periodic cache invalidation.
      if (this._cache.has(req.url)) {
        return Promise.resolve(this._cache.get(req.url));
      }
      this._fetchPromisesCache.set(req.url,
          this.fetchJSON(req).then(response => {
            if (response !== undefined) {
              this._cache.set(req.url, response);
            }
            this._fetchPromisesCache.set(req.url, undefined);
            return response;
          }).catch(err => {
            this._fetchPromisesCache.set(req.url, undefined);
            throw err;
          })
      );
      return this._fetchPromisesCache.get(req.url);
    }

    /**
     * Send an XHR.
     *
     * @param {Gerrit.SendRequest} req
     * @return {Promise}
     */
    send(req) {
      const options = {method: req.method};
      if (req.body) {
        options.headers = new Headers();
        options.headers.set(
            'Content-Type', req.contentType || 'application/json');
        options.body = typeof req.body === 'string' ?
          req.body : JSON.stringify(req.body);
      }
      if (req.headers) {
        if (!options.headers) { options.headers = new Headers(); }
        for (const header in req.headers) {
          if (!req.headers.hasOwnProperty(header)) { continue; }
          options.headers.set(header, req.headers[header]);
        }
      }
      const url = req.url.startsWith('http') ?
        req.url : this.getBaseUrl() + req.url;
      const fetchReq = {
        url,
        fetchOptions: options,
        anonymizedUrl: req.reportUrlAsIs ? url : req.anonymizedUrl,
      };
      const xhr = this.fetch(fetchReq).then(response => {
        if (!response.ok) {
          if (req.errFn) {
            return req.errFn.call(undefined, response);
          }
          this.fire('server-error', {request: fetchReq, response});
        }
        return response;
      }).catch(err => {
        this.fire('network-error', {error: err});
        if (req.errFn) {
          return req.errFn.call(undefined, null, err);
        } else {
          throw err;
        }
      });

      if (req.parseResponse) {
        return xhr.then(res => this.getResponseObject(res));
      }

      return xhr;
    }

    checkCredentials() {
      if (this._credentialCheck.checking) {
        return;
      }
      this._credentialCheck.checking = true;
      let req = {url: '/accounts/self/detail', reportUrlAsIs: true};
      req = this.addAcceptJsonHeader(req);
      // Skip the REST response cache.
      return this.fetchRawJSON(req).then(res => {
        if (!res) { return; }
        if (res.status === 403) {
          this.fire('auth-error');
          this._cache.delete('/accounts/self/detail');
        } else if (res.ok) {
          return this.getResponseObject(res);
        }
      }).then(res => {
        this._credentialCheck.checking = false;
        if (res) {
          this._cache.set('/accounts/self/detail', res);
        }
        return res;
      }).catch(err => {
        this._credentialCheck.checking = false;
        if (err && err.message === FAILED_TO_FETCH_ERROR) {
          this.fire('auth-error');
          this._cache.delete('/accounts/self/detail');
        }
      });
    }

    /**
     * @param {string} prefix
     */
    invalidateFetchPromisesPrefix(prefix) {
      this._fetchPromisesCache.invalidatePrefix(prefix);
      this._cache.invalidatePrefix(prefix);
    }
  }

  window.SiteBasedCache = SiteBasedCache;
  window.FetchPromisesCache = FetchPromisesCache;
  window.GrRestApiHelper = GrRestApiHelper;
})(window);

