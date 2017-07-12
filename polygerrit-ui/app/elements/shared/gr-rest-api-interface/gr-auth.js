// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.Gerrit.Auth) { return; }

  Gerrit.Auth = {
    TYPE: {
      XSRF_TOKEN: 'xsrf_token',
      ACCESS_TOKEN: 'access_token',
    },

    _type: null,
    _cachedToken: null,
    _defaultOptions: {},

    _getToken() {
      return Promise.resolve(this._cachedToken);
    },

    _refreshToken() {
      return Promise.resolve(this._cachedToken);
    },

    setup(getToken, refreshToken, defaultOptions) {
      if (getToken && refreshToken) {
        this._type = Gerrit.Auth.TYPE.ACCESS_TOKEN;
        this._getToken = getToken;
        this._refreshToken = refreshToken;
      }
      if (defaultOptions) {
        for (const p of ['credentials']) {
          this._defaultOptions[p] = defaultOptions[p];
        }
      }
    },

    fetch(url, opt_options) {
      const options = Object.assign({}, this._defaultOptions, opt_options);
      if (this._type === Gerrit.Auth.TYPE.ACCESS_TOKEN) {
        return this._getAccessToken().then(
            accessToken => this._fetchWithAccessToken(url, options, accessToken)
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
          options.headers = options.headers || new Headers();
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
      if (this._isTokenValid(this._cachedToken)) {
        return Promise.resolve(this._cachedToken.access_token);
      } else if (this._cachedToken) {
        this._cachedToken = null;
      }
      return this._getToken().then(token => {
        if (!this._isTokenValid(token)) {
          return this._refreshToken();
        }
        return token;
      }).then(token => {
        if (!this._isTokenValid(token)) {
          return null;
        }
        this._cachedToken = token;
        return this._cachedToken.access_token;
      });
    },

    _fetchWithAccessToken(url, options, accessToken) {
      const method = options.method || 'GET';
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
      let contentType = options.headers && options.headers.get('Content-Type');
      if (contentType) {
        options.headers.set('Content-Type', 'text/plain');
      }
      if (method !== 'GET') {
        options.method = 'POST';
        params.push(`$m=${method}`);
        if (!contentType) {
          contentType = 'application/json';
        }
      }
      if (contentType) {
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
