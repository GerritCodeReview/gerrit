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
  if (window.GrGapiAuth) { return; }

  window.USE_GAPI_AUTH = true;

  const EMAIL_SCOPE = 'email';

  function GrGapiAuth() {}

  GrGapiAuth._loadGapiPromise = null;
  GrGapiAuth._setupPromise = null;
  GrGapiAuth._refreshTokenPromise = null;
  GrGapiAuth._sharedAuthToken = null;
  GrGapiAuth._oauthClientId = null;
  GrGapiAuth._oauthEmail = null;

  GrGapiAuth.prototype.fetch = function(url, options) {
    options = Object.assign({}, options);
    return this._getAccessToken().then(token => {
      if (token) {
        options.headers = options.headers || new Headers();
        options.headers.append('Authorization', `Bearer ${token}`);
        if (!url.startsWith('/a/')) {
          url = '/a' + url;
        }
      }
      return fetch(url, options);
    });
  };

  GrGapiAuth.prototype._getAccessToken = function() {
    if (this._isTokenValid(GrGapiAuth._sharedAuthToken)) {
      return Promise.resolve(GrGapiAuth._sharedAuthToken['access_token']);
    }
    if (!GrGapiAuth._refreshTokenPromise) {
      GrGapiAuth._refreshTokenPromise = this._loadGapi()
        .then(() => this._configureOAuthLibrary())
        .then(() => this._refreshToken())
        .then(token => {
          GrGapiAuth._sharedAuthToken = token;
          GrGapiAuth._refreshTokenPromise = null;
          return this._getAccessToken();
        }).catch(err => {
          console.error(err);
        });
    }
    return GrGapiAuth._refreshTokenPromise;
  };

  GrGapiAuth.prototype._isTokenValid = function(token) {
    if (!token) { return false; }
    if (!token['access_token'] || !token['expires_at']) { return false; }

    const expiration = new Date(parseInt(token['expires_at'], 10) * 1000);
    if (Date.now() >= expiration) { return false; }

    return true;
  };

  GrGapiAuth.prototype._loadGapi = function() {
    if (!GrGapiAuth._loadGapiPromise) {
      GrGapiAuth._loadGapiPromise = new Promise((resolve, reject) => {
        const scriptEl = document.createElement('script');
        scriptEl.defer = true;
        scriptEl.async = true;
        scriptEl.src = 'https://apis.google.com/js/platform.js';
        scriptEl.onerror = reject;
        scriptEl.onload = resolve;
        document.body.appendChild(scriptEl);
      });
    }
    return GrGapiAuth._loadGapiPromise;
  };

  GrGapiAuth.prototype._configureOAuthLibrary = function() {
    if (!GrGapiAuth._setupPromise) {
      GrGapiAuth._setupPromise = new Promise(
        resolve => gapi.load('config_min', resolve)
      )
        .then(() => this._getOAuthConfig())
        .then(config => {
          if (config.hasOwnProperty('auth_url') && config['auth_url']) {
            gapi.config.update('oauth-flow/authUrl', config['auth_url']);
          }
          if (config.hasOwnProperty('proxy_url') && config['proxy_url']) {
            gapi.config.update('oauth-flow/proxyUrl', config['proxy_url']);
          }
          GrGapiAuth._oauthClientId = config['client_id'];
          GrGapiAuth._oauthEmail = config['email'];

          // Loading auth has a side-effect. The URLs should be set before
          // loading it.
          return new Promise(
            resolve => gapi.load('auth', () => gapi.auth.init(resolve))
          );
        });
    }
    return GrGapiAuth._setupPromise;
  };

  GrGapiAuth.prototype._refreshToken = function() {
    const opts = {
      client_id: GrGapiAuth._oauthClientId,
      immediate: true,
      scope: EMAIL_SCOPE,
      login_hint: GrGapiAuth._oauthEmail,
    };
    return new Promise((resolve, reject) => {
      gapi.auth.authorize(opts, token => {
        if (!token) {
          reject('No token returned');
        } else if (token['error']) {
          reject(token['error']);
        } else {
          resolve(token);
        }
      });
    });
  };

  GrGapiAuth.prototype._getOAuthConfig = function() {
    const authConfigURL = '/accounts/self/oauthconfig';
    const opts = {
      headers: new Headers({Accept: 'application/json'}),
      credentials: 'same-origin',
    };
    return fetch(authConfigURL, opts).then(response => {
      if (!response.ok) {
        console.error(response.statusText);
        if (response.body && response.body.then) {
          return response.body.then(text => {
            return Promise.reject(text);
          });
        }
        if (response.statusText) {
          return Promise.reject(response.statusText);
        } else {
          return Promise.reject('_getOAuthConfig' + response.status);
        }
      }
      return this._getResponseObject(response);
    });
  };

  GrGapiAuth.prototype._getResponseObject = function(response) {
    const JSON_PREFIX = ')]}\'';
    return response.text().then(text => {
      return JSON.parse(text.substring(JSON_PREFIX.length));
    });
  },

  window.GrGapiAuth = GrGapiAuth;
})(window);
