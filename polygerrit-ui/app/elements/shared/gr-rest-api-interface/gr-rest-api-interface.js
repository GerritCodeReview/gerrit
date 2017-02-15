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

  var DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };
  var JSON_PREFIX = ')]}\'';
  var MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX = 900;
  var PARENT_PATCH_NUM = 'PARENT';

  var Requests = {
    SEND_DIFF_DRAFT: 'sendDiffDraft',
  };

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
    PUSH_CERTIFICATES: 18,

    // Include change's reviewer updates.
    REVIEWER_UPDATES: 19,

    // Set the submittable boolean.
    SUBMITTABLE: 20,
  };

  Polymer({
    is: 'gr-rest-api-interface',
    behaviors: [Gerrit.PathListBehavior],

    /**
     * Fired when an server error occurs.
     *
     * @event server-error
     */

    /**
     * Fired when a network error occurs.
     *
     * @event network-error
     */

    properties: {
      _cache: {
        type: Object,
        value: {},  // Intentional to share the object across instances.
      },
      _sharedFetchPromises: {
        type: Object,
        value: {},  // Intentional to share the object across instances.
      },
      _pendingRequests: {
        type: Object,
        value: {},  // Intentional to share the object across instances.
      },
    },

    fetchJSON: function(url, opt_errFn, opt_cancelCondition, opt_params,
        opt_opts) {
      opt_opts = opt_opts || {};
      var fetchOptions = {
        credentials: 'same-origin',
        headers: opt_opts.headers,
      };

      var urlWithParams = this._urlWithParams(url, opt_params);
      return fetch(urlWithParams, fetchOptions).then(function(response) {
        if (opt_cancelCondition && opt_cancelCondition()) {
          response.body.cancel();
          return;
        }

        if (!response.ok) {
          if (opt_errFn) {
            opt_errFn.call(null, response);
            return;
          }
          this.fire('server-error', {response: response});
          return;
        }

        return this.getResponseObject(response);
      }.bind(this)).catch(function(err) {
        if (opt_errFn) {
          opt_errFn.call(null, null, err);
        } else {
          this.fire('network-error', {error: err});
          throw err;
        }
        throw err;
      }.bind(this));
    },

    _urlWithParams: function(url, opt_params) {
      if (!opt_params) { return url; }

      var params = [];
      for (var p in opt_params) {
        if (opt_params[p] == null) {
          params.push(encodeURIComponent(p));
          continue;
        }
        var values = [].concat(opt_params[p]);
        for (var i = 0; i < values.length; i++) {
          params.push(
            encodeURIComponent(p) + '=' +
            encodeURIComponent(values[i]));
        }
      }
      return url + '?' + params.join('&');
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
      return this.getLoggedIn().then(function(loggedIn) {
        if (loggedIn) {
          return this._fetchSharedCacheURL('/accounts/self/preferences.diff');
        }
        // These defaults should match the defaults in
        // gerrit-extension-api/src/main/jcg/gerrit/extensions/client/DiffPreferencesInfo.java
        // NOTE: There are some settings that don't apply to PolyGerrit
        // (Render mode being at least one of them).
        return Promise.resolve({
          auto_hide_diff_table_header: true,
          context: 10,
          cursor_blink_rate: 0,
          font_size: 12,
          ignore_whitespace: 'IGNORE_NONE',
          intraline_difference: true,
          line_length: 100,
          line_wrapping: false,
          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          tab_size: 8,
          theme: 'DEFAULT',
        });
      }.bind(this));
    },

    savePreferences: function(prefs, opt_errFn, opt_ctx) {
      // Note (Issue 5142): normalize the download scheme with lower case before
      // saving.
      if (prefs.download_scheme) {
        prefs.download_scheme = prefs.download_scheme.toLowerCase();
      }

      return this.send('PUT', '/accounts/self/preferences', prefs, opt_errFn,
          opt_ctx);
    },

    saveDiffPreferences: function(prefs, opt_errFn, opt_ctx) {
      // Invalidate the cache.
      this._cache['/accounts/self/preferences.diff'] = undefined;
      return this.send('PUT', '/accounts/self/preferences.diff', prefs,
          opt_errFn, opt_ctx);
    },

    getAccount: function() {
      return this._fetchSharedCacheURL('/accounts/self/detail', function(resp) {
        if (resp.status === 403) {
          this._cache['/accounts/self/detail'] = null;
        }
      }.bind(this));
    },

    getAccountEmails: function() {
      return this._fetchSharedCacheURL('/accounts/self/emails');
    },

    addAccountEmail: function(email, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/emails/' +
          encodeURIComponent(email), null, opt_errFn, opt_ctx);
    },

    deleteAccountEmail: function(email, opt_errFn, opt_ctx) {
      return this.send('DELETE', '/accounts/self/emails/' +
          encodeURIComponent(email), null, opt_errFn, opt_ctx);
    },

    setPreferredAccountEmail: function(email, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/emails/' +
          encodeURIComponent(email) + '/preferred', null,
          opt_errFn, opt_ctx).then(function() {
        // If result of getAccountEmails is in cache, update it in the cache
        // so we don't have to invalidate it.
        var cachedEmails = this._cache['/accounts/self/emails'];
        if (cachedEmails) {
          var emails = cachedEmails.map(function(entry) {
            if (entry.email === email) {
              return {email: email, preferred: true};
            } else {
              return {email: email};
            }
          });
          this._cache['/accounts/self/emails'] = emails;
        }
      }.bind(this));
    },

    setAccountName: function(name, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/name', {name: name}, opt_errFn,
          opt_ctx).then(function(response) {
            // If result of getAccount is in cache, update it in the cache
            // so we don't have to invalidate it.
            var cachedAccount = this._cache['/accounts/self/detail'];
            if (cachedAccount) {
              return this.getResponseObject(response).then(function(newName) {
                // Replace object in cache with new object to force UI updates.
                // TODO(logan): Polyfill for Object.assign in IE
                this._cache['/accounts/self/detail'] = Object.assign(
                    {}, cachedAccount, {name: newName});
              }.bind(this));
            }
          }.bind(this));
    },

    setAccountStatus: function(status, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/status', {status: status},
          opt_errFn, opt_ctx).then(function(response) {
            // If result of getAccount is in cache, update it in the cache
            // so we don't have to invalidate it.
            var cachedAccount = this._cache['/accounts/self/detail'];
            if (cachedAccount) {
              return this.getResponseObject(response).then(function(newStatus) {
                // Replace object in cache with new object to force UI updates.
                // TODO(logan): Polyfill for Object.assign in IE
                this._cache['/accounts/self/detail'] = Object.assign(
                    {}, cachedAccount, {status: newStatus});
              }.bind(this));
            }
          }.bind(this));
    },

    getAccountGroups: function() {
      return this._fetchSharedCacheURL('/accounts/self/groups');
    },

    getLoggedIn: function() {
      return this.getAccount().then(function(account) {
        return account != null;
      });
    },

    refreshCredentials: function() {
      this._cache = {};
      return this.getLoggedIn();
    },

    getPreferences: function() {
      return this.getLoggedIn().then(function(loggedIn) {
        if (loggedIn) {
          return this._fetchSharedCacheURL('/accounts/self/preferences').then(
              function(res) {
            if (this._isNarrowScreen()) {
              res.default_diff_view = DiffViewMode.UNIFIED;
            } else {
              res.default_diff_view = res.diff_view;
            }
            return Promise.resolve(res);
          }.bind(this));
        }

        return Promise.resolve({
          changes_per_page: 25,
          default_diff_view: this._isNarrowScreen() ?
              DiffViewMode.UNIFIED : DiffViewMode.SIDE_BY_SIDE,
          diff_view: 'SIDE_BY_SIDE',
        });
      }.bind(this));
    },

    getWatchedProjects: function() {
      return this._fetchSharedCacheURL('/accounts/self/watched.projects');
    },

    saveWatchedProjects: function(projects, opt_errFn, opt_ctx) {
      return this.send('POST', '/accounts/self/watched.projects', projects,
          opt_errFn, opt_ctx)
          .then(function(response) {
            return this.getResponseObject(response);
          }.bind(this));
    },

    deleteWatchedProjects: function(projects, opt_errFn, opt_ctx) {
      return this.send('POST', '/accounts/self/watched.projects:delete',
          projects, opt_errFn, opt_ctx);
    },

    _fetchSharedCacheURL: function(url, opt_errFn) {
      if (this._sharedFetchPromises[url]) {
        return this._sharedFetchPromises[url];
      }
      // TODO(andybons): Periodic cache invalidation.
      if (this._cache[url] !== undefined) {
        return Promise.resolve(this._cache[url]);
      }
      this._sharedFetchPromises[url] = this.fetchJSON(url, opt_errFn).then(
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

    _isNarrowScreen: function() {
      return window.innerWidth < MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX;
    },

    getChanges: function(changesPerPage, opt_query, opt_offset) {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.LABELS,
          ListChangesOption.DETAILED_ACCOUNTS
      );
      // Issue 4524: respect legacy token with max sortkey.
      if (opt_offset === 'n,z') {
        opt_offset = 0;
      }
      var params = {
        n: changesPerPage,
        O: options,
        S: opt_offset || 0,
      };
      if (opt_query && opt_query.length > 0) {
        params.q = opt_query;
      }
      return this.fetchJSON('/changes/', null, null, params);
    },

    getDashboardChanges: function() {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.LABELS,
          ListChangesOption.DETAILED_ACCOUNTS,
          ListChangesOption.REVIEWED
      );
      var params = {
        O: options,
        q: [
          'is:open owner:self',
          'is:open ((reviewer:self -owner:self -star:ignore) OR assignee:self)',
          'is:closed (owner:self OR reviewer:self OR assignee:self) -age:4w ' +
            'limit:10',
        ],
      };
      return this.fetchJSON('/changes/', null, null, params);
    },

    getChangeActionURL: function(changeNum, opt_patchNum, endpoint) {
      return this._changeBaseURL(changeNum, opt_patchNum) + endpoint;
    },

    getChangeDetail: function(changeNum, opt_errFn, opt_cancelCondition) {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.ALL_REVISIONS,
          ListChangesOption.CHANGE_ACTIONS,
          ListChangesOption.CURRENT_ACTIONS,
          ListChangesOption.CURRENT_COMMIT,
          ListChangesOption.DOWNLOAD_COMMANDS,
          ListChangesOption.SUBMITTABLE,
          ListChangesOption.WEB_LINKS
      );
      return this._getChangeDetail(
          changeNum, options, opt_errFn, opt_cancelCondition)
            .then(GrReviewerUpdatesParser.parse);
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

    getChangeFiles: function(changeNum, patchRange) {
      var endpoint = '/files';
      if (patchRange.basePatchNum !== 'PARENT') {
        endpoint += '?base=' + encodeURIComponent(patchRange.basePatchNum);
      }
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchRange.patchNum, endpoint));
    },

    getChangeFilesAsSpeciallySortedArray: function(changeNum, patchRange) {
      return this.getChangeFiles(changeNum, patchRange).then(
          this._normalizeChangeFilesResponse.bind(this));
    },

    getChangeFilePathsAsSpeciallySortedArray: function(changeNum, patchRange) {
      return this.getChangeFiles(changeNum, patchRange).then(function(files) {
        return Object.keys(files).sort(this.specialFilePathCompare);
      }.bind(this));
    },

    _normalizeChangeFilesResponse: function(response) {
      if (!response) { return []; }
      var paths = Object.keys(response).sort(this.specialFilePathCompare);
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

    getChangeRevisionActions: function(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/actions')).then(
              function(revisionActions) {
                // The rebase button on change screen is always enabled.
                if (revisionActions.rebase) {
                  revisionActions.rebase.rebaseOnCurrent =
                      !!revisionActions.rebase.enabled;
                  revisionActions.rebase.enabled = true;
                }
                return revisionActions;
              });
    },

    getChangeSuggestedReviewers: function(changeNum, inputVal, opt_errFn,
        opt_ctx) {
      var url = this.getChangeActionURL(changeNum, null, '/suggest_reviewers');
      return this.fetchJSON(url, opt_errFn, opt_ctx, {
        n: 10,  // Return max 10 results
        q: inputVal,
      });
    },

    getSuggestedGroups: function(inputVal, opt_n, opt_errFn, opt_ctx) {
      var params = {s: inputVal};
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/groups/', opt_errFn, opt_ctx, params);
    },

    getSuggestedProjects: function(inputVal, opt_n, opt_errFn, opt_ctx) {
      var params = {p: inputVal};
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/projects/', opt_errFn, opt_ctx, params);
    },

    getSuggestedAccounts: function(inputVal, opt_n, opt_errFn, opt_ctx) {
      var params = {q: inputVal, suggest: null};
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/accounts/', opt_errFn, opt_ctx, params);
    },

    addChangeReviewer: function(changeNum, reviewerID) {
      return this._sendChangeReviewerRequest('POST', changeNum, reviewerID);
    },

    removeChangeReviewer: function(changeNum, reviewerID) {
      return this._sendChangeReviewerRequest('DELETE', changeNum, reviewerID);
    },

    _sendChangeReviewerRequest: function(method, changeNum, reviewerID) {
      var url = this.getChangeActionURL(changeNum, null, '/reviewers');
      var body;
      switch (method) {
        case 'POST':
          body = {reviewer: reviewerID};
          break;
        case 'DELETE':
          url += '/' + reviewerID;
          break;
        default:
          throw Error('Unsupported HTTP method: ' + method);
      }

      return this.send(method, url, body);
    },

    getRelatedChanges: function(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/related'));
    },

    getChangesSubmittedTogether: function(changeNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, null, '/submitted_together'));
    },

    getChangeConflicts: function(changeNum) {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.CURRENT_REVISION,
          ListChangesOption.CURRENT_COMMIT
      );
      var params = {
        O: options,
        q: 'status:open is:mergeable conflicts:' + changeNum,
      };
      return this.fetchJSON('/changes/', null, null, params);
    },

    getChangeCherryPicks: function(project, changeID, changeNum) {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.CURRENT_REVISION,
          ListChangesOption.CURRENT_COMMIT
      );
      var query = [
        'project:' + project,
        'change:' + changeID,
        '-change:' + changeNum,
        '-is:abandoned',
      ].join(' ');
      var params = {
        O: options,
        q: query,
      };
      return this.fetchJSON('/changes/', null, null, params);
    },

    getChangesWithSameTopic: function(topic) {
      var options = this._listChangesOptionsToHex(
          ListChangesOption.LABELS,
          ListChangesOption.CURRENT_REVISION,
          ListChangesOption.CURRENT_COMMIT,
          ListChangesOption.DETAILED_LABELS
      );
      var params = {
        O: options,
        q: 'status:open topic:' + topic,
      };
      return this.fetchJSON('/changes/', null, null, params);
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

    saveChangeReview: function(changeNum, patchNum, review, opt_errFn,
        opt_ctx) {
      var url = this.getChangeActionURL(changeNum, patchNum, '/review');
      return this.send('POST', url, review, opt_errFn, opt_ctx);
    },

    saveChangeCommitMessageEdit: function(changeNum, message) {
      var url = this.getChangeActionURL(changeNum, null, '/edit:message');
      return this.send('PUT', url, {message: message});
    },

    publishChangeEdit: function(changeNum) {
      return this.send('POST',
          this.getChangeActionURL(changeNum, null, '/edit:publish'));
    },

    saveChangeStarred: function(changeNum, starred) {
      var url = '/accounts/self/starred.changes/' + changeNum;
      var method = starred ? 'PUT' : 'DELETE';
      return this.send(method, url);
    },

    send: function(method, url, opt_body, opt_errFn, opt_ctx, opt_contentType) {
      var headers = new Headers({
        'X-Gerrit-Auth': this._getCookie('XSRF_TOKEN'),
      });
      var options = {
        method: method,
        headers: headers,
        credentials: 'same-origin',
      };
      if (opt_body) {
        headers.append('Content-Type', opt_contentType || 'application/json');
        if (typeof opt_body !== 'string') {
          opt_body = JSON.stringify(opt_body);
        }
        options.body = opt_body;
      }
      return fetch(url, options).then(function(response) {
        if (!response.ok) {
          if (opt_errFn) {
            opt_errFn.call(opt_ctx || null, response);
            return undefined;
          }
          this.fire('server-error', {response: response});
        }

        return response;
      }.bind(this)).catch(function(err) {
        this.fire('network-error', {error: err});
        if (opt_errFn) {
          opt_errFn.call(opt_ctx, null, err);
        } else {
          throw err;
        }
      }.bind(this));
    },

    getDiff: function(changeNum, basePatchNum, patchNum, path,
        opt_errFn, opt_cancelCondition) {
      var url = this._getDiffFetchURL(changeNum, patchNum, path);
      var params = {
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

    getDiffRobotComments: function(changeNum, basePatchNum, patchNum,
        opt_path) {
      return this._getDiffComments(changeNum, '/robotcomments', basePatchNum,
          patchNum, opt_path);
    },

    getDiffDrafts: function(changeNum, opt_basePatchNum, opt_patchNum,
        opt_path) {
      return this._getDiffComments(changeNum, '/drafts', opt_basePatchNum,
          opt_patchNum, opt_path);
    },

    _setRange: function(comments, comment) {
      if (comment.in_reply_to && !comment.range) {
        for (var i = 0; i < comments.length; i++) {
          if (comments[i].id === comment.in_reply_to) {
            comment.range = comments[i].range;
            break;
          }
        }
      }
      return comment;
    },

    _setRanges: function(comments) {
      comments = comments || [];
      comments.sort(function(a, b) {
        return util.parseDate(a.updated) - util.parseDate(b.updated);
      });
      comments.forEach(function(comment) {
        this._setRange(comments, comment);
      }.bind(this));
      return comments;
    },

    _getDiffComments: function(changeNum, endpoint, opt_basePatchNum,
        opt_patchNum, opt_path) {
      if (!opt_basePatchNum && !opt_patchNum && !opt_path) {
        return this.fetchJSON(
            this._getDiffCommentsFetchURL(changeNum, endpoint));
      }

      function onlyParent(c) { return c.side == PARENT_PATCH_NUM; }
      function withoutParent(c) { return c.side != PARENT_PATCH_NUM; }
      function setPath(c) { c.path = opt_path; }

      var promises = [];
      var comments;
      var baseComments;
      var url =
          this._getDiffCommentsFetchURL(changeNum, endpoint, opt_patchNum);
      promises.push(this.fetchJSON(url).then(function(response) {
        comments = response[opt_path] || [];

        // TODO(kaspern): Implement this on in the backend so this can be
        // removed.

        // Sort comments by date so that parent ranges can be propagated in a
        // single pass.
        comments = this._setRanges(comments);

        if (opt_basePatchNum == PARENT_PATCH_NUM) {
          baseComments = comments.filter(onlyParent);
          baseComments.forEach(setPath);
        }
        comments = comments.filter(withoutParent);

        comments.forEach(setPath);
      }.bind(this)));

      if (opt_basePatchNum != PARENT_PATCH_NUM) {
        var baseURL = this._getDiffCommentsFetchURL(changeNum, endpoint,
            opt_basePatchNum);
        promises.push(this.fetchJSON(baseURL).then(function(response) {
          baseComments = (response[opt_path] || []).filter(withoutParent);

          baseComments = this._setRanges(baseComments);

          baseComments.forEach(setPath);
        }.bind(this)));
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

    saveDiffDraft: function(changeNum, patchNum, draft) {
      return this._sendDiffDraftRequest('PUT', changeNum, patchNum, draft);
    },

    deleteDiffDraft: function(changeNum, patchNum, draft) {
      return this._sendDiffDraftRequest('DELETE', changeNum, patchNum, draft);
    },

    hasPendingDiffDrafts: function() {
      return !!this._pendingRequests[Requests.SEND_DIFF_DRAFT];
    },

    _sendDiffDraftRequest: function(method, changeNum, patchNum, draft) {
      var url = this.getChangeActionURL(changeNum, patchNum, '/drafts');
      if (draft.id) {
        url += '/' + draft.id;
      }
      var body;
      if (method === 'PUT') {
        body = draft;
      }

      if (!this._pendingRequests[Requests.SEND_DIFF_DRAFT]) {
        this._pendingRequests[Requests.SEND_DIFF_DRAFT] = 0;
      }
      this._pendingRequests[Requests.SEND_DIFF_DRAFT]++;

      return this.send(method, url, body).then(function(res) {
        this._pendingRequests[Requests.SEND_DIFF_DRAFT]--;
        return res;
      }.bind(this));
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

    getCommitInfo: function(project, commit) {
      return this.fetchJSON(
          '/projects/' + encodeURIComponent(project) +
          '/commits/' + encodeURIComponent(commit));
    },

    _fetchB64File: function(url) {
      return fetch(url, {credentials: 'same-origin'}).then(function(response) {
        var type = response.headers.get('X-FYI-Content-Type');
        return response.text()
          .then(function(text) {
            return {body: text, type: type};
          });
      });
    },

    getChangeFileContents: function(changeId, patchNum, path) {
      return this._fetchB64File(
          '/changes/' + encodeURIComponent(changeId) +
          '/revisions/' + encodeURIComponent(patchNum) +
          '/files/' + encodeURIComponent(path) +
          '/content');
    },

    getCommitFileContents: function(projectName, commit, path) {
      return this._fetchB64File(
          '/projects/' + encodeURIComponent(projectName) +
          '/commits/' + encodeURIComponent(commit) +
          '/files/' + encodeURIComponent(path) +
          '/content');
    },

    getImagesForDiff: function(project, commit, changeNum, diff, patchRange) {
      var promiseA;
      var promiseB;

      if (diff.meta_a && diff.meta_a.content_type.indexOf('image/') === 0) {
        if (patchRange.basePatchNum === 'PARENT') {
          // Need the commit info know the parent SHA.
          promiseA = this.getCommitInfo(project, commit).then(function(info) {
            if (info.parents.length !== 1) {
              return Promise.reject('Change commit has multiple parents.');
            }
            var parent = info.parents[0].commit;
            return this.getCommitFileContents(project, parent,
                diff.meta_a.name);
          }.bind(this));

        } else {
          promiseA = this.getChangeFileContents(changeNum,
              patchRange.basePatchNum, diff.meta_a.name);
        }
      } else {
        promiseA = Promise.resolve(null);
      }

      if (diff.meta_b && diff.meta_b.content_type.indexOf('image/') === 0) {
        promiseB = this.getChangeFileContents(changeNum, patchRange.patchNum,
            diff.meta_b.name);
      } else {
        promiseB = Promise.resolve(null);
      }

      return Promise.all([promiseA, promiseB])
        .then(function(results) {
          var baseImage = results[0];
          var revisionImage = results[1];

          // Sometimes the server doesn't send back the content type.
          if (baseImage) {
            baseImage._expectedType = diff.meta_a.content_type;
          }
          if (revisionImage) {
            revisionImage._expectedType = diff.meta_b.content_type;
          }

          return {baseImage: baseImage, revisionImage: revisionImage};
        }.bind(this));
    },

    setChangeTopic: function(changeNum, topic) {
      return this.send('PUT', '/changes/' + encodeURIComponent(changeNum) +
          '/topic', {topic: topic});
    },

    getAccountHttpPassword: function(opt_errFn) {
      return this._fetchSharedCacheURL('/accounts/self/password.http',
          opt_errFn);
    },

    deleteAccountHttpPassword: function() {
      return this.send('DELETE', '/accounts/self/password.http');
    },

    generateAccountHttpPassword: function() {
      return this.send('PUT', '/accounts/self/password.http', {generate: true})
          .then(this.getResponseObject);
    },

    getAccountSSHKeys: function() {
      return this._fetchSharedCacheURL('/accounts/self/sshkeys');
    },

    addAccountSSHKey: function(key) {
      return this.send('POST', '/accounts/self/sshkeys', key, null, null,
          'plain/text')
          .then(function(response) {
            if (response.status < 200 && response.status >= 300) {
              return Promise.reject();
            }
            return this.getResponseObject(response);
          }.bind(this))
          .then(function(obj) {
            if (!obj.valid) { return Promise.reject(); }
            return obj;
          });
    },

    deleteAccountSSHKey: function(id) {
      return this.send('DELETE', '/accounts/self/sshkeys/' + id);
    },

    deleteVote: function(changeID, account, label) {
      return this.send('DELETE', '/changes/' + changeID +
          '/reviewers/' + account + '/votes/' + encodeURIComponent(label));
    },

    setDescription: function(changeNum, patchNum, desc) {
      return this.send('PUT',
          this.getChangeActionURL(changeNum, patchNum, '/description'),
          {description: desc});
    },

    confirmEmail: function(token) {
      return this.send('PUT', '/config/server/email.confirm', {token: token})
          .then(function(response) {
            if (response.status === 204) {
              return 'Email confirmed successfully.';
            }
            return null;
          });
    },

    setAssignee: function(changeNum, assignee) {
      return this.send('PUT',
          this.getChangeActionURL(changeNum, null, '/assignee'),
          {assignee: assignee});
    },

    deleteAssignee: function(changeNum) {
      return this.send('DELETE',
          this.getChangeActionURL(changeNum, null, '/assignee'));
    },
  });
})();
