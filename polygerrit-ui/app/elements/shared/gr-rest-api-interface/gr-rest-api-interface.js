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

  // Must be kept in sync with the ListChangesOption enum and protobuf.
  var ListChangesOption = {
    LABELS: 0,
    DETAILED_LABELS: 8,

    // Return information on the current patch set of the change.
    CURRENT_REVISION: 1,
    ALL_REVISIONS: 2,

    // If revisions are included, parse the commit object.
    CURRENT_COMMIT: 3,
    ALL_COMMITS: 4,

    // If a patch set is included, include the files of the patch set.
    CURRENT_FILES: 5,
    ALL_FILES: 6,

    // If accounts are included, include detailed account info.
    DETAILED_ACCOUNTS: 7,

    // Include messages associated with the change.
    MESSAGES: 9,

    // Include allowed actions client could perform.
    CURRENT_ACTIONS: 10,

    // Set the reviewed boolean for the caller.
    REVIEWED: 11,

    // Include download commands for the caller.
    DOWNLOAD_COMMANDS: 13,

    // Include patch set weblinks.
    WEB_LINKS: 14,

    // Include consistency check results.
    CHECK: 15,

    // Include allowed change actions client could perform.
    CHANGE_ACTIONS: 16,

    // Include a copy of commit messages including review footers.
    COMMIT_FOOTERS: 17,

    // Include push certificate information along with any patch sets.
    PUSH_CERTIFICATES: 18
  };

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

    fetchJSON: function(url, opt_errFn, opt_cancelCondition, opt_params,
        opt_opts) {
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

        if (!response.ok && opt_errFn) {
          opt_errFn.call(null, response);
          return undefined;
        }
        return this.getResponseObject(response);
      }.bind(this)).catch(function(err) {
        if (opt_opts.noCredentials) {
          throw err;
        } else {
          // This could be because of a 302 auth redirect. Retry the request.
          return this.fetchJSON(url, opt_errFn, opt_cancelCondition, opt_params,
              Object.assign(opt_opts, {noCredentials: true}));
        }
      }.bind(this));
    },

    getResponseObject: function(response) {
      return response.text().then(function(text) {
        var result;
        try {
          result = JSON.parse(text.substring(JSON_PREFIX.length));
        } catch (_) {
          result = null;
        }
        return result;
      });
    },

    getConfig: function() {
      return this._fetchSharedCacheURL('/config/server/info');
    },

    getProjectConfig: function(project) {
      return this._fetchSharedCacheURL(
          '/projects/' + encodeURIComponent(project) + '/config');
    },

    getVersion: function() {
      return this._fetchSharedCacheURL('/config/server/version');
    },

    getDiffPreferences: function() {
      return this._fetchSharedCacheURL('/accounts/self/preferences.diff');
    },

    saveDiffPreferences: function(prefs, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/preferences.diff', prefs,
          opt_errFn, opt_ctx);
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

    getChangeActionURL: function(changeNum, opt_patchNum, endpoint) {
      return this._changeBaseURL(changeNum, opt_patchNum) + endpoint;
    },

    getChangeDetail: function(changeNum, opt_errFn, opt_cancelCondition) {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.ALL_REVISIONS,
          ListChangesOption.CHANGE_ACTIONS,
          ListChangesOption.DOWNLOAD_COMMANDS
      );
      return this._getChangeDetail(changeNum, options, opt_errFn,
          opt_cancelCondition);
    },

    getDiffChangeDetail: function(changeNum, opt_errFn, opt_cancelCondition) {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.ALL_REVISIONS
      );
      return this._getChangeDetail(changeNum, options, opt_errFn,
          opt_cancelCondition);
    },

    _getChangeDetail: function(changeNum, options, opt_errFn,
        opt_cancelCondition) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, null, '/detail'),
          opt_errFn,
          opt_cancelCondition,
          {O: options});
    },

    getChangeCommitInfo: function(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/commit?links'));
    },

    getChangeFiles: function(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/files'));
    },

    getChangeFilesAsSpeciallySortedArray: function(changeNum, patchNum) {
      return this.getChangeFiles(changeNum, patchNum).then(
          this._normalizeChangeFilesResponse.bind(this));
    },

    getChangeFilePathsAsSpeciallySortedArray: function(changeNum, patchNum) {
      return this.getChangeFiles(changeNum, patchNum).then(function(files) {
        return Object.keys(files).sort(this._specialFilePathCompare.bind(this));
      }.bind(this));
    },

    _normalizeChangeFilesResponse: function(response) {
      var paths = Object.keys(response).sort(
          this._specialFilePathCompare.bind(this));
      var files = [];
      for (var i = 0; i < paths.length; i++) {
        var info = response[paths[i]];
        info.__path = paths[i];
        info.lines_inserted = info.lines_inserted || 0;
        info.lines_deleted = info.lines_deleted || 0;
        files.push(info);
      }
      return files;
    },

    _specialFilePathCompare: function(a, b) {
      var COMMIT_MESSAGE_PATH = '/COMMIT_MSG';
      // The commit message always goes first.
      if (a === COMMIT_MESSAGE_PATH) {
        return -1;
      }
      if (b === COMMIT_MESSAGE_PATH) {
        return 1;
      }

      var aLastDotIndex = a.lastIndexOf('.');
      var aExt = a.substr(aLastDotIndex + 1);
      var aFile = a.substr(0, aLastDotIndex);

      var bLastDotIndex = b.lastIndexOf('.');
      var bExt = b.substr(bLastDotIndex + 1);
      var bFile = a.substr(0, bLastDotIndex);

      // Sort header files above others with the same base name.
      var headerExts = ['h', 'hxx', 'hpp'];
      if (aFile.length > 0 && aFile === bFile) {
        if (headerExts.indexOf(aExt) !== -1 &&
            headerExts.indexOf(bExt) !== -1) {
          return a.localeCompare(b);
        }
        if (headerExts.indexOf(aExt) !== -1) {
          return -1;
        }
        if (headerExts.indexOf(bExt) !== -1) {
          return 1;
        }
      }

      return a.localeCompare(b);
    },

    getChangeRevisionActions: function(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/actions')).then(
              function(revisionActions) {
                // The rebase button on change screen is always enabled.
                if (revisionActions.rebase) {
                  revisionActions.rebase.enabled = true;
                }
                return revisionActions;
              });
    },

    getReviewedFiles: function(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/files?reviewed'));
    },

    saveFileReviewed: function(changeNum, patchNum, path, reviewed, opt_errFn,
        opt_ctx) {
      var method = reviewed ? 'PUT' : 'DELETE';
      var url = this.getChangeActionURL(changeNum, patchNum,
          '/files/' + encodeURIComponent(path) + '/reviewed');

      return this.send(method, url, null, opt_errFn, opt_ctx);
    },

    send: function(method, url, opt_body, opt_errFn, opt_ctx) {
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
        if (typeof opt_body !== 'string') {
          opt_body = JSON.stringify(opt_body);
        }
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
        opt_errFn, opt_cancelCondition) {
      var url = this._getDiffFetchURL(changeNum, patchNum, path);
      var params =  {
        context: 'ALL',
        intraline: null,
        whitespace: 'IGNORE_NONE',
      };
      if (basePatchNum != PARENT_PATCH_NUM) {
        params.base = basePatchNum;
      }

      return this.fetchJSON(url, opt_errFn, opt_cancelCondition, params);
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
            this._getDiffCommentsFetchURL(changeNum, endpoint));
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

    // Derived from
    // gerrit-extension-api/src/main/j/c/g/gerrit/extensions/client/ListChangesOption.java
    _listChangesOptionsToHex: function() {
      var v = 0;
      for (var i = 0; i < arguments.length; i++) {
        v |= 1 << arguments[i];
      }
      return v.toString(16);
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
