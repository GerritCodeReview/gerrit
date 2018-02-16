/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  const Defs = {};

  /**
   * @typedef {{
   *    basePatchNum: (string|number),
   *    patchNum: (number),
   * }}
   */
  Defs.patchRange;

  const DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };
  const JSON_PREFIX = ')]}\'';
  const MAX_PROJECT_RESULTS = 25;
  const MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX = 900;
  const PARENT_PATCH_NUM = 'PARENT';
  const CHECK_SIGN_IN_DEBOUNCE_MS = 3 * 1000;
  const CHECK_SIGN_IN_DEBOUNCER_NAME = 'checkCredentials';
  const FAILED_TO_FETCH_ERROR = 'Failed to fetch';

  const Requests = {
    SEND_DIFF_DRAFT: 'sendDiffDraft',
  };

  const CREATE_DRAFT_UNEXPECTED_STATUS_MESSAGE =
      'Saving draft resulted in HTTP 200 (OK) but expected HTTP 201 (Created)';
  const HEADER_REPORTING_BLACKLIST = /^set-cookie$/i;


  Polymer({
    is: 'gr-rest-api-interface',

    behaviors: [
      Gerrit.PathListBehavior,
      Gerrit.PatchSetBehavior,
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

    /**
     * Fired when credentials were rejected by server (e.g. expired).
     *
     * @event auth-error
     */

    properties: {
      _cache: {
        type: Object,
        value: {}, // Intentional to share the object across instances.
      },
      _sharedFetchPromises: {
        type: Object,
        value: {}, // Intentional to share the object across instances.
      },
      _pendingRequests: {
        type: Object,
        value: {}, // Intentional to share the object across instances.
      },
      _etags: {
        type: Object,
        value: new GrEtagDecorator(), // Share across instances.
      },
      /**
       * Used to maintain a mapping of changeNums to project names.
       */
      _projectLookup: {
        type: Object,
        value: {}, // Intentional to share the object across instances.
      },
      _auth: {
        type: Object,
        value: Gerrit.Auth, // Share across instances.
      },
    },

    JSON_PREFIX,

    /**
     * Fetch JSON from url provided.
     * Returns a Promise that resolves to a native Response.
     * Doesn't do error checking. Supports cancel condition. Performs auth.
     * Validates auth expiry errors.
     * @param {string} url
     * @param {?function(?Response, string=)=} opt_errFn
     *    passed as null sometimes.
     * @param {?function()=} opt_cancelCondition
     *    passed as null sometimes.
     * @param {?Object=} opt_params URL params, key-value hash.
     * @param {?Object=} opt_options Fetch options.
     */
    _fetchRawJSON(url, opt_errFn, opt_cancelCondition, opt_params,
        opt_options) {
      const urlWithParams = this._urlWithParams(url, opt_params);
      return this._auth.fetch(urlWithParams, opt_options).then(response => {
        if (opt_cancelCondition && opt_cancelCondition()) {
          response.body.cancel();
          return;
        }
        return response;
      }).catch(err => {
        const isLoggedIn = !!this._cache['/accounts/self/detail'];
        if (isLoggedIn && err && err.message === FAILED_TO_FETCH_ERROR) {
          if (!this.isDebouncerActive(CHECK_SIGN_IN_DEBOUNCER_NAME)) {
            this.checkCredentials();
          }
          this.debounce(CHECK_SIGN_IN_DEBOUNCER_NAME, this.checkCredentials,
              CHECK_SIGN_IN_DEBOUNCE_MS);
          return;
        }
        if (opt_errFn) {
          opt_errFn.call(undefined, null, err);
        } else {
          this.fire('network-error', {error: err});
        }
        throw err;
      });
    },

    /**
     * Fetch JSON from url provided.
     * Returns a Promise that resolves to a parsed response.
     * Same as {@link _fetchRawJSON}, plus error handling.
     * @param {string} url
     * @param {?function(?Response, string=)=} opt_errFn
     *    passed as null sometimes.
     * @param {?function()=} opt_cancelCondition
     *    passed as null sometimes.
     * @param {?Object=} opt_params URL params, key-value hash.
     * @param {?Object=} opt_options Fetch options.
     */
    fetchJSON(url, opt_errFn, opt_cancelCondition, opt_params, opt_options) {
      return this._fetchRawJSON(
          url, opt_errFn, opt_cancelCondition, opt_params, opt_options)
          .then(response => {
            if (!response) {
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
            return response && this.getResponseObject(response);
          });
    },

    /**
     * @param {string} url
     * @param {?Object=} opt_params URL params, key-value hash.
     * @return {string}
     */
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

    /**
     * @param {!Object} response
     * @return {?}
     */
    getResponseObject(response) {
      return this._readResponsePayload(response)
          .then(payload => payload.parsed);
    },

    /**
     * @param {!Object} response
     * @return {!Object}
     */
    _readResponsePayload(response) {
      return response.text().then(text => {
        let result;
        try {
          result = this._parsePrefixedJSON(text);
        } catch (_) {
          result = null;
        }
        return {parsed: result, raw: text};
      });
    },

    /**
     * @param {string} source
     * @return {?}
     */
    _parsePrefixedJSON(source) {
      return JSON.parse(source.substring(JSON_PREFIX.length));
    },

    getConfig(noCache) {
      if (!noCache) {
        return this._fetchSharedCacheURL('/config/server/info');
      }

      return this.fetchJSON('/config/server/info');
    },

    getRepo(repo, opt_errFn) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this._fetchSharedCacheURL(
          '/projects/' + encodeURIComponent(repo), opt_errFn);
    },

    getProjectConfig(repo, opt_errFn) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this._fetchSharedCacheURL(
          '/projects/' + encodeURIComponent(repo) + '/config', opt_errFn);
    },

    getRepoAccess(repo) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this._fetchSharedCacheURL(
          '/access/?project=' + encodeURIComponent(repo));
    },

    getRepoDashboards(repo, opt_errFn) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this._fetchSharedCacheURL(
          `/projects/${encodeURIComponent(repo)}/dashboards?inherited`,
          opt_errFn);
    },

    saveRepoConfig(repo, config, opt_errFn, opt_ctx) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      const encodeName = encodeURIComponent(repo);
      return this.send('PUT', `/projects/${encodeName}/config`, config,
          opt_errFn, opt_ctx);
    },

    runRepoGC(repo, opt_errFn, opt_ctx) {
      if (!repo) { return ''; }
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      const encodeName = encodeURIComponent(repo);
      return this.send('POST', `/projects/${encodeName}/gc`, '',
          opt_errFn, opt_ctx);
    },

    /**
     * @param {?Object} config
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    createRepo(config, opt_errFn, opt_ctx) {
      if (!config.name) { return ''; }
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      const encodeName = encodeURIComponent(config.name);
      return this.send('PUT', `/projects/${encodeName}`, config, opt_errFn,
          opt_ctx);
    },

    /**
     * @param {?Object} config
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    createGroup(config, opt_errFn, opt_ctx) {
      if (!config.name) { return ''; }
      const encodeName = encodeURIComponent(config.name);
      return this.send('PUT', `/groups/${encodeName}`, config, opt_errFn,
          opt_ctx);
    },

    getGroupConfig(group, opt_errFn) {
      const encodeName = encodeURIComponent(group);
      return this.fetchJSON(`/groups/${encodeName}/detail`, opt_errFn);
    },

    /**
     * @param {string} repo
     * @param {string} ref
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    deleteRepoBranches(repo, ref, opt_errFn, opt_ctx) {
      if (!repo || !ref) { return ''; }
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      const encodeName = encodeURIComponent(repo);
      const encodeRef = encodeURIComponent(ref);
      return this.send('DELETE',
          `/projects/${encodeName}/branches/${encodeRef}`, '',
          opt_errFn, opt_ctx);
    },

    /**
     * @param {string} repo
     * @param {string} ref
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    deleteRepoTags(repo, ref, opt_errFn, opt_ctx) {
      if (!repo || !ref) { return ''; }
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      const encodeName = encodeURIComponent(repo);
      const encodeRef = encodeURIComponent(ref);
      return this.send('DELETE',
          `/projects/${encodeName}/tags/${encodeRef}`, '',
          opt_errFn, opt_ctx);
    },

    /**
     * @param {string} name
     * @param {string} branch
     * @param {string} revision
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    createRepoBranch(name, branch, revision, opt_errFn, opt_ctx) {
      if (!name || !branch || !revision) { return ''; }
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      const encodeName = encodeURIComponent(name);
      const encodeBranch = encodeURIComponent(branch);
      return this.send('PUT',
          `/projects/${encodeName}/branches/${encodeBranch}`,
          revision, opt_errFn, opt_ctx);
    },

    /**
     * @param {string} name
     * @param {string} tag
     * @param {string} revision
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    createRepoTag(name, tag, revision, opt_errFn, opt_ctx) {
      if (!name || !tag || !revision) { return ''; }
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      const encodeName = encodeURIComponent(name);
      const encodeTag = encodeURIComponent(tag);
      return this.send('PUT', `/projects/${encodeName}/tags/${encodeTag}`,
          revision, opt_errFn, opt_ctx);
    },

    /**
     * @param {!string} groupName
     * @returns {!Promise<boolean>}
     */
    getIsGroupOwner(groupName) {
      const encodeName = encodeURIComponent(groupName);
      return this._fetchSharedCacheURL(`/groups/?owned&q=${encodeName}`)
          .then(configs => configs.hasOwnProperty(groupName));
    },

    getGroupMembers(groupName, opt_errFn) {
      const encodeName = encodeURIComponent(groupName);
      return this.send('GET', `/groups/${encodeName}/members/`, null, opt_errFn)
          .then(response => this.getResponseObject(response));
    },

    getIncludedGroup(groupName) {
      const encodeName = encodeURIComponent(groupName);
      return this.send('GET', `/groups/${encodeName}/groups/`)
          .then(response => this.getResponseObject(response));
    },

    saveGroupName(groupId, name) {
      const encodeId = encodeURIComponent(groupId);
      return this.send('PUT', `/groups/${encodeId}/name`, {name});
    },

    saveGroupOwner(groupId, ownerId) {
      const encodeId = encodeURIComponent(groupId);
      return this.send('PUT', `/groups/${encodeId}/owner`, {owner: ownerId});
    },

    saveGroupDescription(groupId, description) {
      const encodeId = encodeURIComponent(groupId);
      return this.send('PUT', `/groups/${encodeId}/description`,
          {description});
    },

    saveGroupOptions(groupId, options) {
      const encodeId = encodeURIComponent(groupId);
      return this.send('PUT', `/groups/${encodeId}/options`, options);
    },

    getGroupAuditLog(group, opt_errFn) {
      return this._fetchSharedCacheURL(
          '/groups/' + group + '/log.audit', opt_errFn);
    },

    saveGroupMembers(groupName, groupMembers) {
      const encodeName = encodeURIComponent(groupName);
      const encodeMember = encodeURIComponent(groupMembers);
      return this.send('PUT', `/groups/${encodeName}/members/${encodeMember}`)
          .then(response => this.getResponseObject(response));
    },

    saveIncludedGroup(groupName, includedGroup, opt_errFn) {
      const encodeName = encodeURIComponent(groupName);
      const encodeIncludedGroup = encodeURIComponent(includedGroup);
      return this.send('PUT',
          `/groups/${encodeName}/groups/${encodeIncludedGroup}`, null,
          opt_errFn).then(response => {
            if (response.ok) {
              return this.getResponseObject(response);
            }
          });
    },

    deleteGroupMembers(groupName, groupMembers) {
      const encodeName = encodeURIComponent(groupName);
      const encodeMember = encodeURIComponent(groupMembers);
      return this.send('DELETE',
          `/groups/${encodeName}/members/${encodeMember}`);
    },

    deleteIncludedGroup(groupName, includedGroup) {
      const encodeName = encodeURIComponent(groupName);
      const encodeIncludedGroup = encodeURIComponent(includedGroup);
      return this.send('DELETE',
          `/groups/${encodeName}/groups/${encodeIncludedGroup}`);
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
        // java/com/google/gerrit/extensions/client/DiffPreferencesInfo.java
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

    getEditPreferences() {
      return this.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          return this._fetchSharedCacheURL('/accounts/self/preferences.edit');
        }
        // These defaults should match the defaults in
        // java/com/google/gerrit/extensions/client/EditPreferencesInfo.java
        return Promise.resolve({
          auto_close_brackets: false,
          cursor_blink_rate: 0,
          hide_line_numbers: false,
          hide_top_menu: false,
          indent_unit: 2,
          indent_with_tabs: false,
          key_map_type: 'DEFAULT',
          line_length: 100,
          line_wrapping: false,
          match_brackets: true,
          show_base: false,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          tab_size: 8,
          theme: 'DEFAULT',
        });
      });
    },

    /**
     * @param {?Object} prefs
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    savePreferences(prefs, opt_errFn, opt_ctx) {
      // Note (Issue 5142): normalize the download scheme with lower case before
      // saving.
      if (prefs.download_scheme) {
        prefs.download_scheme = prefs.download_scheme.toLowerCase();
      }

      return this.send('PUT', '/accounts/self/preferences', prefs, opt_errFn,
          opt_ctx);
    },

    /**
     * @param {?Object} prefs
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    saveDiffPreferences(prefs, opt_errFn, opt_ctx) {
      // Invalidate the cache.
      this._cache['/accounts/self/preferences.diff'] = undefined;
      return this.send('PUT', '/accounts/self/preferences.diff', prefs,
          opt_errFn, opt_ctx);
    },

    /**
     * @param {?Object} prefs
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    saveEditPreferences(prefs, opt_errFn, opt_ctx) {
      // Invalidate the cache.
      this._cache['/accounts/self/preferences.edit'] = undefined;
      return this.send('PUT', '/accounts/self/preferences.edit', prefs,
          opt_errFn, opt_ctx);
    },

    getAccount() {
      return this._fetchSharedCacheURL('/accounts/self/detail', resp => {
        if (!resp || resp.status === 403) {
          this._cache['/accounts/self/detail'] = null;
        }
      });
    },

    getExternalIds() {
      return this.fetchJSON('/accounts/self/external.ids');
    },

    deleteAccountIdentity(id) {
      return this.send('POST', '/accounts/self/external.ids:delete', id)
          .then(response => this.getResponseObject(response));
    },

    /**
     * @param {string} userId the ID of the user usch as an email address.
     * @return {!Promise<!Object>}
     */
    getAccountDetails(userId) {
      return this.fetchJSON(`/accounts/${encodeURIComponent(userId)}/detail`);
    },

    getAccountEmails() {
      return this._fetchSharedCacheURL('/accounts/self/emails');
    },

    /**
     * @param {string} email
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    addAccountEmail(email, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/emails/' +
          encodeURIComponent(email), null, opt_errFn, opt_ctx);
    },

    /**
     * @param {string} email
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    deleteAccountEmail(email, opt_errFn, opt_ctx) {
      return this.send('DELETE', '/accounts/self/emails/' +
          encodeURIComponent(email), null, opt_errFn, opt_ctx);
    },

    /**
     * @param {string} email
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
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

    /**
     * @param {?Object} obj
     */
    _updateCachedAccount(obj) {
      // If result of getAccount is in cache, update it in the cache
      // so we don't have to invalidate it.
      const cachedAccount = this._cache['/accounts/self/detail'];
      if (cachedAccount) {
        // Replace object in cache with new object to force UI updates.
        this._cache['/accounts/self/detail'] =
            Object.assign({}, cachedAccount, obj);
      }
    },

    /**
     * @param {string} name
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    setAccountName(name, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/name', {name}, opt_errFn, opt_ctx)
          .then(response => this.getResponseObject(response)
              .then(newName => this._updateCachedAccount({name: newName})));
    },

    /**
     * @param {string} username
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    setAccountUsername(username, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/username', {username}, opt_errFn,
          opt_ctx).then(response => this.getResponseObject(response)
              .then(newName => this._updateCachedAccount({username: newName})));
    },

    /**
     * @param {string} status
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    setAccountStatus(status, opt_errFn, opt_ctx) {
      return this.send('PUT', '/accounts/self/status', {status},
          opt_errFn, opt_ctx).then(response => this.getResponseObject(response)
              .then(newStatus => this._updateCachedAccount(
                  {status: newStatus})));
    },

    getAccountStatus(userId) {
      return this.fetchJSON(`/accounts/${encodeURIComponent(userId)}/status`);
    },

    getAccountGroups() {
      return this.fetchJSON('/accounts/self/groups');
    },

    getAccountAgreements() {
      return this.fetchJSON('/accounts/self/agreements');
    },

    saveAccountAgreement(name) {
      return this.send('PUT', '/accounts/self/agreements', name);
    },

    /**
     * @param {string=} opt_params
     */
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
      return this._fetchRawJSON('/accounts/self/detail').then(response => {
        if (!response) { return; }
        if (response.status === 403) {
          this.fire('auth-error');
          this._cache['/accounts/self/detail'] = null;
        } else if (response.ok) {
          return this.getResponseObject(response);
        }
      }).then(response => {
        if (response) {
          this._cache['/accounts/self/detail'] = response;
        }
        return response;
      });
    },

    getDefaultPreferences() {
      return this._fetchSharedCacheURL('/config/server/preferences');
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
          size_bar_in_change_table: true,
        });
      });
    },

    getWatchedProjects() {
      return this._fetchSharedCacheURL('/accounts/self/watched.projects');
    },

    /**
     * @param {string} projects
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    saveWatchedProjects(projects, opt_errFn, opt_ctx) {
      return this.send('POST', '/accounts/self/watched.projects', projects,
          opt_errFn, opt_ctx)
          .then(response => {
            return this.getResponseObject(response);
          });
    },

    /**
     * @param {string} projects
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    deleteWatchedProjects(projects, opt_errFn, opt_ctx) {
      return this.send('POST', '/accounts/self/watched.projects:delete',
          projects, opt_errFn, opt_ctx);
    },

    /**
     * @param {string} url
     * @param {function(?Response, string=)=} opt_errFn
     */
    _fetchSharedCacheURL(url, opt_errFn) {
      if (this._sharedFetchPromises[url]) {
        return this._sharedFetchPromises[url];
      }
      // TODO(andybons): Periodic cache invalidation.
      if (this._cache[url] !== undefined) {
        return Promise.resolve(this._cache[url]);
      }
      this._sharedFetchPromises[url] = this.fetchJSON(url, opt_errFn)
          .then(response => {
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

    /**
     * @param {number=} opt_changesPerPage
     * @param {string|!Array<string>=} opt_query A query or an array of queries.
     * @param {number|string=} opt_offset
     * @param {!Object=} opt_options
     * @return {?Array<!Object>|?Array<!Array<!Object>>} If opt_query is an
     *     array, fetchJSON will return an array of arrays of changeInfos. If it
     *     is unspecified or a string, fetchJSON will return an array of
     *     changeInfos.
     */
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
      const iterateOverChanges = arr => {
        for (const change of (arr || [])) {
          this._maybeInsertInLookup(change);
        }
      };
      return this.fetchJSON('/changes/', null, null, params).then(response => {
        // Response may be an array of changes OR an array of arrays of
        // changes.
        if (opt_query instanceof Array) {
          // Normalize the response to look like a multi-query response
          // when there is only one query.
          if (opt_query.length === 1) {
            response = [response];
          }
          for (const arr of response) {
            iterateOverChanges(arr);
          }
        } else {
          iterateOverChanges(response);
        }
        return response;
      });
    },

    /**
     * Inserts a change into _projectLookup iff it has a valid structure.
     * @param {?{ _number: (number|string) }} change
     */
    _maybeInsertInLookup(change) {
      if (change && change.project && change._number) {
        this.setInProjectLookup(change._number, change.project);
      }
    },

    /**
     * TODO (beckysiegel) this needs to be rewritten with the optional param
     * at the end.
     *
     * @param {number|string} changeNum
     * @param {?number|string=} opt_patchNum passed as null sometimes.
     * @param {?=} endpoint
     * @return {!Promise<string>}
     */
    getChangeActionURL(changeNum, opt_patchNum, endpoint) {
      return this._changeBaseURL(changeNum, opt_patchNum)
          .then(url => url + endpoint);
    },

    /**
     * @param {number|string} changeNum
     * @param {function(?Response, string=)=} opt_errFn
     * @param {function()=} opt_cancelCondition
     */
    getChangeDetail(changeNum, opt_errFn, opt_cancelCondition) {
      const options = this.listChangesOptionsToHex(
          this.ListChangesOption.ALL_COMMITS,
          this.ListChangesOption.ALL_REVISIONS,
          this.ListChangesOption.CHANGE_ACTIONS,
          this.ListChangesOption.CURRENT_ACTIONS,
          this.ListChangesOption.DETAILED_LABELS,
          this.ListChangesOption.DOWNLOAD_COMMANDS,
          this.ListChangesOption.MESSAGES,
          this.ListChangesOption.SUBMITTABLE,
          this.ListChangesOption.WEB_LINKS,
          this.ListChangesOption.SKIP_MERGEABLE
      );
      return this._getChangeDetail(
          changeNum, options, opt_errFn, opt_cancelCondition)
          .then(GrReviewerUpdatesParser.parse);
    },

    /**
     * @param {number|string} changeNum
     * @param {function(?Response, string=)=} opt_errFn
     * @param {function()=} opt_cancelCondition
     */
    getDiffChangeDetail(changeNum, opt_errFn, opt_cancelCondition) {
      const params = this.listChangesOptionsToHex(
          this.ListChangesOption.ALL_COMMITS,
          this.ListChangesOption.ALL_REVISIONS
      );
      return this._getChangeDetail(changeNum, params, opt_errFn,
          opt_cancelCondition);
    },

    /**
     * @param {number|string} changeNum
     * @param {function(?Response, string=)=} opt_errFn
     * @param {function()=} opt_cancelCondition
     */
    _getChangeDetail(changeNum, params, opt_errFn,
        opt_cancelCondition) {
      return this.getChangeActionURL(changeNum, null, '/detail').then(url => {
        const urlWithParams = this._urlWithParams(url, params);
        return this._fetchRawJSON(
            url,
            opt_errFn,
            opt_cancelCondition,
            {O: params},
            this._etags.getOptions(urlWithParams))
            .then(response => {
              if (response && response.status === 304) {
                return Promise.resolve(this._parsePrefixedJSON(
                    this._etags.getCachedPayload(urlWithParams)));
              }

              if (response && !response.ok) {
                if (opt_errFn) {
                  opt_errFn.call(null, response);
                } else {
                  this.fire('server-error', {response});
                }
                return;
              }

              const payloadPromise = response ?
                  this._readResponsePayload(response) :
                  Promise.resolve(null);

              return payloadPromise.then(payload => {
                if (!payload) { return null; }
                this._etags.collect(urlWithParams, response, payload.raw);
                this._maybeInsertInLookup(payload.parsed);

                return payload.parsed;
              });
            });
      });
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string} patchNum
     */
    getChangeCommitInfo(changeNum, patchNum) {
      return this._getChangeURLAndFetch(changeNum, '/commit?links', patchNum);
    },

    /**
     * @param {number|string} changeNum
     * @param {Defs.patchRange} patchRange
     * @param {number=} opt_parentIndex
     */
    getChangeFiles(changeNum, patchRange, opt_parentIndex) {
      let params = undefined;
      if (this.isMergeParent(patchRange.basePatchNum)) {
        params = {parent: this.getParentIndex(patchRange.basePatchNum)};
      } else if (!this.patchNumEquals(patchRange.basePatchNum, 'PARENT')) {
        params = {base: patchRange.basePatchNum};
      }
      return this._getChangeURLAndFetch(changeNum, '/files',
          patchRange.patchNum, undefined, undefined, params);
    },

    /**
     * @param {number|string} changeNum
     * @param {Defs.patchRange} patchRange
     */
    getChangeEditFiles(changeNum, patchRange) {
      let endpoint = '/edit?list';
      if (patchRange.basePatchNum !== 'PARENT') {
        endpoint += '&base=' + encodeURIComponent(patchRange.basePatchNum + '');
      }
      return this._getChangeURLAndFetch(changeNum, endpoint);
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string} patchNum
     * @param {string} query
     * @return {!Promise<!Object>}
     */
    queryChangeFiles(changeNum, patchNum, query) {
      return this._getChangeURLAndFetch(changeNum,
          `/files?q=${encodeURIComponent(query)}`, patchNum);
    },

    /**
     * @param {number|string} changeNum
     * @param {Defs.patchRange} patchRange
     * @return {!Promise<!Array<!Object>>}
     */
    getChangeFilesAsSpeciallySortedArray(changeNum, patchRange) {
      if (this.patchNumEquals(patchRange.patchNum, this.EDIT_NAME)) {
        return this.getChangeEditFiles(changeNum, patchRange).then(res =>
          this._normalizeChangeFilesResponse(res.files));
      }
      return this.getChangeFiles(changeNum, patchRange).then(
          this._normalizeChangeFilesResponse.bind(this));
    },

    /**
     * The closure compiler doesn't realize this.specialFilePathCompare is
     * valid.
     * @suppress {checkTypes}
     */
    getChangeFilePathsAsSpeciallySortedArray(changeNum, patchRange) {
      return this.getChangeFiles(changeNum, patchRange).then(files => {
        return Object.keys(files).sort(this.specialFilePathCompare);
      });
    },

    /**
     * The closure compiler doesn't realize this.specialFilePathCompare is
     * valid.
     * @suppress {checkTypes}
     */
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
      return this._getChangeURLAndFetch(changeNum, '/actions', patchNum)
          .then(revisionActions => {
            // The rebase button on change screen is always enabled.
            if (revisionActions.rebase) {
              revisionActions.rebase.rebaseOnCurrent =
                  !!revisionActions.rebase.enabled;
              revisionActions.rebase.enabled = true;
            }
            return revisionActions;
          });
    },

    /**
     * @param {number|string} changeNum
     * @param {string} inputVal
     * @param {function(?Response, string=)=} opt_errFn
     */
    getChangeSuggestedReviewers(changeNum, inputVal, opt_errFn) {
      const params = {n: 10};
      if (inputVal) { params.q = inputVal; }
      return this._getChangeURLAndFetch(changeNum, '/suggest_reviewers', null,
          opt_errFn, null, params);
    },

    /**
     * @param {number|string} changeNum
     */
    getChangeIncludedIn(changeNum) {
      return this._getChangeURLAndFetch(changeNum, '/in', null);
    },

    _computeFilter(filter) {
      if (filter && filter.startsWith('^')) {
        filter = '&r=' + encodeURIComponent(filter);
      } else if (filter) {
        filter = '&m=' + encodeURIComponent(filter);
      } else {
        filter = '';
      }
      return filter;
    },

    /**
     * @param {string} filter
     * @param {number} groupsPerPage
     * @param {number=} opt_offset
     * @return {!Promise<?Object>}
     */
    getGroups(filter, groupsPerPage, opt_offset) {
      const offset = opt_offset || 0;

      return this._fetchSharedCacheURL(
          `/groups/?n=${groupsPerPage + 1}&S=${offset}` +
          this._computeFilter(filter)
      );
    },

    /**
     * @param {string} filter
     * @param {number} reposPerPage
     * @param {number=} opt_offset
     * @return {!Promise<?Object>}
     */
    getRepos(filter, reposPerPage, opt_offset) {
      const offset = opt_offset || 0;

      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this._fetchSharedCacheURL(
          `/projects/?d&n=${reposPerPage + 1}&S=${offset}` +
          this._computeFilter(filter)
      );
    },

    setRepoHead(repo, ref) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this.send(
          'PUT', `/projects/${encodeURIComponent(repo)}/HEAD`, {ref});
    },

    /**
     * @param {string} filter
     * @param {string} repo
     * @param {number} reposBranchesPerPage
     * @param {number=} opt_offset
     * @param {?function(?Response, string=)=} opt_errFn
     * @return {!Promise<?Object>}
     */
    getRepoBranches(filter, repo, reposBranchesPerPage, opt_offset, opt_errFn) {
      const offset = opt_offset || 0;

      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this.fetchJSON(
          `/projects/${encodeURIComponent(repo)}/branches` +
          `?n=${reposBranchesPerPage + 1}&S=${offset}` +
          this._computeFilter(filter),
          opt_errFn
      );
    },

    /**
     * @param {string} filter
     * @param {string} repo
     * @param {number} reposTagsPerPage
     * @param {number=} opt_offset
     * @param {?function(?Response, string=)=} opt_errFn
     * @return {!Promise<?Object>}
     */
    getRepoTags(filter, repo, reposTagsPerPage, opt_offset, opt_errFn) {
      const offset = opt_offset || 0;

      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this.fetchJSON(
          `/projects/${encodeURIComponent(repo)}/tags` +
          `?n=${reposTagsPerPage + 1}&S=${offset}` +
          this._computeFilter(filter),
          opt_errFn
      );
    },

    /**
     * @param {string} filter
     * @param {number} pluginsPerPage
     * @param {number=} opt_offset
     * @param {?function(?Response, string=)=} opt_errFn
     * @return {!Promise<?Object>}
     */
    getPlugins(filter, pluginsPerPage, opt_offset, opt_errFn) {
      const offset = opt_offset || 0;

      return this.fetchJSON(
          `/plugins/?all&n=${pluginsPerPage + 1}&S=${offset}` +
          this._computeFilter(filter),
          opt_errFn
      );
    },

    getRepoAccessRights(repoName, opt_errFn) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this.fetchJSON(
          `/projects/${encodeURIComponent(repoName)}/access`,
          opt_errFn
      );
    },

    setRepoAccessRights(repoName, repoInfo) {
      // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
      // supports it.
      return this.send(
          'POST', `/projects/${encodeURIComponent(repoName)}/access`,
          repoInfo);
    },

    setProjectAccessRightsForReview(projectName, projectInfo) {
      return this.send(
          'PUT', `/projects/${encodeURIComponent(projectName)}/access:review`,
          projectInfo).then(response => this.getResponseObject(response));
    },

    /**
     * @param {string} inputVal
     * @param {number} opt_n
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    getSuggestedGroups(inputVal, opt_n, opt_errFn, opt_ctx) {
      const params = {s: inputVal};
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/groups/', opt_errFn, opt_ctx, params);
    },

    /**
     * @param {string} inputVal
     * @param {number} opt_n
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    getSuggestedProjects(inputVal, opt_n, opt_errFn, opt_ctx) {
      const params = {
        m: inputVal,
        n: MAX_PROJECT_RESULTS,
        type: 'ALL',
      };
      if (opt_n) { params.n = opt_n; }
      return this.fetchJSON('/projects/', opt_errFn, opt_ctx, params);
    },

    /**
     * @param {string} inputVal
     * @param {number} opt_n
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    getSuggestedAccounts(inputVal, opt_n, opt_errFn, opt_ctx) {
      if (!inputVal) {
        return Promise.resolve([]);
      }
      const params = {suggest: null, q: inputVal};
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
      return this.getChangeActionURL(changeNum, null, '/reviewers')
          .then(url => {
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
          });
    },

    getRelatedChanges(changeNum, patchNum) {
      return this._getChangeURLAndFetch(changeNum, '/related', patchNum);
    },

    getChangesSubmittedTogether(changeNum) {
      return this._getChangeURLAndFetch(changeNum, '/submitted_together', null);
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
      return this._getChangeURLAndFetch(changeNum, '/files?reviewed', patchNum);
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string} patchNum
     * @param {string} path
     * @param {boolean} reviewed
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    saveFileReviewed(changeNum, patchNum, path, reviewed, opt_errFn, opt_ctx) {
      const method = reviewed ? 'PUT' : 'DELETE';
      const e = `/files/${encodeURIComponent(path)}/reviewed`;
      return this.getChangeURLAndSend(changeNum, method, patchNum, e, null,
          opt_errFn, opt_ctx);
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string} patchNum
     * @param {!Object} review
     * @param {function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     */
    saveChangeReview(changeNum, patchNum, review, opt_errFn, opt_ctx) {
      const promises = [
        this.awaitPendingDiffDrafts(),
        this.getChangeActionURL(changeNum, patchNum, '/review'),
      ];
      return Promise.all(promises).then(([, url]) => {
        return this.send('POST', url, review, opt_errFn, opt_ctx);
      });
    },

    getChangeEdit(changeNum, opt_download_commands) {
      const params = opt_download_commands ? {'download-commands': true} : null;
      return this.getLoggedIn().then(loggedIn => {
        return loggedIn ?
            this._getChangeURLAndFetch(changeNum, '/edit/', null, null, null,
                params) :
            false;
      });
    },

    /**
     * @param {string} project
     * @param {string} branch
     * @param {string} subject
     * @param {string=} opt_topic
     * @param {boolean=} opt_isPrivate
     * @param {boolean=} opt_workInProgress
     * @param {string=} opt_baseChange
     */
    createChange(project, branch, subject, opt_topic, opt_isPrivate,
        opt_workInProgress, opt_baseChange) {
      return this.send('POST', '/changes/', {
        project,
        branch,
        subject,
        topic: opt_topic,
        is_private: opt_isPrivate,
        work_in_progress: opt_workInProgress,
        base_change: opt_baseChange,
      }).then(response => this.getResponseObject(response));
    },

    /**
     * @param {number|string} changeNum
     * @param {string} path
     * @param {number|string} patchNum
     */
    getFileContent(changeNum, path, patchNum) {
      // 404s indicate the file does not exist yet in the revision, so suppress
      // them.
      const suppress404s = res => {
        if (res && res.status !== 404) { this.fire('server-error', {res}); }
        return res;
      };
      const promise = this.patchNumEquals(patchNum, this.EDIT_NAME) ?
          this._getFileInChangeEdit(changeNum, path) :
          this._getFileInRevision(changeNum, path, patchNum, suppress404s);

      return promise.then(res => {
        if (!res.ok) { return res; }

        // The file type (used for syntax highlighting) is identified in the
        // X-FYI-Content-Type header of the response.
        const type = res.headers.get('X-FYI-Content-Type');
        return this.getResponseObject(res).then(content => {
          return {content, type, ok: true};
        });
      });
    },

    /**
     * Gets a file in a specific change and revision.
     * @param {number|string} changeNum
     * @param {string} path
     * @param {number|string} patchNum
     * @param {?function(?Response, string=)=} opt_errFn
     */
    _getFileInRevision(changeNum, path, patchNum, opt_errFn) {
      const e = `/files/${encodeURIComponent(path)}/content`;
      const headers = {Accept: 'application/json'};
      return this.getChangeURLAndSend(changeNum, 'GET', patchNum, e, null,
          opt_errFn, null, null, headers);
    },

    /**
     * Gets a file in a change edit.
     * @param {number|string} changeNum
     * @param {string} path
     */
    _getFileInChangeEdit(changeNum, path) {
      const e = '/edit/' + encodeURIComponent(path);
      const headers = {Accept: 'application/json'};
      return this.getChangeURLAndSend(changeNum, 'GET', null, e, null, null,
          null, null, headers);
    },

    rebaseChangeEdit(changeNum) {
      return this.getChangeURLAndSend(changeNum, 'POST', null, '/edit:rebase');
    },

    deleteChangeEdit(changeNum) {
      return this.getChangeURLAndSend(changeNum, 'DELETE', null, '/edit');
    },

    restoreFileInChangeEdit(changeNum, restore_path) {
      const p = {restore_path};
      return this.getChangeURLAndSend(changeNum, 'POST', null, '/edit', p);
    },

    renameFileInChangeEdit(changeNum, old_path, new_path) {
      const p = {old_path, new_path};
      return this.getChangeURLAndSend(changeNum, 'POST', null, '/edit', p);
    },

    deleteFileInChangeEdit(changeNum, path) {
      const e = '/edit/' + encodeURIComponent(path);
      return this.getChangeURLAndSend(changeNum, 'DELETE', null, e);
    },

    saveChangeEdit(changeNum, path, contents) {
      const e = '/edit/' + encodeURIComponent(path);
      return this.getChangeURLAndSend(changeNum, 'PUT', null, e, contents, null,
          null, 'text/plain');
    },

    // Deprecated, prefer to use putChangeCommitMessage instead.
    saveChangeCommitMessageEdit(changeNum, message) {
      const p = {message};
      return this.getChangeURLAndSend(changeNum, 'PUT', null, '/edit:message',
          p);
    },

    publishChangeEdit(changeNum) {
      return this.getChangeURLAndSend(changeNum, 'POST', null,
          '/edit:publish');
    },

    putChangeCommitMessage(changeNum, message) {
      const p = {message};
      return this.getChangeURLAndSend(changeNum, 'PUT', null, '/message', p);
    },

    saveChangeStarred(changeNum, starred) {
      const url = '/accounts/self/starred.changes/' + changeNum;
      const method = starred ? 'PUT' : 'DELETE';
      return this.send(method, url);
    },

    /**
     * @param {string} method
     * @param {string} url
     * @param {?string|number|Object=} opt_body passed as null sometimes
     *    and also apparently a number. TODO (beckysiegel) remove need for
     *    number at least.
     * @param {?function(?Response, string=)=} opt_errFn
     *    passed as null sometimes.
     * @param {?=} opt_ctx
     * @param {?string=} opt_contentType
     * @param {Object=} opt_headers
     */
    send(method, url, opt_body, opt_errFn, opt_ctx, opt_contentType,
        opt_headers) {
      const options = {method};
      if (opt_body) {
        options.headers = new Headers();
        options.headers.set(
            'Content-Type', opt_contentType || 'application/json');
        if (typeof opt_body !== 'string') {
          opt_body = JSON.stringify(opt_body);
        }
        options.body = opt_body;
      }
      if (opt_headers) {
        if (!options.headers) { options.headers = new Headers(); }
        for (const header in opt_headers) {
          if (!opt_headers.hasOwnProperty(header)) { continue; }
          options.headers.set(header, opt_headers[header]);
        }
      }
      if (!url.startsWith('http')) {
        url = this.getBaseUrl() + url;
      }
      return this._auth.fetch(url, options).then(response => {
        if (!response.ok) {
          if (opt_errFn) {
            return opt_errFn.call(opt_ctx || null, response);
          }
          this.fire('server-error', {response});
        }
        return response;
      }).catch(err => {
        this.fire('network-error', {error: err});
        if (opt_errFn) {
          return opt_errFn.call(opt_ctx, null, err);
        } else {
          throw err;
        }
      });
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string} basePatchNum Negative values specify merge parent
     *     index.
     * @param {number|string} patchNum
     * @param {string} path
     * @param {function(?Response, string=)=} opt_errFn
     * @param {function()=} opt_cancelCondition
     */
    getDiff(changeNum, basePatchNum, patchNum, path,
        opt_errFn, opt_cancelCondition) {
      const params = {
        context: 'ALL',
        intraline: null,
        whitespace: 'IGNORE_NONE',
      };
      if (this.isMergeParent(basePatchNum)) {
        params.parent = this.getParentIndex(basePatchNum);
      } else if (!this.patchNumEquals(basePatchNum, PARENT_PATCH_NUM)) {
        params.base = basePatchNum;
      }
      const endpoint = `/files/${encodeURIComponent(path)}/diff`;

      return this._getChangeURLAndFetch(changeNum, endpoint, patchNum,
          opt_errFn, opt_cancelCondition, params);
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string=} opt_basePatchNum
     * @param {number|string=} opt_patchNum
     * @param {string=} opt_path
     * @return {!Promise<!Object>}
     */
    getDiffComments(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
      return this._getDiffComments(changeNum, '/comments', opt_basePatchNum,
          opt_patchNum, opt_path);
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string=} opt_basePatchNum
     * @param {number|string=} opt_patchNum
     * @param {string=} opt_path
     * @return {!Promise<!Object>}
     */
    getDiffRobotComments(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
      return this._getDiffComments(changeNum, '/robotcomments',
          opt_basePatchNum, opt_patchNum, opt_path);
    },

    /**
     * If the user is logged in, fetch the user's draft diff comments. If there
     * is no logged in user, the request is not made and the promise yields an
     * empty object.
     *
     * @param {number|string} changeNum
     * @param {number|string=} opt_basePatchNum
     * @param {number|string=} opt_patchNum
     * @param {string=} opt_path
     * @return {!Promise<!Object>}
     */
    getDiffDrafts(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
      return this.getLoggedIn().then(loggedIn => {
        if (!loggedIn) { return Promise.resolve({}); }
        return this._getDiffComments(changeNum, '/drafts', opt_basePatchNum,
            opt_patchNum, opt_path);
      });
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

    /**
     * @param {number|string} changeNum
     * @param {string} endpoint
     * @param {number|string=} opt_basePatchNum
     * @param {number|string=} opt_patchNum
     * @param {string=} opt_path
     * @return {!Promise<!Object>}
     */
    _getDiffComments(changeNum, endpoint, opt_basePatchNum,
        opt_patchNum, opt_path) {
      /**
       * Fetches the comments for a given patchNum.
       * Helper function to make promises more legible.
       *
       * @param {string|number=} opt_patchNum
       * @return {!Promise<!Object>} Diff comments response.
       */
      const fetchComments = opt_patchNum => {
        return this._getChangeURLAndFetch(changeNum, endpoint, opt_patchNum);
      };

      if (!opt_basePatchNum && !opt_patchNum && !opt_path) {
        return fetchComments();
      }
      function onlyParent(c) { return c.side == PARENT_PATCH_NUM; }
      function withoutParent(c) { return c.side != PARENT_PATCH_NUM; }
      function setPath(c) { c.path = opt_path; }

      const promises = [];
      let comments;
      let baseComments;
      let fetchPromise;
      fetchPromise = fetchComments(opt_patchNum).then(response => {
        comments = response[opt_path] || [];
        // TODO(kaspern): Implement this on in the backend so this can
        // be removed.
        // Sort comments by date so that parent ranges can be propagated
        // in a single pass.
        comments = this._setRanges(comments);

        if (opt_basePatchNum == PARENT_PATCH_NUM) {
          baseComments = comments.filter(onlyParent);
          baseComments.forEach(setPath);
        }
        comments = comments.filter(withoutParent);

        comments.forEach(setPath);
      });
      promises.push(fetchPromise);

      if (opt_basePatchNum != PARENT_PATCH_NUM) {
        fetchPromise = fetchComments(opt_basePatchNum).then(response => {
          baseComments = (response[opt_path] || [])
              .filter(withoutParent);
          baseComments = this._setRanges(baseComments);
          baseComments.forEach(setPath);
        });
        promises.push(fetchPromise);
      }

      return Promise.all(promises).then(() => {
        return Promise.resolve({
          baseComments,
          comments,
        });
      });
    },

    /**
     * @param {number|string} changeNum
     * @param {string} endpoint
     * @param {number|string=} opt_patchNum
     */
    _getDiffCommentsFetchURL(changeNum, endpoint, opt_patchNum) {
      return this._changeBaseURL(changeNum, opt_patchNum)
          .then(url => url + endpoint);
    },

    saveDiffDraft(changeNum, patchNum, draft) {
      return this._sendDiffDraftRequest('PUT', changeNum, patchNum, draft);
    },

    deleteDiffDraft(changeNum, patchNum, draft) {
      return this._sendDiffDraftRequest('DELETE', changeNum, patchNum, draft);
    },

    /**
     * @returns {boolean} Whether there are pending diff draft sends.
     */
    hasPendingDiffDrafts() {
      const promises = this._pendingRequests[Requests.SEND_DIFF_DRAFT];
      return promises && promises.length;
    },

    /**
     * @returns {!Promise<undefined>} A promise that resolves when all pending
     *    diff draft sends have resolved.
     */
    awaitPendingDiffDrafts() {
      return Promise.all(this._pendingRequests[Requests.SEND_DIFF_DRAFT] || [])
          .then(() => {
            this._pendingRequests[Requests.SEND_DIFF_DRAFT] = [];
          });
    },

    _sendDiffDraftRequest(method, changeNum, patchNum, draft) {
      const isCreate = !draft.id && method === 'PUT';
      let endpoint = '/drafts';
      if (draft.id) {
        endpoint += '/' + draft.id;
      }
      let body;
      if (method === 'PUT') {
        body = draft;
      }

      if (!this._pendingRequests[Requests.SEND_DIFF_DRAFT]) {
        this._pendingRequests[Requests.SEND_DIFF_DRAFT] = [];
      }

      const promise = this.getChangeURLAndSend(changeNum, method, patchNum,
          endpoint, body);
      this._pendingRequests[Requests.SEND_DIFF_DRAFT].push(promise);

      if (isCreate) {
        return this._failForCreate200(promise);
      }

      return promise;
    },

    getCommitInfo(project, commit) {
      return this.fetchJSON(
          '/projects/' + encodeURIComponent(project) +
          '/commits/' + encodeURIComponent(commit));
    },

    _fetchB64File(url) {
      return this._auth.fetch(this.getBaseUrl() + url)
          .then(response => {
            if (!response.ok) { return Promise.reject(response.statusText); }
            const type = response.headers.get('X-FYI-Content-Type');
            return response.text()
                .then(text => {
                  return {body: text, type};
                });
          });
    },

    /**
     * @param {string} changeId
     * @param {string|number} patchNum
     * @param {string} path
     * @param {number=} opt_parentIndex
     */
    getB64FileContents(changeId, patchNum, path, opt_parentIndex) {
      const parent = typeof opt_parentIndex === 'number' ?
          '?parent=' + opt_parentIndex : '';
      return this._changeBaseURL(changeId, patchNum).then(url => {
        url = `${url}/files/${encodeURIComponent(path)}/content${parent}`;
        return this._fetchB64File(url);
      });
    },

    getImagesForDiff(changeNum, diff, patchRange) {
      let promiseA;
      let promiseB;

      if (diff.meta_a && diff.meta_a.content_type.startsWith('image/')) {
        if (patchRange.basePatchNum === 'PARENT') {
          // Note: we only attempt to get the image from the first parent.
          promiseA = this.getB64FileContents(changeNum, patchRange.patchNum,
              diff.meta_a.name, 1);
        } else {
          promiseA = this.getB64FileContents(changeNum,
              patchRange.basePatchNum, diff.meta_a.name);
        }
      } else {
        promiseA = Promise.resolve(null);
      }

      if (diff.meta_b && diff.meta_b.content_type.startsWith('image/')) {
        promiseB = this.getB64FileContents(changeNum, patchRange.patchNum,
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

    /**
     * @param {number|string} changeNum
     * @param {?number|string=} opt_patchNum passed as null sometimes.
     * @param {string=} opt_project
     * @return {!Promise<string>}
     */
    _changeBaseURL(changeNum, opt_patchNum, opt_project) {
      // TODO(kaspern): For full slicer migration, app should warn with a call
      // stack every time _changeBaseURL is called without a project.
      const projectPromise = opt_project ?
          Promise.resolve(opt_project) :
          this.getFromProjectLookup(changeNum);
      return projectPromise.then(project => {
        let url = `/changes/${encodeURIComponent(project)}~${changeNum}`;
        if (opt_patchNum) {
          url += `/revisions/${opt_patchNum}`;
        }
        return url;
      });
    },

    /**
     * @suppress {checkTypes}
     * Resulted in error: Promise.prototype.then does not match formal
     * parameter.
     */
    setChangeTopic(changeNum, topic) {
      const p = {topic};
      return this.getChangeURLAndSend(changeNum, 'PUT', null, '/topic', p)
          .then(this.getResponseObject.bind(this));
    },

    /**
     * @suppress {checkTypes}
     * Resulted in error: Promise.prototype.then does not match formal
     * parameter.
     */
    setChangeHashtag(changeNum, hashtag) {
      return this.getChangeURLAndSend(changeNum, 'POST', null, '/hashtags',
          hashtag).then(this.getResponseObject.bind(this));
    },

    deleteAccountHttpPassword() {
      return this.send('DELETE', '/accounts/self/password.http');
    },

    /**
     * @suppress {checkTypes}
     * Resulted in error: Promise.prototype.then does not match formal
     * parameter.
     */
    generateAccountHttpPassword() {
      return this.send('PUT', '/accounts/self/password.http', {generate: true})
          .then(this.getResponseObject.bind(this));
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

    getAccountGPGKeys() {
      return this.fetchJSON('/accounts/self/gpgkeys');
    },

    addAccountGPGKey(key) {
      return this.send('POST', '/accounts/self/gpgkeys', key)
          .then(response => {
            if (response.status < 200 && response.status >= 300) {
              return Promise.reject();
            }
            return this.getResponseObject(response);
          })
          .then(obj => {
            if (!obj) { return Promise.reject(); }
            return obj;
          });
    },

    deleteAccountGPGKey(id) {
      return this.send('DELETE', '/accounts/self/gpgkeys/' + id);
    },

    deleteVote(changeNum, account, label) {
      const e = `/reviewers/${account}/votes/${encodeURIComponent(label)}`;
      return this.getChangeURLAndSend(changeNum, 'DELETE', null, e);
    },

    setDescription(changeNum, patchNum, desc) {
      const p = {description: desc};
      return this.getChangeURLAndSend(changeNum, 'PUT', patchNum,
          '/description', p);
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

    getCapabilities(token, opt_errFn) {
      return this.fetchJSON('/config/server/capabilities', opt_errFn);
    },

    setAssignee(changeNum, assignee) {
      const p = {assignee};
      return this.getChangeURLAndSend(changeNum, 'PUT', null, '/assignee', p);
    },

    deleteAssignee(changeNum) {
      return this.getChangeURLAndSend(changeNum, 'DELETE', null, '/assignee');
    },

    probePath(path) {
      return fetch(new Request(path, {method: 'HEAD'}))
          .then(response => {
            return response.ok;
          });
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string=} opt_message
     */
    startWorkInProgress(changeNum, opt_message) {
      const payload = {};
      if (opt_message) {
        payload.message = opt_message;
      }
      return this.getChangeURLAndSend(changeNum, 'POST', null, '/wip', payload)
          .then(response => {
            if (response.status === 204) {
              return 'Change marked as Work In Progress.';
            }
          });
    },

    /**
     * @param {number|string} changeNum
     * @param {number|string=} opt_body
     * @param {function(?Response, string=)=} opt_errFn
     */
    startReview(changeNum, opt_body, opt_errFn) {
      return this.getChangeURLAndSend(changeNum, 'POST', null, '/ready',
          opt_body, opt_errFn);
    },

    /**
     * @suppress {checkTypes}
     * Resulted in error: Promise.prototype.then does not match formal
     * parameter.
     */
    deleteComment(changeNum, patchNum, commentID, reason) {
      const endpoint = `/comments/${commentID}/delete`;
      const payload = {reason};
      return this.getChangeURLAndSend(changeNum, 'POST', patchNum, endpoint,
          payload).then(this.getResponseObject.bind(this));
    },

    /**
     * Given a changeNum, gets the change.
     *
     * @param {number|string} changeNum
     * @param {function(?Response, string=)=} opt_errFn
     * @return {!Promise<?Object>} The change
     */
    getChange(changeNum, opt_errFn) {
      // Cannot use _changeBaseURL, as this function is used by _projectLookup.
      return this.fetchJSON(`/changes/?q=change:${changeNum}`, opt_errFn)
          .then(res => {
            if (!res || !res.length) { return null; }
            return res[0];
          });
    },

    /**
     * @param {string|number} changeNum
     * @param {string=} project
     */
    setInProjectLookup(changeNum, project) {
      if (this._projectLookup[changeNum] &&
          this._projectLookup[changeNum] !== project) {
        console.warn('Change set with multiple project nums.' +
            'One of them must be invalid.');
      }
      this._projectLookup[changeNum] = project;
    },

    /**
     * Checks in _projectLookup for the changeNum. If it exists, returns the
     * project. If not, calls the restAPI to get the change, populates
     * _projectLookup with the project for that change, and returns the project.
     *
     * @param {string|number} changeNum
     * @return {!Promise<string|undefined>}
     */
    getFromProjectLookup(changeNum) {
      const project = this._projectLookup[changeNum];
      if (project) { return Promise.resolve(project); }

      const onError = response => {
        // Fire a page error so that the visual 404 is displayed.
        this.fire('page-error', {response});
      };

      return this.getChange(changeNum, onError).then(change => {
        if (!change || !change.project) { return; }
        this.setInProjectLookup(changeNum, change.project);
        return change.project;
      });
    },

    /**
     * Alias for _changeBaseURL.then(send).
     * @todo(beckysiegel) clean up comments
     * @param {string|number} changeNum
     * @param {string} method
     * @param {?string|number} patchNum gets passed as null.
     * @param {?string} endpoint gets passed as null.
     * @param {?Object|number|string=} opt_payload gets passed as null, string,
     *    Object, or number.
     * @param {?function(?Response, string=)=} opt_errFn
     * @param {?=} opt_ctx
     * @param {?=} opt_contentType
     * @param {Object=} opt_headers
     * @return {!Promise<!Object>}
     */
    getChangeURLAndSend(changeNum, method, patchNum, endpoint, opt_payload,
        opt_errFn, opt_ctx, opt_contentType, opt_headers) {
      return this._changeBaseURL(changeNum, patchNum).then(url => {
        return this.send(method, url + endpoint, opt_payload, opt_errFn,
            opt_ctx, opt_contentType, opt_headers);
      });
    },

   /**
    * Alias for _changeBaseURL.then(fetchJSON).
    * @todo(beckysiegel) clean up comments
    * @param {string|number} changeNum
    * @param {string} endpoint
    * @param {?string|number=} opt_patchNum gets passed as null.
    * @param {?function(?Response, string=)=} opt_errFn gets passed as null.
    * @param {?function()=} opt_cancelCondition gets passed as null.
    * @param {?Object=} opt_params gets passed as null.
    * @param {!Object=} opt_options
    * @return {!Promise<!Object>}
    */
    _getChangeURLAndFetch(changeNum, endpoint, opt_patchNum, opt_errFn,
        opt_cancelCondition, opt_params, opt_options) {
      return this._changeBaseURL(changeNum, opt_patchNum).then(url => {
        return this.fetchJSON(url + endpoint, opt_errFn, opt_cancelCondition,
            opt_params, opt_options);
      });
    },

    /**
     * Get blame information for the given diff.
     * @param {string|number} changeNum
     * @param {string|number} patchNum
     * @param {string} path
     * @param {boolean=} opt_base If true, requests blame for the base of the
     *     diff, rather than the revision.
     * @return {!Promise<!Object>}
     */
    getBlame(changeNum, patchNum, path, opt_base) {
      const encodedPath = encodeURIComponent(path);
      return this._getChangeURLAndFetch(changeNum,
          `/files/${encodedPath}/blame`, patchNum, undefined, undefined,
          opt_base ? {base: 't'} : undefined);
    },

    /**
     * Modify the given create draft request promise so that it fails and throws
     * an error if the response bears HTTP status 200 instead of HTTP 201.
     * @see Issue 7763
     * @param {Promise} promise The original promise.
     * @return {Promise} The modified promise.
     */
    _failForCreate200(promise) {
      return promise.then(result => {
        if (result.status === 200) {
          // Read the response headers into an object representation.
          const headers = Array.from(result.headers.entries())
              .reduce((obj, [key, val]) => {
                if (!HEADER_REPORTING_BLACKLIST.test(key)) {
                  obj[key] = val;
                }
                return obj;
              }, {});
          const err = new Error([
            CREATE_DRAFT_UNEXPECTED_STATUS_MESSAGE,
            JSON.stringify(headers),
          ].join('\n'));
          // Throw the error so that it is caught by gr-reporting.
          throw err;
        }
        return result;
      });
    },

    /**
     * Fetch a project dashboard definition.
     * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#get-dashboard
     * @param {string} project
     * @param {string} dashboard
     * @param {function(?Response, string=)=} opt_errFn
     *    passed as null sometimes.
     * @return {!Promise<!Object>}
     */
    getDashboard(project, dashboard, opt_errFn) {
      const url = '/projects/' + encodeURIComponent(project) + '/dashboards/' +
          encodeURIComponent(dashboard);
      return this._fetchSharedCacheURL(url, opt_errFn);
    },

    getMergeable(changeNum) {
      return this.getChangeURLAndSend(changeNum, 'GET', null,
          '/revisions/current/mergeable')
          .then(this.getResponseObject.bind(this));
    },
  });
})();
