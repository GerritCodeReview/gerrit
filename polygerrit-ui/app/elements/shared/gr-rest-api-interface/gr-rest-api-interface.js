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

    getAccount: function() {
      return this._fetchSharedCacheURL('/accounts/self/detail');
    },

    getPrefs: function() {
      return this._fetchSharedCacheURL('/accounts/self/preferences');
    },

    _fetchSharedCacheURL: function(url) {
      if (this._sharedFetchPromises[url]) {
        return this._sharedFetchPromises[url];
      }
      // TODO(andybons): Periodic cache invalidation.
      if (this._cache[url] !== undefined) {
        return this._cache[url];
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

    getDiff: function(changeNum, basePatchNum, patchNum, path,
        opt_cancelCondition) {
      var url = this._getDiffFetchURL(changeNum, patchNum, path);
      var params =  {
        context: 'ALL',
        intraline: null
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

    getDiffComments: function(changeNum, basePatchNum, patchNum, path) {
      return this._getDiffComments(changeNum, basePatchNum, patchNum, path,
          '/comments');
    },

    getDiffDrafts: function(changeNum, basePatchNum, patchNum, path) {
      return this._getDiffComments(changeNum, basePatchNum, patchNum, path,
          '/drafts');
    },

    _getDiffComments: function(changeNum, basePatchNum, patchNum, path,
        endpoint) {
      function onlyParent(c) { return c.side == PARENT_PATCH_NUM; }
      function withoutParent(c) { return c.side != PARENT_PATCH_NUM; }

      var promises = [];
      var comments;
      var baseComments;
      var url = this._getDiffCommentsFetchURL(changeNum, patchNum, endpoint);
      promises.push(this.fetchJSON(url).then(function(response) {
        comments = response[path] || [];
        if (basePatchNum == PARENT_PATCH_NUM) {
          baseComments = comments.filter(onlyParent);
        }
        comments = comments.filter(withoutParent);
      }.bind(this)));

      if (basePatchNum != PARENT_PATCH_NUM) {
        var baseURL = this._getDiffCommentsFetchURL(changeNum, basePatchNum,
            endpoint);
        promises.push(this.fetchJSON(baseURL).then(function(response) {
          baseComments = (response[path] || []).filter(withoutParent);
        }));
      }

      return Promise.all(promises).then(function() {
        return Promise.resolve({
          baseComments: baseComments,
          comments: comments,
        });
      });
    },

    _getDiffCommentsFetchURL: function(changeNum, patchNum, endpoint) {
      return this._changeBaseURL(changeNum, patchNum) + endpoint;
    },

    _changeBaseURL: function(changeNum, patchNum) {
      var v = '/changes/' + changeNum;
      if (patchNum) {
        v += '/revisions/' + patchNum;
      }
      return v;
    },

  });
})();
