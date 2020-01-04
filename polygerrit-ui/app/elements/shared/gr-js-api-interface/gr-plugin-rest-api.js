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

  let restApi;

  function getRestApi() {
    if (!restApi) {
      restApi = document.createElement('gr-rest-api-interface');
    }
    return restApi;
  }

  function GrPluginRestApi(opt_prefix) {
    this.opt_prefix = opt_prefix || '';
  }

  GrPluginRestApi.prototype.getLoggedIn = function() {
    return getRestApi().getLoggedIn();
  };

  GrPluginRestApi.prototype.getVersion = function() {
    return getRestApi().getVersion();
  };

  GrPluginRestApi.prototype.getConfig = function() {
    return getRestApi().getConfig();
  };

  GrPluginRestApi.prototype.invalidateReposCache = function() {
    getRestApi().invalidateReposCache();
  };

  GrPluginRestApi.prototype.getAccount = function() {
    return getRestApi().getAccount();
  };

  GrPluginRestApi.prototype.getAccountCapabilities = function(capabilities) {
    return getRestApi().getAccountCapabilities(capabilities);
  };

  GrPluginRestApi.prototype.getRepos =
    function(filter, reposPerPage, opt_offset) {
      return getRestApi().getRepos(filter, reposPerPage, opt_offset);
    };

  /**
   * Fetch and return native browser REST API Response.
   *
   * @param {string} method HTTP Method (GET, POST, etc)
   * @param {string} url URL without base path or plugin prefix
   * @param {Object=} payload Respected for POST and PUT only.
   * @param {?function(?Response, string=)=} opt_errFn
   *    passed as null sometimes.
   * @return {!Promise}
   */
  GrPluginRestApi.prototype.fetch = function(method, url, opt_payload,
      opt_errFn, opt_contentType) {
    return getRestApi().send(method, this.opt_prefix + url, opt_payload,
        opt_errFn, opt_contentType);
  };

  /**
   * Fetch and parse REST API response, if request succeeds.
   *
   * @param {string} method HTTP Method (GET, POST, etc)
   * @param {string} url URL without base path or plugin prefix
   * @param {Object=} payload Respected for POST and PUT only.
   * @param {?function(?Response, string=)=} opt_errFn
   *    passed as null sometimes.
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.send = function(method, url, opt_payload,
      opt_errFn, opt_contentType) {
    return this.fetch(method, url, opt_payload, opt_errFn, opt_contentType)
        .then(response => {
          if (response.status < 200 || response.status >= 300) {
            return response.text().then(text => {
              if (text) {
                return Promise.reject(text);
              } else {
                return Promise.reject(response.status);
              }
            });
          } else {
            return getRestApi().getResponseObject(response);
          }
        });
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.get = function(url) {
    return this.send('GET', url);
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.post = function(url, opt_payload, opt_errFn,
      opt_contentType) {
    return this.send('POST', url, opt_payload, opt_errFn, opt_contentType);
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.put = function(url, opt_payload, opt_errFn,
      opt_contentType) {
    return this.send('PUT', url, opt_payload, opt_errFn, opt_contentType);
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on 204, rejects on error.
   */
  GrPluginRestApi.prototype.delete = function(url) {
    return this.fetch('DELETE', url).then(response => {
      if (response.status !== 204) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(text);
          } else {
            return Promise.reject(response.status);
          }
        });
      }
      return response;
    });
  };

  window.GrPluginRestApi = GrPluginRestApi;
})(window);
