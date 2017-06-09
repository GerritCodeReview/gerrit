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
  if (window.GrGapiOauth) { return; }

  const EMAIL_SCOPE = 'email';

  function GrGapiOauth() {
  }

  GrGapiOauth.isRequired = false;
  GrGapiOauth._setupPromise = null;
  GrGapiOauth._sharedAuthToken = null;
  GrGapiOauth._oauthClientId = null;
  GrGapiOauth._oauthEmail = null;

  GrGapiOauth.prototype.getAccessToken = function() {
    if (this._isTokenValid(GrGapiOauth._sharedAuthToken)) {
      return Promise.resolve(GrGapiOauth._sharedAuthToken['access_token']);
    } else {
      return this._configureOAuthLibrary()
          .then(() => this._refreshToken())
          .then(token => {
            debugger
            GrGapiOauth._sharedAuthToken = token;
            return this.getAccessToken();
          }).catch(err => {
            debugger;
            console.error(err);
          });
    }
  };

  GrGapiOauth.prototype._isTokenValid = function(token) {
    if (!token) { return false; }
    if (!token['access_token'] || !token['expires_at']) { return false; }

    const expiration = new Date(parseInt(token['expires_at'], 10) * 1000);
    if (Date.now() >= expiration) { return false; }

    return true;
  };

  GrGapiOauth.prototype._configureOAuthLibrary = function() {
    if (!GrGapiOauth._setupPromise) {
      GrGapiOauth._setupPromise = new Promise(
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
          GrGapiOauth._oauthClientId = config['client_id'];
          GrGapiOauth._oauthEmail = config['email'];

          // Loading auth has a side-effect. The URLs should be set before
          // loading it.
          return new Promise(
            resolve => gapi.load('auth', () => gapi.auth.init(resolve))
          );
        }).then(() => {
          GrGapiOauth._isSetupComplete = true;
        });
    }
    return GrGapiOauth._setupPromise;
  };

  GrGapiOauth.prototype._refreshToken = function() {
    const opts = {
      client_id: GrGapiOauth._oauthClientId,
      immediate: true,
      scope: EMAIL_SCOPE,
      login_hint: GrGapiOauth._oauthEmail,
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

  GrGapiOauth.prototype._getOAuthConfig = function() {
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

  GrGapiOauth.prototype._getResponseObject = function(response) {
    const JSON_PREFIX = ')]}\'';
    return response.text().then(text => {
      return JSON.parse(text.substring(JSON_PREFIX.length));
    });
  },

  window.GrGapiOauth = GrGapiOauth;
})(window);
