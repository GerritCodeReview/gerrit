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
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.Gerrit.Auth) { return; }

  const MAX_GET_TOKEN_RETRIES = 2;

  Gerrit.Auth = {
    TYPE: {
      XSRF_TOKEN: 'xsrf_token',
      ACCESS_TOKEN: 'access_token',
    },

    _type: null,
    _cachedTokenPromise: null,
    _defaultOptions: {},
    _retriesLeft: MAX_GET_TOKEN_RETRIES,

    _getToken() {
      return Promise.resolve(this._cachedTokenPromise);
    },

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
        this._type = Gerrit.Auth.TYPE.ACCESS_TOKEN;
        this._cachedTokenPromise = null;
        this._getToken = getToken;
      }
      this._defaultOptions = {};
      if (defaultOptions) {
        for (const p of ['credentials']) {
          this._defaultOptions[p] = defaultOptions[p];
        }
      }
    },

    /**
     * Perform network fetch with authentication.
     *
     * @param {string} url
     * @param {Object=} opt_options
     * @return {!Promise<!Response>}
     */
    fetch(url, opt_options) {
      const options = Object.assign({
        headers: new Headers(),
      }, this._defaultOptions, opt_options);
      if (this._type === Gerrit.Auth.TYPE.ACCESS_TOKEN) {
        return this._getAccessToken().then(
            accessToken =>
              this._fetchWithAccessToken(url, options, accessToken)
        );
      } else {
        return this._fetchWithXsrfToken(url, options);
      }
    },

    _getCookie(name) {
      const key = name + '=';
      let result = '';
      document.cookie.split(';').some(c => {
        c = c.trim();
        if (c.startsWith(key)) {
          result = c.substring(key.length);
          return true;
        }
      });
      return result;
    },

    _isTokenValid(token) {
      if (!token) { return false; }
      if (!token.access_token || !token.expires_at) { return false; }

      const expiration = new Date(parseInt(token.expires_at, 10) * 1000);
      if (Date.now() >= expiration.getTime()) { return false; }

      return true;
    },

    _fetchWithXsrfToken(url, options) {
      if (options.method && options.method !== 'GET') {
        const token = this._getCookie('XSRF_TOKEN');
        if (token) {
          options.headers.append('X-Gerrit-Auth', token);
        }
      }
      options.credentials = 'same-origin';
      return fetch(url, options);
    },

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
    },

    _fetchWithAccessToken(url, options, accessToken) {
      const params = [];

      if (accessToken) {
        params.push(`access_token=${accessToken}`);
        const baseUrl = Gerrit.BaseUrlBehavior.getBaseUrl();
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
    },
  };

  window.Gerrit.Auth = Gerrit.Auth;
})(window);
