// Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  var JSON_PREFIX = ')]}\'';
  var PARENT_PATCH_NUM = 'PARENT';

  Polymer({
    is: 'gr-rest-api-interface',

    properties: {
      _cache: {
        type: Object,
        value: {},  // Intentional to share the object accross instances.
      },
      _sharedFetchPromises: {
        type: Object,
        value: {},  // Intentional to share the object accross instances.
      },
    },

    fetchJSON: function(url, opt_cancelCondition, opt_params, opt_opts) {
      opt_opts = opt_opts || {};

      var fetchOptions = {
        credentials: (opt_opts.noCredentials ? undefined : 'same-origin'),
        headers: opt_opts.headers,
      };

      var urlWithParams = url;
      if (opt_params) {
        var params = [];
        for (var p in opt_params) {
          if (opt_params[p] == null) {
            params.push(encodeURIComponent(p));
            continue;
          }
          params.push(
            encodeURIComponent(p) + '=' +
            encodeURIComponent(opt_params[p]));
        }
        // Sorting the params leaves the order deterministic which is easier
        // to test.
        urlWithParams += '?' + params.sort().join('&');
      }

      return fetch(urlWithParams, fetchOptions).then(function(response) {
        if (opt_cancelCondition && opt_cancelCondition()) {
          response.body.cancel();
          return;
        }

        return response.text().then(function(text) {
          var result;
          try {
            result = JSON.parse(text.substring(JSON_PREFIX.length));
          } catch (_) {
            result = null;
          }
          return result;
        });
      }).catch(function(err) {
        if (opt_opts.noCredentials) {
          throw err;
        } else {
          // This could be because of a 302 auth redirect. Retry the request.
          return this.fetchJSON(url, opt_cancelCondition, opt_params,
              Object.assign(opt_opts, {noCredentials: true}));
        }
      }.bind(this));
    },

    getConfig: function() {
      return this._fetchSharedCacheURL('/config/server/info');
    },

    getVersion: function() {
      return this._fetchSharedCacheURL('/config/server/version');
    },

    getDiffPreferences: function() {
      return this._fetchSharedCacheURL('/accounts/self/preferences.diff');
    },

    getAccount: function() {
      return this._fetchSharedCacheURL('/accounts/self/detail');
    },

    getLoggedIn: function() {
      return this.getAccount().then(function(account) {
        return account != null;
      });
    },

    getPreferences: function() {
      return this._fetchSharedCacheURL('/accounts/self/preferences');
    },

    _fetchSharedCacheURL: function(url) {
      if (this._sharedFetchPromises[url]) {
        return this._sharedFetchPromises[url];
      }
      // TODO(andybons): Periodic cache invalidation.
      if (this._cache[url] !== undefined) {
        return Promise.resolve(this._cache[url]);
      }
      this._sharedFetchPromises[url] = this.fetchJSON(url).then(
        function(response) {
          if (response !== undefined) {
            this._cache[url] = response;
          }
          this._sharedFetchPromises[url] = undefined;
          return response;
        }.bind(this)).catch(function(err) {
          this._sharedFetchPromises[url] = undefined;
          throw err;
        }.bind(this));
      return this._sharedFetchPromises[url];
    },

    getChangeFiles: function(changeNum, patchNum) {
      return this.fetchJSON(
          this._changeBaseURL(changeNum, patchNum) + '/files');
    },

    getReviewedFiles: function(changeNum, patchNum) {
      return this.fetchJSON(
          this._changeBaseURL(changeNum, patchNum) + '/files?reviewed');
    },

    saveFileReviewed: function(changeNum, patchNum, path, reviewed, opt_errFn,
        opt_ctx) {
      var method = reviewed ? 'PUT' : 'DELETE';
      var url = this._changeBaseURL(changeNum, patchNum) + '/files/' +
          encodeURIComponent(path) + '/reviewed';

      return this._save(method, url, null, opt_errFn, opt_ctx);
    },

    _save: function(method, url, opt_body, opt_errFn, opt_ctx) {
      var headers = new Headers({
        'X-Gerrit-Auth': this._getCookie('XSRF_TOKEN'),
      });
      var options = {
        method: method,
        headers: headers,
        credentials: 'same-origin',
      };
      if (opt_body) {
        headers.append('Content-Type', 'application/json');
        options.body = opt_body;
      }
      return fetch(url, options).catch(function(err) {
        if (opt_errFn) {
          opt_errFn.call(opt_ctx || this);
        } else {
          throw err;
        }
      });
    },

    getDiff: function(changeNum, basePatchNum, patchNum, path,
        opt_cancelCondition) {
      var url = this._getDiffFetchURL(changeNum, patchNum, path);
      var params =  {
        context: 'ALL',
        intraline: null,
        whitespace: 'IGNORE_NONE',
      };
      if (basePatchNum != PARENT_PATCH_NUM) {
        params.base = basePatchNum;
      }

      return this.fetchJSON(url, opt_cancelCondition, params);
    },

    _getDiffFetchURL: function(changeNum, patchNum, path) {
      return this._changeBaseURL(changeNum, patchNum) + '/files/' +
          encodeURIComponent(path) + '/diff';
    },

    getDiffComments: function(changeNum, opt_basePatchNum, opt_patchNum,
        opt_path) {
      return this._getDiffComments(changeNum, '/comments', opt_basePatchNum,
          opt_patchNum, opt_path);
    },

    getDiffDrafts: function(changeNum, opt_basePatchNum, opt_patchNum,
        opt_path) {
      return this._getDiffComments(changeNum, '/drafts', opt_basePatchNum,
          opt_patchNum, opt_path);
    },

    _getDiffComments: function(changeNum, endpoint, opt_basePatchNum,
        opt_patchNum, opt_path) {
      if (!opt_basePatchNum && !opt_patchNum && !opt_path) {
        return this.fetchJSON(
            this._getDiffCommentsFetchURL(changeNum, '/drafts'));
      }

      function onlyParent(c) { return c.side == PARENT_PATCH_NUM; }
      function withoutParent(c) { return c.side != PARENT_PATCH_NUM; }

      var promises = [];
      var comments;
      var baseComments;
      var url =
          this._getDiffCommentsFetchURL(changeNum, endpoint, opt_patchNum);
      promises.push(this.fetchJSON(url).then(function(response) {
        comments = response[opt_path] || [];
        if (opt_basePatchNum == PARENT_PATCH_NUM) {
          baseComments = comments.filter(onlyParent);
        }
        comments = comments.filter(withoutParent);
      }.bind(this)));

      if (opt_basePatchNum != PARENT_PATCH_NUM) {
        var baseURL = this._getDiffCommentsFetchURL(changeNum, endpoint,
            opt_basePatchNum);
        promises.push(this.fetchJSON(baseURL).then(function(response) {
          baseComments = (response[opt_path] || []).filter(withoutParent);
        }));
      }

      return Promise.all(promises).then(function() {
        return Promise.resolve({
          baseComments: baseComments,
          comments: comments,
        });
      });
    },

    _getDiffCommentsFetchURL: function(changeNum, endpoint, opt_patchNum) {
      return this._changeBaseURL(changeNum, opt_patchNum) + endpoint;
    },

    _changeBaseURL: function(changeNum, opt_patchNum) {
      var v = '/changes/' + changeNum;
      if (opt_patchNum) {
        v += '/revisions/' + opt_patchNum;
      }
      return v;
    },

    _getCookie: function(name) {
      var key = name + '=';
      var cookies = document.cookie.split(';');
      for (var i = 0; i < cookies.length; i++) {
        var c = cookies[i];
        while (c.charAt(0) == ' ') {
          c = c.substring(1);
        }
        if (c.indexOf(key) == 0) {
          return c.substring(key.length, c.length);
        }
      }
      return '';
    },

  });
})();
