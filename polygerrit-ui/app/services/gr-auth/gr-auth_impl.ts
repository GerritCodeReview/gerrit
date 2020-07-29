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
import {getBaseUrl} from '../utils/url-util.js';

const MAX_AUTH_CHECK_WAIT_TIME_MS = 1000 * 30; // 30s
const MAX_GET_TOKEN_RETRIES = 2;

/**
 * Auth class.
 */
export class Auth {
  constructor(eventEmitter) {
    this._type = null;
    this._cachedTokenPromise = null;
    this._defaultOptions = {};
    this._retriesLeft = MAX_GET_TOKEN_RETRIES;
    this._status = Auth.STATUS.UNDETERMINED;
    this._authCheckPromise = null;
    this._last_auth_check_time = Date.now();
    this.eventEmitter = eventEmitter;
  }

  get baseUrl() {
    return getBaseUrl();
  }

  /**
   * Returns if user is authed or not.
   *
   * @returns {!Promise<boolean>}
   */
  authCheck() {
    if (!this._authCheckPromise ||
      (Date.now() - this._last_auth_check_time > MAX_AUTH_CHECK_WAIT_TIME_MS)
    ) {
      // Refetch after last check expired
      this._authCheckPromise = fetch(`${this.baseUrl}/auth-check`);
      this._last_auth_check_time = Date.now();
    }

    return this._authCheckPromise.then(res => {
      // auth-check will return 204 if authed
      // treat the rest as unauthed
      if (res.status === 204) {
        this._setStatus(Auth.STATUS.AUTHED);
        return true;
      } else {
        this._setStatus(Auth.STATUS.NOT_AUTHED);
        return false;
      }
    }).catch(e => {
      this._setStatus(Auth.STATUS.ERROR);
      // Reset _authCheckPromise to avoid caching the failed promise
      this._authCheckPromise = null;
      return false;
    });
  }

  clearCache() {
    this._authCheckPromise = null;
  }

  /**
   * @param {Auth.STATUS} status
   */
  _setStatus(status) {
    if (this._status === status) return;

    if (this._status === Auth.STATUS.AUTHED) {
      this.eventEmitter.emit('auth-error', {
        message: Auth.CREDS_EXPIRED_MSG, action: 'Refresh credentials',
      });
    }
    this._status = status;
  }

  get status() {
    return this._status;
  }

  get isAuthed() {
    return this._status === Auth.STATUS.AUTHED;
  }

  _getToken() {
    return Promise.resolve(this._cachedTokenPromise);
  }

  /**
   * Enable cross-domain authentication using OAuth access token.
   *
   * @param {
   *   function(): !Promise<{
   *     access_token: string,
   *     expires_at: number
   *   }>
   * } getToken
   * @param {?{credentials:string}} defaultOptions
   */
  setup(getToken, defaultOptions) {
    this._retriesLeft = MAX_GET_TOKEN_RETRIES;
    if (getToken) {
      this._type = Auth.TYPE.ACCESS_TOKEN;
      this._cachedTokenPromise = null;
      this._getToken = getToken;
    }
    this._defaultOptions = {};
    if (defaultOptions) {
      for (const p of ['credentials']) {
        this._defaultOptions[p] = defaultOptions[p];
      }
    }
  }

  /**
   * Perform network fetch with authentication.
   *
   * @param {string} url
   * @param {Object=} opt_options
   * @return {!Promise<!Response>}
   */
  fetch(url, opt_options) {
    const options = {
      headers: new Headers(),
      ...this._defaultOptions,
      ...opt_options,
    };
    if (this._type === Auth.TYPE.ACCESS_TOKEN) {
      return this._getAccessToken().then(
          accessToken =>
            this._fetchWithAccessToken(url, options, accessToken)
      );
    } else {
      return this._fetchWithXsrfToken(url, options);
    }
  }

  _getCookie(name) {
    const key = name + '=';
    let result = '';
    document.cookie.split(';').some(c => {
      c = c.trim();
      if (c.startsWith(key)) {
        result = c.substring(key.length);
        return true;
      }
      return false;
    });
    return result;
  }

  _isTokenValid(token) {
    if (!token) { return false; }
    if (!token.access_token || !token.expires_at) { return false; }

    const expiration = new Date(parseInt(token.expires_at, 10) * 1000);
    if (Date.now() >= expiration.getTime()) { return false; }

    return true;
  }

  _fetchWithXsrfToken(url, options) {
    if (options.method && options.method !== 'GET') {
      const token = this._getCookie('XSRF_TOKEN');
      if (token) {
        options.headers.append('X-Gerrit-Auth', token);
      }
    }
    options.credentials = 'same-origin';
    return fetch(url, options);
  }

  /**
   * @return {!Promise<string>}
   */
  _getAccessToken() {
    if (!this._cachedTokenPromise) {
      this._cachedTokenPromise = this._getToken();
    }
    return this._cachedTokenPromise.then(token => {
      if (this._isTokenValid(token)) {
        this._retriesLeft = MAX_GET_TOKEN_RETRIES;
        return token.access_token;
      }
      if (this._retriesLeft > 0) {
        this._retriesLeft--;
        this._cachedTokenPromise = null;
        return this._getAccessToken();
      }
      // Fall back to anonymous access.
      return null;
    });
  }

  _fetchWithAccessToken(url, options, accessToken) {
    const params = [];

    if (accessToken) {
      params.push(`access_token=${accessToken}`);
      const baseUrl = this.baseUrl;
      const pathname = baseUrl ?
        url.substring(url.indexOf(baseUrl) + baseUrl.length) : url;
      if (!pathname.startsWith('/a/')) {
        url = url.replace(pathname, '/a' + pathname);
      }
    }

    const method = options.method || 'GET';
    let contentType = options.headers.get('Content-Type');

    // For all requests with body, ensure json content type.
    if (!contentType && options.body) {
      contentType = 'application/json';
    }

    if (method !== 'GET') {
      options.method = 'POST';
      params.push(`$m=${method}`);
      // If a request is not GET, and does not have a body, ensure text/plain
      // content type.
      if (!contentType) {
        contentType = 'text/plain';
      }
    }

    if (contentType) {
      options.headers.set('Content-Type', 'text/plain');
      params.push(`$ct=${encodeURIComponent(contentType)}`);
    }

    if (params.length) {
      url = url + (url.indexOf('?') === -1 ? '?' : '&') + params.join('&');
    }
    return fetch(url, options);
  }
}

Auth.TYPE = {
  XSRF_TOKEN: 'xsrf_token',
  ACCESS_TOKEN: 'access_token',
};

/** @enum {number} */
Auth.STATUS = {
  UNDETERMINED: 0,
  AUTHED: 1,
  NOT_AUTHED: 2,
  ERROR: 3,
};

Auth.CREDS_EXPIRED_MSG = 'Credentials expired.';
