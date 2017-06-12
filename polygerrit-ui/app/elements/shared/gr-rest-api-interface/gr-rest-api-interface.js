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

  const DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };
  const JSON_PREFIX = ')]}\'';
  const MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX = 900;
  const PARENT_PATCH_NUM = 'PARENT';

  const Requests = {
    SEND_DIFF_DRAFT: 'sendDiffDraft',
  };

  Polymer({
    is: 'gr-rest-api-interface',

    behaviors: [
      Gerrit.PathListBehavior,
      Gerrit.RESTClientBehavior,
    ],

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

    fetchJSON(url, opt_errFn, opt_cancelCondition, opt_params,
        opt_opts) {
      opt_opts = opt_opts || {};
      // Issue 5715, This can be reverted back once
      // iOS 10.3 and mac os 10.12.4 has the fetch api fix.
      const fetchOptions = {
        credentials: 'same-origin',
      };
      if (opt_opts.headers !== undefined) {
        fetchOptions['headers'] = opt_opts.headers;
      }

      const urlWithParams = this._urlWithParams(url, opt_params);
      return fetch(urlWithParams, fetchOptions).then(response => {
        if (opt_cancelCondition && opt_cancelCondition()) {
          response.body.cancel();
          return;
        }

        if (!response.ok) {
          if (opt_errFn) {
            opt_errFn.call(null, response);
            return;
          }
          this.fire('server-error', {response});
          return;
        }

        return this.getResponseObject(response);
      }).catch(err => {
        if (opt_errFn) {
          opt_errFn.call(null, null, err);
        } else {
          this.fire('network-error', {error: err});
          throw err;
        }
        throw err;
      });
    },

    _urlWithParams(url, opt_params) {
      if (!opt_params) { return this.getBaseUrl() + url; }

      const params = [];
      for (const p in opt_params) {
        if (opt_params[p] == null) {
          params.push(encodeURIComponent(p));
          continue;
        }
        for (const value of [].concat(opt_params[p])) {
          params.push(`${encodeURIComponent(p)}=${encodeURIComponent(value)}`);
        }
      }
      return this.getBaseUrl() + url + '?' + params.join('&');
    },

    getResponseObject(response) {
      return response.text().then(text => {
        let result;
        try {
          result = JSON.parse(text.substring(JSON_PREFIX.length));
        } catch (_) {
          result = null;
        }
        return result;
      });
    },

    getConfig() {
      return this._fetchSharedCacheURL('/config/server/info');
    },

    getProjectConfig(project) {
      return this._fetchSharedCacheURL(
          '/projects/' + encodeURIComponent(project) + '/config');
    },

    getProjectAccess(project) {
      return this._fetchSharedCacheURL(
          '/access/?project=' + encodeURIComponent(project));
    },

    saveProjectConfig(project, config, opt_errFn, opt_ctx) {
      return this.send('PUT', `/projects/${project}/config`, config, opt_errFn,
          opt_ctx);
    },

    getVersion() {
      return this._fetchSharedCacheURL('/config/server/version');
    },

    getDiffPreferences() {
      return this.getLoggedIn().then(loggedIn => {
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
      });
    },

    savePreferences(prefs, opt_errFn, opt_ctx) {
      // Note (Issue 5142): normalize the download scheme with lower case before
      // saving.
      if (prefs.download_scheme) {
        prefs.download_scheme = prefs.download_scheme.toLowerCase();
      }

      return this.send('PUT', '/accounts/self/preferences', prefs, opt_errFn,
          opt_ctx);
    },

    saveDiffPreferences(prefs, opt_errFn, opt_ctx) {
      // Invalidate the cache.
      this._cache['/accounts/self/preferences.diff'] = undefined;
      return this.send('PUT', '/accounts/self/preferences.diff', prefs,
          opt_errFn, opt_ctx);
    },

    getAccount() {
      return this._fetchSharedCacheURL('/accounts/self/detail', resp => {
        if (resp.status === 403) {
          this._cache['/accounts/self/detail'] = null;
        }
      });
    },

    getAccountEmails() {
      return this._fetchSharedCacheURL('/accounts/self/emails');
    },

    addAccountEmail(email, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/emails/' +
          encodeURIComponent(email), null, opt_errFn, opt_ctx);
    },

    deleteAccountEmail(email, opt_errFn, opt_ctx) {
      return this.send('DELETE', '/accounts/self/emails/' +
          encodeURIComponent(email), null, opt_errFn, opt_ctx);
    },

    setPreferredAccountEmail(email, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/emails/' +
          encodeURIComponent(email) + '/preferred', null,
          opt_errFn, opt_ctx).then(() => {
            // If result of getAccountEmails is in cache, update it in the cache
            // so we don't have to invalidate it.
            const cachedEmails = this._cache['/accounts/self/emails'];
            if (cachedEmails) {
              const emails = cachedEmails.map(entry => {
                if (entry.email === email) {
                  return {email, preferred: true};
                } else {
                  return {email};
                }
              });
              this._cache['/accounts/self/emails'] = emails;
            }
          });
    },

    getAccountUsername() {
      return this.fetchJSON('/accounts/self/username');
    },

    setAccountUsername(username, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/username', {username}, opt_errFn,
          opt_ctx).then(response => {
            // If result of getAccount is in cache, update it in the cache
            // so we don't have to invalidate it.
            const cachedAccount = this._cache['/accounts/self/detail'];
            if (cachedAccount) {
              return this.getResponseObject(response).then(newUsername => {
                // Replace object in cache with new object to force UI updates.
                // TODO(logan): Polyfill for Object.assign in IE
                this._cache['/accounts/self/detail'] = Object.assign(
                    {}, cachedAccount, {username: newUsername});
              });
            }
          });
    },


    setAccountName(name, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/name', {name}, opt_errFn,
          opt_ctx).then(response => {
            // If result of getAccount is in cache, update it in the cache
            // so we don't have to invalidate it.
            const cachedAccount = this._cache['/accounts/self/detail'];
            if (cachedAccount) {
              return this.getResponseObject(response).then(newName => {
                // Replace object in cache with new object to force UI updates.
                // TODO(logan): Polyfill for Object.assign in IE
                this._cache['/accounts/self/detail'] = Object.assign(
                    {}, cachedAccount, {name: newName});
              });
            }
          });
    },

    setAccountStatus(status, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/status', {status},
          opt_errFn, opt_ctx).then(response => {
            // If result of getAccount is in cache, update it in the cache
            // so we don't have to invalidate it.
            const cachedAccount = this._cache['/accounts/self/detail'];
            if (cachedAccount) {
              return this.getResponseObject(response).then(newStatus => {
                // Replace object in cache with new object to force UI updates.
                // TODO(logan): Polyfill for Object.assign in IE
                this._cache['/accounts/self/detail'] = Object.assign(
                    {}, cachedAccount, {status: newStatus});
              });
            }
          });
    },

    getAccountGroups() {
      return this._fetchSharedCacheURL('/accounts/self/groups');
    },

    getAccountCapabilities(opt_params) {
      let queryString = '';
      if (opt_params) {
        queryString = '?q=' + opt_params
            .map(param => { return encodeURIComponent(param); })
            .join('&q=');
      }
      return this._fetchSharedCacheURL('/accounts/self/capabilities' +
          queryString);
    },

    getLoggedIn() {
      return this.getAccount().then(account => {
        return account != null;
      });
    },

    getIsAdmin() {
      return this.getLoggedIn().then(isLoggedIn => {
        if (isLoggedIn) {
          return this.getAccountCapabilities();
        } else {
          return Promise.resolve();
        }
      }).then(capabilities => {
        return capabilities && capabilities.administrateServer;
      });
    },

    checkCredentials() {
      // Skip the REST response cache.
      return this.fetchJSON('/accounts/self/detail');
    },

    getPreferences() {
      return this.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          return this._fetchSharedCacheURL('/accounts/self/preferences').then(
              res => {
                if (this._isNarrowScreen()) {
                  res.default_diff_view = DiffViewMode.UNIFIED;
                } else {
                  res.default_diff_view = res.diff_view;
                }
                return Promise.resolve(res);
              });
        }

        return Promise.resolve({
          changes_per_page: 25,
          default_diff_view: this._isNarrowScreen() ?
              DiffViewMode.UNIFIED : DiffViewMode.SIDE_BY_SIDE,
          diff_view: 'SIDE_BY_SIDE',
        });
      });
    },

    getWatchedProjects() {
      return this._fetchSharedCacheURL('/accounts/self/watched.projects');
    },

    saveWatchedProjects(projects, opt_errFn, opt_ctx) {
      return this.send('POST', '/accounts/self/watched.projects', projects,
          opt_errFn, opt_ctx)
          .then(response => {
            return this.getResponseObject(response);
          });
    },

    deleteWatchedProjects(projects, opt_errFn, opt_ctx) {
      return this.send('POST', '/accounts/self/watched.projects:delete',
          projects, opt_errFn, opt_ctx);
    },

    _fetchSharedCacheURL(url, opt_errFn) {
      if (this._sharedFetchPromises[url]) {
        return this._sharedFetchPromises[url];
      }
      // TODO(andybons): Periodic cache invalidation.
      if (this._cache[url] !== undefined) {
        return Promise.resolve(this._cache[url]);
      }
      this._sharedFetchPromises[url] = this.fetchJSON(url, opt_errFn).then(
          response => {
            if (response !== undefined) {
              this._cache[url] = response;
            }
            this._sharedFetchPromises[url] = undefined;
            return response;
          }).catch(err => {
            this._sharedFetchPromises[url] = undefined;
            throw err;
          });
      return this._sharedFetchPromises[url];
    },

    _isNarrowScreen() {
      return window.innerWidth < MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX;
    },

    getChanges(opt_changesPerPage, opt_query, opt_offset, opt_options) {
      const options = opt_options || this.listChangesOptionsToHex(
          this.ListChangesOption.LABELS,
          this.ListChangesOption.DETAILED_ACCOUNTS
      );
      // Issue 4524: respect legacy token with max sortkey.
      if (opt_offset === 'n,z') {
        opt_offset = 0;
      }
      const params = {
        O: options,
        S: opt_offset || 0,
      };
      if (opt_changesPerPage) { params.n = opt_changesPerPage; }
      if (opt_query && opt_query.length > 0) {
        params.q = opt_query;
      }
      return this.fetchJSON('/changes/', null, null, params);
    },

    getChangeActionURL(changeNum, opt_patchNum, endpoint) {
      return this._changeBaseURL(changeNum, opt_patchNum) + endpoint;
    },

    getChangeDetail(changeNum, opt_errFn, opt_cancelCondition) {
      const options = this.listChangesOptionsToHex(
          this.ListChangesOption.ALL_REVISIONS,
          this.ListChangesOption.CHANGE_ACTIONS,
          this.ListChangesOption.CURRENT_ACTIONS,
          this.ListChangesOption.CURRENT_COMMIT,
          this.ListChangesOption.DOWNLOAD_COMMANDS,
          this.ListChangesOption.SUBMITTABLE,
          this.ListChangesOption.WEB_LINKS
      );
      return this._getChangeDetail(
          changeNum, options, opt_errFn, opt_cancelCondition)
          .then(GrReviewerUpdatesParser.parse);
    },

    getDiffChangeDetail(changeNum, opt_errFn, opt_cancelCondition) {
      const options = this.listChangesOptionsToHex(
          this.ListChangesOption.ALL_REVISIONS
      );
      return this._getChangeDetail(changeNum, options, opt_errFn,
          opt_cancelCondition);
    },

    _getChangeDetail(changeNum, options, opt_errFn,
        opt_cancelCondition) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, null, '/detail'),
          opt_errFn,
          opt_cancelCondition,
          {O: options});
    },

    getChangeCommitInfo(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/commit?links'));
    },

    getChangeFiles(changeNum, patchRange) {
      let endpoint = '/files';
      if (patchRange.basePatchNum !== 'PARENT') {
        endpoint += '?base=' + encodeURIComponent(patchRange.basePatchNum);
      }
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchRange.patchNum, endpoint));
    },

    getChangeFilesAsSpeciallySortedArray(changeNum, patchRange) {
      return this.getChangeFiles(changeNum, patchRange).then(
          this._normalizeChangeFilesResponse.bind(this));
    },

    getChangeFilePathsAsSpeciallySortedArray(changeNum, patchRange) {
      return this.getChangeFiles(changeNum, patchRange).then(files => {
        return Object.keys(files).sort(this.specialFilePathCompare);
      });
    },

    _normalizeChangeFilesResponse(response) {
      if (!response) { return []; }
      const paths = Object.keys(response).sort(this.specialFilePathCompare);
      const files = [];
      for (let i = 0; i < paths.length; i++) {
        const info = response[paths[i]];
        info.__path = paths[i];
        info.lines_inserted = info.lines_inserted || 0;
        info.lines_deleted = info.lines_deleted || 0;
        files.push(info);
      }
      return files;
    },

    getChangeRevisionActions(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/actions')).then(
          revisionActions => {
                // The rebase button on change screen is always enabled.
            if (revisionActions.rebase) {
              revisionActions.rebase.rebaseOnCurrent =
                      !!revisionActions.rebase.enabled;
              revisionActions.rebase.enabled = true;
            }
            return revisionActions;
          });
    },

    getChangeSuggestedReviewers(changeNum, inputVal, opt_errFn,
        opt_ctx) {
      const url =
          this.getChangeActionURL(changeNum, null, '/suggest_reviewers');
      return this.fetchJSON(url, opt_errFn, opt_ctx, {
        n: 10,  // Return max 10 results
        q: inputVal,
      });
    },

    getGroups(filter, groupsPerPage, opt_offset) {
      const offset = opt_offset || 0;
      filter = filter ? '&m=' + filter : '';

      return this._fetchSharedCacheURL(
          `/groups/?n=${groupsPerPage + 1}&S=${offset}${filter}`
      );
    },

    getProjects(filter, projectsPerPage, opt_offset) {
      const offset = opt_offset || 0;
      filter = filter ? '&m=' + filter : '';

      return this._fetchSharedCacheURL(
          `/projects/?d&n=${projectsPerPage + 1}&S=${offset}${filter}`
      );
    },

    getSuggestedGroups(inputVal, opt_n, opt_errFn, opt_ctx) {
      const params = {s: inputVal};
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/groups/', opt_errFn, opt_ctx, params);
    },

    getSuggestedProjects(inputVal, opt_n, opt_errFn, opt_ctx) {
      const params = {p: inputVal};
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/projects/', opt_errFn, opt_ctx, params);
    },

    getSuggestedAccounts(inputVal, opt_n, opt_errFn, opt_ctx) {
      const params = {q: inputVal, suggest: null};
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/accounts/', opt_errFn, opt_ctx, params);
    },

    addChangeReviewer(changeNum, reviewerID) {
      return this._sendChangeReviewerRequest('POST', changeNum, reviewerID);
    },

    removeChangeReviewer(changeNum, reviewerID) {
      return this._sendChangeReviewerRequest('DELETE', changeNum, reviewerID);
    },

    _sendChangeReviewerRequest(method, changeNum, reviewerID) {
      let url = this.getChangeActionURL(changeNum, null, '/reviewers');
      let body;
      switch (method) {
        case 'POST':
          body = {reviewer: reviewerID};
          break;
        case 'DELETE':
          url += '/' + encodeURIComponent(reviewerID);
          break;
        default:
          throw Error('Unsupported HTTP method: ' + method);
      }

      return this.send(method, url, body);
    },

    getRelatedChanges(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/related'));
    },

    getChangesSubmittedTogether(changeNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, null, '/submitted_together'));
    },

    getChangeConflicts(changeNum) {
      const options = this.listChangesOptionsToHex(
          this.ListChangesOption.CURRENT_REVISION,
          this.ListChangesOption.CURRENT_COMMIT
      );
      const params = {
        O: options,
        q: 'status:open is:mergeable conflicts:' + changeNum,
      };
      return this.fetchJSON('/changes/', null, null, params);
    },

    getChangeCherryPicks(project, changeID, changeNum) {
      const options = this.listChangesOptionsToHex(
          this.ListChangesOption.CURRENT_REVISION,
          this.ListChangesOption.CURRENT_COMMIT
      );
      const query = [
        'project:' + project,
        'change:' + changeID,
        '-change:' + changeNum,
        '-is:abandoned',
      ].join(' ');
      const params = {
        O: options,
        q: query,
      };
      return this.fetchJSON('/changes/', null, null, params);
    },

    getChangesWithSameTopic(topic) {
      const options = this.listChangesOptionsToHex(
          this.ListChangesOption.LABELS,
          this.ListChangesOption.CURRENT_REVISION,
          this.ListChangesOption.CURRENT_COMMIT,
          this.ListChangesOption.DETAILED_LABELS
      );
      const params = {
        O: options,
        q: 'status:open topic:' + topic,
      };
      return this.fetchJSON('/changes/', null, null, params);
    },

    getReviewedFiles(changeNum, patchNum) {
      return this.fetchJSON(
          this.getChangeActionURL(changeNum, patchNum, '/files?reviewed'));
    },

    saveFileReviewed(changeNum, patchNum, path, reviewed, opt_errFn, opt_ctx) {
      const method = reviewed ? 'PUT' : 'DELETE';
      const url = this.getChangeActionURL(changeNum, patchNum,
          '/files/' + encodeURIComponent(path) + '/reviewed');

      return this.send(method, url, null, opt_errFn, opt_ctx);
    },

    saveChangeReview(changeNum, patchNum, review, opt_errFn, opt_ctx) {
      const url = this.getChangeActionURL(changeNum, patchNum, '/review');
      return this.send('POST', url, review, opt_errFn, opt_ctx);
    },

    getFileInChangeEdit(changeNum, path) {
      return this.send('GET',
          this.getChangeActionURL(changeNum, null,
              '/edit/' + encodeURIComponent(path)
          ));
    },

    rebaseChangeEdit(changeNum) {
      return this.send('POST',
          this.getChangeActionURL(changeNum, null,
              '/edit:rebase'
          ));
    },

    deleteChangeEdit(changeNum) {
      return this.send('DELETE',
          this.getChangeActionURL(changeNum, null,
              '/edit'
          ));
    },

    restoreFileInChangeEdit(changeNum, restore_path) {
      return this.send('POST',
          this.getChangeActionURL(changeNum, null, '/edit'),
          {restore_path}
      );
    },

    renameFileInChangeEdit(changeNum, old_path, new_path) {
      return this.send('POST',
          this.getChangeActionURL(changeNum, null, '/edit'),
          {old_path},
          {new_path}
      );
    },

    deleteFileInChangeEdit(changeNum, path) {
      return this.send('DELETE',
          this.getChangeActionURL(changeNum, null,
              '/edit/' + encodeURIComponent(path)
          ));
    },

    saveChangeEdit(changeNum, path, contents) {
      return this.send('PUT',
          this.getChangeActionURL(changeNum, null,
              '/edit/' + encodeURIComponent(path)
          ),
          contents
      );
    },

    saveChangeCommitMessageEdit(changeNum, message) {
      const url = this.getChangeActionURL(changeNum, null, '/edit:message');
      return this.send('PUT', url, {message});
    },

    publishChangeEdit(changeNum) {
      return this.send('POST',
          this.getChangeActionURL(changeNum, null, '/edit:publish'));
    },

    saveChangeStarred(changeNum, starred) {
      const url = '/accounts/self/starred.changes/' + changeNum;
      const method = starred ? 'PUT' : 'DELETE';
      return this.send(method, url);
    },

    send(method, url, opt_body, opt_errFn, opt_ctx, opt_contentType) {
      const headers = new Headers({
        'X-Gerrit-Auth': this._getCookie('XSRF_TOKEN'),
      });
      const options = {
        method,
        headers,
        credentials: 'same-origin',
      };
      if (opt_body) {
        headers.append('Content-Type', opt_contentType || 'application/json');
        if (typeof opt_body !== 'string') {
          opt_body = JSON.stringify(opt_body);
        }
        options.body = opt_body;
      }
      return fetch(this.getBaseUrl() + url, options).then(response => {
        if (!response.ok) {
          if (opt_errFn) {
            opt_errFn.call(opt_ctx || null, response);
            return undefined;
          }
          this.fire('server-error', {response});
        }

        return response;
      }).catch(err => {
        this.fire('network-error', {error: err});
        if (opt_errFn) {
          opt_errFn.call(opt_ctx, null, err);
        } else {
          throw err;
        }
      });
    },

    getDiff(changeNum, basePatchNum, patchNum, path,
        opt_errFn, opt_cancelCondition) {
      const url = this._getDiffFetchURL(changeNum, patchNum, path);
      const params = {
        context: 'ALL',
        intraline: null,
        whitespace: 'IGNORE_NONE',
      };
      if (basePatchNum != PARENT_PATCH_NUM) {
        params.base = basePatchNum;
      }

      return this.fetchJSON(url, opt_errFn, opt_cancelCondition, params);
    },

    _getDiffFetchURL(changeNum, patchNum, path) {
      return this._changeBaseURL(changeNum, patchNum) + '/files/' +
          encodeURIComponent(path) + '/diff';
    },

    getDiffComments(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
      return this._getDiffComments(changeNum, '/comments', opt_basePatchNum,
          opt_patchNum, opt_path);
    },

    getDiffRobotComments(changeNum, basePatchNum, patchNum, opt_path) {
      return this._getDiffComments(changeNum, '/robotcomments', basePatchNum,
          patchNum, opt_path);
    },

    getDiffDrafts(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
      return this._getDiffComments(changeNum, '/drafts', opt_basePatchNum,
          opt_patchNum, opt_path);
    },

    _setRange(comments, comment) {
      if (comment.in_reply_to && !comment.range) {
        for (let i = 0; i < comments.length; i++) {
          if (comments[i].id === comment.in_reply_to) {
            comment.range = comments[i].range;
            break;
          }
        }
      }
      return comment;
    },

    _setRanges(comments) {
      comments = comments || [];
      comments.sort((a, b) => {
        return util.parseDate(a.updated) - util.parseDate(b.updated);
      });
      for (const comment of comments) {
        this._setRange(comments, comment);
      }
      return comments;
    },

    _getDiffComments(changeNum, endpoint, opt_basePatchNum,
        opt_patchNum, opt_path) {
      if (!opt_basePatchNum && !opt_patchNum && !opt_path) {
        return this.fetchJSON(
            this._getDiffCommentsFetchURL(changeNum, endpoint));
      }

      function onlyParent(c) { return c.side == PARENT_PATCH_NUM; }
      function withoutParent(c) { return c.side != PARENT_PATCH_NUM; }
      function setPath(c) { c.path = opt_path; }

      const promises = [];
      let comments;
      let baseComments;
      const url =
          this._getDiffCommentsFetchURL(changeNum, endpoint, opt_patchNum);
      promises.push(this.fetchJSON(url).then(response => {
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
      }));

      if (opt_basePatchNum != PARENT_PATCH_NUM) {
        const baseURL = this._getDiffCommentsFetchURL(changeNum, endpoint,
            opt_basePatchNum);
        promises.push(this.fetchJSON(baseURL).then(response => {
          baseComments = (response[opt_path] || []).filter(withoutParent);

          baseComments = this._setRanges(baseComments);

          baseComments.forEach(setPath);
        }));
      }

      return Promise.all(promises).then(() => {
        return Promise.resolve({
          baseComments,
          comments,
        });
      });
    },

    _getDiffCommentsFetchURL(changeNum, endpoint, opt_patchNum) {
      return this._changeBaseURL(changeNum, opt_patchNum) + endpoint;
    },

    saveDiffDraft(changeNum, patchNum, draft) {
      return this._sendDiffDraftRequest('PUT', changeNum, patchNum, draft);
    },

    deleteDiffDraft(changeNum, patchNum, draft) {
      return this._sendDiffDraftRequest('DELETE', changeNum, patchNum, draft);
    },

    hasPendingDiffDrafts() {
      return !!this._pendingRequests[Requests.SEND_DIFF_DRAFT];
    },

    _sendDiffDraftRequest(method, changeNum, patchNum, draft) {
      let url = this.getChangeActionURL(changeNum, patchNum, '/drafts');
      if (draft.id) {
        url += '/' + draft.id;
      }
      let body;
      if (method === 'PUT') {
        body = draft;
      }

      if (!this._pendingRequests[Requests.SEND_DIFF_DRAFT]) {
        this._pendingRequests[Requests.SEND_DIFF_DRAFT] = 0;
      }
      this._pendingRequests[Requests.SEND_DIFF_DRAFT]++;

      return this.send(method, url, body).then(res => {
        this._pendingRequests[Requests.SEND_DIFF_DRAFT]--;
        return res;
      });
    },

    _getCookie(name) {
      const key = name + '=';
      const cookies = document.cookie.split(';');
      for (let i = 0; i < cookies.length; i++) {
        let c = cookies[i];
        while (c.charAt(0) == ' ') {
          c = c.substring(1);
        }
        if (c.startsWith(key)) {
          return c.substring(key.length, c.length);
        }
      }
      return '';
    },

    getCommitInfo(project, commit) {
      return this.fetchJSON(
          '/projects/' + encodeURIComponent(project) +
          '/commits/' + encodeURIComponent(commit));
    },

    _fetchB64File(url) {
      return fetch(this.getBaseUrl() + url, {credentials: 'same-origin'})
          .then(response => {
            if (!response.ok) { return Promise.reject(response.statusText); }
            const type = response.headers.get('X-FYI-Content-Type');
            return response.text()
                .then(text => {
                  return {body: text, type};
                });
          });
    },

    getChangeFileContents(changeId, patchNum, path, opt_parentIndex) {
      const parent = typeof opt_parentIndex === 'number' ?
          '?parent=' + opt_parentIndex : '';
      return this._fetchB64File(
          '/changes/' + encodeURIComponent(changeId) +
          '/revisions/' + encodeURIComponent(patchNum) +
          '/files/' + encodeURIComponent(path) +
          '/content' + parent);
    },

    getImagesForDiff(changeNum, diff, patchRange) {
      let promiseA;
      let promiseB;

      if (diff.meta_a && diff.meta_a.content_type.startsWith('image/')) {
        if (patchRange.basePatchNum === 'PARENT') {
          // Note: we only attempt to get the image from the first parent.
          promiseA = this.getChangeFileContents(changeNum, patchRange.patchNum,
              diff.meta_a.name, 1);
        } else {
          promiseA = this.getChangeFileContents(changeNum,
              patchRange.basePatchNum, diff.meta_a.name);
        }
      } else {
        promiseA = Promise.resolve(null);
      }

      if (diff.meta_b && diff.meta_b.content_type.startsWith('image/')) {
        promiseB = this.getChangeFileContents(changeNum, patchRange.patchNum,
            diff.meta_b.name);
      } else {
        promiseB = Promise.resolve(null);
      }

      return Promise.all([promiseA, promiseB]).then(results => {
        const baseImage = results[0];
        const revisionImage = results[1];

        // Sometimes the server doesn't send back the content type.
        if (baseImage) {
          baseImage._expectedType = diff.meta_a.content_type;
          baseImage._name = diff.meta_a.name;
        }
        if (revisionImage) {
          revisionImage._expectedType = diff.meta_b.content_type;
          revisionImage._name = diff.meta_b.name;
        }

        return {baseImage, revisionImage};
      });
    },

    _changeBaseURL(changeNum, opt_patchNum) {
      let v = '/changes/' + changeNum;
      if (opt_patchNum) {
        v += '/revisions/' + opt_patchNum;
      }
      return v;
    },

    setChangeTopic(changeNum, topic) {
      return this.send('PUT', '/changes/' + encodeURIComponent(changeNum) +
          '/topic', {topic});
    },

    deleteAccountHttpPassword() {
      return this.send('DELETE', '/accounts/self/password.http');
    },

    generateAccountHttpPassword() {
      return this.send('PUT', '/accounts/self/password.http', {generate: true})
          .then(this.getResponseObject);
    },

    getAccountSSHKeys() {
      return this._fetchSharedCacheURL('/accounts/self/sshkeys');
    },

    addAccountSSHKey(key) {
      return this.send('POST', '/accounts/self/sshkeys', key, null, null,
          'plain/text')
          .then(response => {
            if (response.status < 200 && response.status >= 300) {
              return Promise.reject();
            }
            return this.getResponseObject(response);
          })
          .then(obj => {
            if (!obj.valid) { return Promise.reject(); }
            return obj;
          });
    },

    deleteAccountSSHKey(id) {
      return this.send('DELETE', '/accounts/self/sshkeys/' + id);
    },

    deleteVote(changeID, account, label) {
      return this.send('DELETE', '/changes/' + changeID +
          '/reviewers/' + account + '/votes/' + encodeURIComponent(label));
    },

    setDescription(changeNum, patchNum, desc) {
      return this.send('PUT',
          this.getChangeActionURL(changeNum, patchNum, '/description'),
          {description: desc});
    },

    confirmEmail(token) {
      return this.send('PUT', '/config/server/email.confirm', {token})
          .then(response => {
            if (response.status === 204) {
              return 'Email confirmed successfully.';
            }
            return null;
          });
    },

    setAssignee(changeNum, assignee) {
      return this.send('PUT',
          this.getChangeActionURL(changeNum, null, '/assignee'),
          {assignee});
    },

    deleteAssignee(changeNum) {
      return this.send('DELETE',
          this.getChangeActionURL(changeNum, null, '/assignee'));
    },

    probePath(path) {
      return fetch(new Request(path, {method: 'HEAD'}))
          .then(response => {
            return response.ok;
          });
    },

    startWorkInProgress(changeNum, opt_message) {
      const payload = {};
      if (opt_message) {
        payload.message = opt_message;
      }
      const url = this.getChangeActionURL(changeNum, null, '/wip');
      return this.send('POST', url, payload)
          .then(response => {
            if (response.status === 204) {
              return 'Change marked as Work In Progress.';
            }
          });
    },

    startReview(changeNum, review) {
      return this.send(
          'POST', this.getChangeActionURL(changeNum, null, '/ready'), review);
    },

    deleteComment(changeNum, patchNum, commentID, reason) {
      const url = this.changeBaseURL(changeNum, patchNum) +
          '/comments/' + commentID + '/delete';
      return this.send('POST', url, {reason}).then(response =>
        this.getResponseObject(response));
    },
  });
})();
