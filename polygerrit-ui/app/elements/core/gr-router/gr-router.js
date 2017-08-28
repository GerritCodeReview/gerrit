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

  const RoutePattern = {
    ROOT: '/',
    DASHBOARD: '/dashboard/(.*)',
    ADMIN_PLACEHOLDER: '/admin/(.*)',
    AGREEMENTS: /^\/settings\/(agreements|new-agreement)/,
    REGISTER: /^\/register(\/.*)?/,

    // Matches /admin/groups/<group>
    GROUP: /^\/admin\/groups\/([^,]+)$/,

    // Matches /admin/groups/<group>,info (backwords compat with gwtui)
    // Redirects to /admin/groups/<group>
    GROUP_INFO: /^\/admin\/groups\/(.+),info$/,

    // Matches /admin/groups/<group>,audit-log
    GROUP_AUDIT_LOG: /^\/admin\/groups\/(.+),audit-log$/,

    // Matches /admin/groups/<group>,members
    GROUP_MEMBERS: /^\/admin\/groups\/(.+),members$/,

    // Matches /admin/groups[,<offset>][/].
    GROUP_LIST_OFFSET: /^\/admin\/groups(,(\d+))?(\/)?$/,
    GROUP_LIST_FILTER: '/admin/groups/q/filter::filter',
    GROUP_LIST_FILTER_OFFSET: '/admin/groups/q/filter::filter,:offset',

    // Matches /admin/projects/<project>
    PROJECT: /^\/admin\/projects\/([^,]+)$/,

    // Matches /admin/projects/<project>,commands.
    PROJECT_COMMANDS: /^\/admin\/projects\/(.+),commands$/,

    // Matches /admin/projects[,<offset>][/].
    PROJECT_LIST_OFFSET: /^\/admin\/projects(,(\d+))?(\/)?$/,
    PROJECT_LIST_FILTER: '/admin/projects/q/filter::filter',
    PROJECT_LIST_FILTER_OFFSET: '/admin/projects/q/filter::filter,:offset',

    // Matches /admin/projects/<project>,branches[,<offset>].
    BRANCH_LIST_OFFSET: /^\/admin\/projects\/(.+),branches(,(.+))?$/,
    BRANCH_LIST_FILTER: '/admin/projects/:project,branches/q/filter::filter',
    BRANCH_LIST_FILTER_OFFSET:
        '/admin/projects/:project,branches/q/filter::filter,:offset',

    // Matches /admin/projects/<project>,tags[,<offset>].
    TAG_LIST_OFFSET: /^\/admin\/projects\/(.+),tags(,(.+))?$/,
    TAG_LIST_FILTER: '/admin/projects/:project,tags/q/filter::filter',
    TAG_LIST_FILTER_OFFSET:
        '/admin/projects/:project,tags/q/filter::filter,:offset',

    PLUGIN_LIST: /^\/admin\/plugins(\/)?$/,

    // Matches /admin/plugins[,<offset>][/].
    PLUGIN_LIST_OFFSET: /^\/admin\/plugins(,(\d+))?(\/)?$/,
    PLUGIN_LIST_FILTER: '/admin/plugins/q/filter::filter',
    PLUGIN_LIST_FILTER_OFFSET: '/admin/plugins/q/filter::filter,:offset',

    QUERY: '/q/:query',
    QUERY_OFFSET: '/q/:query,:offset',

    // Matches /c/<changeNum>/[<basePatchNum>..][<patchNum>][/].
    CHNAGE_LEGACY: /^\/c\/(\d+)\/?(((\d+|edit)(\.\.(\d+|edit))?))?\/?$/,
    CHANGE_NUMBER_LEGACY: /^\/(\d+)\/?/,

    // Matches
    // /c/<project>/+/<changeNum>/
    //     [<basePatchNum|edit>..][<patchNum|edit>]/[path].
    // TODO(kaspern): Migrate completely to project based URLs, with backwards
    // compatibility for change-only.
    // eslint-disable-next-line max-len
    CHANGE_OR_DIFF: /^\/c\/(.+)\/\+\/(\d+)(\/?((\d+|edit)(\.\.(\d+|edit))?(\/(.+))?))?\/?$/,

    // Matches /c/<changeNum>/[<basePatchNum>..]<patchNum>/<path>.
    DIFF_LEGACY: /^\/c\/(\d+)\/((\d+|edit)(\.\.(\d+|edit))?)\/(.+)/,

    SETTINGS: /^\/settings\/?/,
    SETTINGS_LEGACY: /^\/settings\/VE\/(\S+)/,
  };

  /**
   * Pattern to recognize and parse the diff line locations as they appear at
   * the end of diff URLs. In this format, a number on its own indicates that
   * line number in the revision of the diff. A number prefixed by either an 'a'
   * or a 'b' indicates that line number of the base of the diff.
   * @type {RegExp}
   */
  const LINE_ADDRESS_PATTERN = /^([ab]?)(\d+)$/;

  // Polymer makes `app` intrinsically defined on the window by virtue of the
  // custom element having the id "app", but it is made explicit here.
  const app = document.querySelector('#app');
  if (!app) {
    console.log('No gr-app found (running tests)');
  }

  let _reporting;
  function getReporting() {
    if (!_reporting) {
      _reporting = document.createElement('gr-reporting');
    }
    return _reporting;
  }

  document.onload = function() {
    getReporting().pageLoaded();
  };

  window.addEventListener('WebComponentsReady', () => {
    getReporting().timeEnd('WebComponentsReady');
  });

  Polymer({
    is: 'gr-router',

    properties: {
      _restAPI: {
        type: Object,
        value: () => document.createElement('gr-rest-api-interface'),
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    start() {
      if (!app) { return; }
      this._startRouter();
    },

    _setParams(params) {
      app.params = params;
    },

    _redirect(url) {
      page.redirect(url);
    },

    _generateUrl(params) {
      const base = this.getBaseUrl();
      let url = '';

      if (params.view === Gerrit.Nav.View.SEARCH) {
        const operators = [];
        if (params.owner) {
          operators.push('owner:' + this.encodeURL(params.owner, false));
        }
        if (params.project) {
          operators.push('project:' + this.encodeURL(params.project, false));
        }
        if (params.branch) {
          operators.push('branch:' + this.encodeURL(params.branch, false));
        }
        if (params.topic) {
          operators.push('topic:"' + this.encodeURL(params.topic, false) + '"');
        }
        if (params.hashtag) {
          operators.push('hashtag:"' +
              this.encodeURL(params.hashtag.toLowerCase(), false) + '"');
        }
        if (params.statuses) {
          if (params.statuses.length === 1) {
            operators.push(
                'status:' + this.encodeURL(params.statuses[0], false));
          } else if (params.statuses.length > 1) {
            operators.push(
                '(' +
                params.statuses.map(s => `status:${this.encodeURL(s, false)}`)
                    .join(' OR ') +
                ')');
          }
        }
        url = '/q/' + operators.join('+');
      } else if (params.view === Gerrit.Nav.View.CHANGE) {
        let range = this._getPatchRangeExpression(params);
        if (range.length) { range = '/' + range; }
        if (params.project) {
          url = `/c/${params.project}/+/${params.changeNum}${range}`;
        } else {
          url = `/c/${params.changeNum}${range}`;
        }
      } else if (params.view === Gerrit.Nav.View.DASHBOARD) {
        url = `/dashboard/${params.user || 'self'}`;
      } else if (params.view === Gerrit.Nav.View.DIFF) {
        let range = this._getPatchRangeExpression(params);
        if (range.length) { range = '/' + range; }

        let suffix = `${range}/${this.encodeURL(params.path, true)}`;
        if (params.lineNum) {
          suffix += '#';
          if (params.leftSide) { suffix += 'b'; }
          suffix += params.lineNum;
        }

        if (params.project) {
          url = `/c/${params.project}/+/${params.changeNum}${suffix}`;
        } else {
          url = `/c/${params.changeNum}${suffix}`;
        }
      } else {
        throw new Error('Can\'t generate');
      }

      return base + url;
    },

    /**
     * Given an object of parameters, potentially including a `patchNum` or a
     * `basePatchNum` or both, return a string representation of that range. If
     * no range is indicated in the params, the empty string is returned.
     * @param {!Object} params
     * @return {string}
     */
    _getPatchRangeExpression(params) {
      let range = '';
      if (params.patchNum) { range = '' + params.patchNum; }
      if (params.basePatchNum) { range = params.basePatchNum + '..' + range; }
      return range;
    },

    /**
     * Given a set of params without a project, gets the project from the rest
     * API project lookup and then sets the app params.
     *
     * @param {?Object} params
     */
    _normalizeLegacyRouteParams(params) {
      if (!params.changeNum) { return Promise.resolve(); }

      return this._restAPI.getFromProjectLookup(params.changeNum)
          .then(project => {
            params.project = project;
            this._normalizePatchRangeParams(params);
            this._redirect(this._generateUrl(params));
          });
    },

    /**
     * Normalizes the params object, and determines if the URL needs to be
     * modified to fit the proper schema.
     *
     * @param {*} params
     * @return {boolean} whether or not the URL needs to be upgraded.
     */
    _normalizePatchRangeParams(params) {
      const hasBasePatchNum = params.basePatchNum !== null &&
          params.basePatchNum !== undefined;
      const hasPatchNum = params.patchNum !== null &&
          params.patchNum !== undefined;
      let needsRedirect = false;
      if (hasBasePatchNum &&
          this.patchNumEquals(params.basePatchNum, params.patchNum)) {
        needsRedirect = true;
        params.basePatchNum = null;
      } else if (hasBasePatchNum && !hasPatchNum) {
        // Regexes set basePatchNum instead of patchNum when only one is
        // specified. Redirect is not needed in this case.
        params.patchNum = params.basePatchNum;
        params.basePatchNum = null;
      }
      // In GWTUI, edits are represented in URLs with either 0 or 'edit'.
      // TODO(kaspern): Remove this normalization when GWT UI is gone.
      if (this.patchNumEquals(params.basePatchNum, 0)) {
        params.basePatchNum = this.EDIT_NAME;
        needsRedirect = true;
      }
      if (this.patchNumEquals(params.patchNum, 0)) {
        params.patchNum = this.EDIT_NAME;
        needsRedirect = true;
      }
      return needsRedirect;
    },

    /**
     * Redirect the user to login using the given return-URL for redirection
     * after authentication success.
     * @param {string} returnUrl
     */
    _redirectToLogin(returnUrl) {
      const basePath = this.getBaseUrl() || '';
      page(
          '/login/' + encodeURIComponent(returnUrl.substring(basePath.length)));
    },

    /**
     * Hashes parsed by page.js exclude "inner" hashes, so a URL like "/a#b#c"
     * is parsed to have a hash of "b" rather than "b#c". Instead, this method
     * parses hashes correctly. Will return an empty string if there is no hash.
     * @param {!string} canonicalPath
     * @return {!string} Everything after the first '#' ("a#b#c" -> "b#c").
     */
    _getHashFromCanonicalPath(canonicalPath) {
      return canonicalPath.split('#').slice(1).join('#');
    },

    _parseLineAddress(hash) {
      const match = hash.match(LINE_ADDRESS_PATTERN);
      if (!match) { return null; }
      return {
        leftSide: !!match[1],
        lineNum: parseInt(match[2], 10),
      };
    },

    /**
     * Check to see if the user is logged in and return a promise that only
     * resolves if the user is logged in. If the user us not logged in, the
     * promise is rejected and the page is redirected to the login flow.
     * @param {!Object} data The parsed route data.
     * @return {!Promise<!Object>} A promise yielding the original route data
     *     (if it resolves).
     */
    _redirectIfNotLoggedIn(data) {
      return this._restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          return Promise.resolve();
        } else {
          this._redirectToLogin(data.canonicalPath);
          return Promise.reject();
        }
      });
    },

    /**  Page.js middleware that warms the REST API's logged-in cache line. */
    _loadUserMiddleware(ctx, next) {
      this._restAPI.getLoggedIn().then(() => { next(); });
    },

    /**
     * Map a route to a method on the router.
     *
     * @param {!string|!RegExp} pattern The page.js pattern for the route.
     * @param {!string} handlerName The method name for the handler. If the
     *     route is matched, the handler will be executed with `this` referring
     *     to the component. Its return value will be discarded so that it does
     *     not interfere with page.js.
     * @param  {?boolean=} opt_authRedirect If true, then auth is checked before
     *     executing the handler. If the user is not logged in, it will redirect
     *     to the login flow and the handler will not be executed. The login
     *     redirect specifies the matched URL to be used after successfull auth.
     */
    _mapRoute(pattern, handlerName, opt_authRedirect) {
      if (!this[handlerName]) {
        console.error('Attempted to map route to unknown method: ',
            handlerName);
        return;
      }
      page(pattern, this._loadUserMiddleware.bind(this), data => {
        const promise = opt_authRedirect ?
          this._redirectIfNotLoggedIn(data) : Promise.resolve();
        promise.then(() => { this[handlerName](data); });
      });
    },

    _startRouter() {
      const base = this.getBaseUrl();
      if (base) {
        page.base(base);
      }

      const reporting = getReporting();

      Gerrit.Nav.setup(url => { page.show(url); },
          this._generateUrl.bind(this));

      // Middleware
      page((ctx, next) => {
        document.body.scrollTop = 0;

        // Fire asynchronously so that the URL is changed by the time the event
        // is processed.
        this.async(() => {
          this.fire('location-change', {
            hash: window.location.hash,
            pathname: window.location.pathname,
          });
          reporting.locationChanged();
        }, 1);
        next();
      });

      this._mapRoute(RoutePattern.ROOT, '_handleRootRoute');

      this._mapRoute(RoutePattern.DASHBOARD, '_handleDashboardRoute');

      this._mapRoute(RoutePattern.GROUP_INFO, '_handleGroupInfoRoute', true);

      this._mapRoute(RoutePattern.GROUP_AUDIT_LOG, '_handleGroupAuditLogRoute',
          true);

      this._mapRoute(RoutePattern.GROUP_MEMBERS, '_handleGroupMembersRoute');

      this._mapRoute(RoutePattern.GROUP_LIST_OFFSET,
          '_handleGroupListOffsetRoute', true);

      this._mapRoute(RoutePattern.GROUP_LIST_FILTER_OFFSET,
          '_handleGroupListFilterOffsetRoute', true);

      this._mapRoute(RoutePattern.GROUP_LIST_FILTER,
          '_handleGroupListFilterRoute', true);

      this._mapRoute(RoutePattern.GROUP, '_handleGroupRoute', true);

      this._mapRoute(RoutePattern.PROJECT_COMMANDS,
          '_handleProjectCommandsRoute', true);

      this._mapRoute(RoutePattern.BRANCH_LIST_OFFSET,
          '_handleBranchListOffsetRoute');

      this._mapRoute(RoutePattern.BRANCH_LIST_FILTER_OFFSET,
          '_handleBranchListFilterOffsetRoute');

      this._mapRoute(RoutePattern.BRANCH_LIST_FILTER,
          '_handleBranchListFilterRoute');

      this._mapRoute(RoutePattern.TAG_LIST_OFFSET,
          '_handleTagListOffsetRoute');

      this._mapRoute(RoutePattern.TAG_LIST_FILTER_OFFSET,
          '_handleTagListFilterOffsetRoute');

      this._mapRoute(RoutePattern.TAG_LIST_FILTER,
          '_handleTagListFilterRoute');

      this._mapRoute(RoutePattern.PROJECT_LIST_OFFSET,
          '_handleProjectListOffsetRoute');

      this._mapRoute(RoutePattern.PROJECT_LIST_FILTER_OFFSET,
          '_handleProjectListFilterOffsetRoute');

      this._mapRoute(RoutePattern.PROJECT_LIST_FILTER,
          '_handleProjectListFilterRoute');

      this._mapRoute(RoutePattern.PROJECT, '_handleProjectRoute');

      this._mapRoute(RoutePattern.PLUGIN_LIST_OFFSET,
          '_handlePluginListOffsetRoute', true);

      this._mapRoute(RoutePattern.PLUGIN_LIST_FILTER_OFFSET,
          '_handlePluginListFilterOffsetRoute', true);

      this._mapRoute(RoutePattern.PLUGIN_LIST_FILTER,
          '_handlePluginListFilterRoute', true);

      this._mapRoute(RoutePattern.PLUGIN_LIST, '_handlePluginListRoute', true);

      this._mapRoute(RoutePattern.ADMIN_PLACEHOLDER,
          '_handleAdminPlaceholderRoute', true);

      this._mapRoute(RoutePattern.QUERY_OFFSET, '_handleQueryRoute');
      this._mapRoute(RoutePattern.QUERY, '_handleQueryRoute');

      this._mapRoute(RoutePattern.CHANGE_NUMBER_LEGACY,
          '_handleChangeNumberLegacyRoute');

      this._mapRoute(RoutePattern.CHANGE_OR_DIFF, '_handleChangeOrDiffRoute');

      this._mapRoute(RoutePattern.CHNAGE_LEGACY, '_handleChnageLegacyRoute');

      this._mapRoute(RoutePattern.DIFF_LEGACY, '_handleDiffLegacyRoute');

      this._mapRoute(RoutePattern.AGREEMENTS, '_handleAgreementsRoute', true);

      this._mapRoute(RoutePattern.SETTINGS_LEGACY,
          '_handleSettingsLegacyRoute', true);

      this._mapRoute(RoutePattern.SETTINGS, '_handleSettingsRoute', true);

      this._mapRoute(RoutePattern.REGISTER, '_handleRegisterRoute');

      page.start();
    },

    /**
     * @param {!Object} data
     * @return {Promise|null} if handling the route involves asynchrony, then a
     *     promise is returned. Otherwise, synchronous handling returns null.
     */
    _handleRootRoute(data) {
      if (data.querystring.match(/^closeAfterLogin/)) {
        // Close child window on redirect after login.
        window.close();
        return null;
      }
      let hash = this._getHashFromCanonicalPath(data.canonicalPath);
      // For backward compatibility with GWT links.
      if (hash) {
        // In certain login flows the server may redirect to a hash without
        // a leading slash, which page.js doesn't handle correctly.
        if (hash[0] !== '/') {
          hash = '/' + hash;
        }
        if (hash.includes('/ /') && data.canonicalPath.includes('/+/')) {
          // Path decodes all '+' to ' ' -- this breaks project-based URLs.
          // See Issue 6888.
          hash = hash.replace('/ /', '/+/');
        }
        const base = this.getBaseUrl();
        let newUrl = base + hash;
        if (hash.startsWith('/VE/')) {
          newUrl = base + '/settings' + hash;
        }
        this._redirect(newUrl);
        return null;
      }
      return this._restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          this._redirect('/dashboard/self');
        } else {
          this._redirect('/q/status:open');
        }
      });
    },

    _handleDashboardRoute(data) {
      if (!data.params[0]) {
        this._redirect('/dashboard/self');
        return;
      }

      return this._restAPI.getLoggedIn().then(loggedIn => {
        if (!loggedIn) {
          if (data.params[0].toLowerCase() === 'self') {
            this._redirectToLogin(data.canonicalPath);
          } else {
            // TODO: encode user or use _generateUrl.
            this._redirect('/q/owner:' + data.params[0]);
          }
        } else {
          this._setParams({
            view: Gerrit.Nav.View.DASHBOARD,
            user: data.params[0],
          });
        }
      });
    },

    _handleGroupInfoRoute(data) {
      this._redirect('/admin/groups/' + encodeURIComponent(data.params[0]));
    },

    _handleGroupAuditLogRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-group-audit-log',
        detailType: 'audit-log',
        groupId: data.params[0],
      });
    },

    _handleGroupMembersRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-group-members',
        detailType: 'members',
        groupId: data.params[0],
      });
    },

    _handleGroupListOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-group-list',
        offset: data.params[1] || 0,
        filter: null,
      });
    },

    _handleGroupListFilterOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-group-list',
        offset: data.params.offset,
        filter: data.params.filter,
      });
    },

    _handleGroupListFilterRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-group-list',
        filter: data.params.filter || null,
      });
    },

    _handleGroupRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-group',
        groupId: data.params[0],
      });
    },

    _handleProjectCommandsRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-commands',
        detailType: 'commands',
        project: data.params[0],
      });
    },

    _handleBranchListOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'branches',
        project: data.params[0],
        offset: data.params[2] || 0,
        filter: null,
      });
    },

    _handleBranchListFilterOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'branches',
        project: data.params.project,
        offset: data.params.offset,
        filter: data.params.filter,
      });
    },

    _handleBranchListFilterRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'branches',
        project: data.params.project,
        filter: data.params.filter || null,
      });
    },

    _handleTagListOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'tags',
        project: data.params[0],
        offset: data.params[2] || 0,
        filter: null,
      });
    },

    _handleTagListFilterOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'tags',
        project: data.params.project,
        offset: data.params.offset,
        filter: data.params.filter,
      });
    },

    _handleTagListFilterRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'tags',
        project: data.params.project,
        filter: data.params.filter || null,
      });
    },

    _handleProjectListOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-project-list',
        offset: data.params[1] || 0,
        filter: null,
      });
    },

    _handleProjectListFilterOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-project-list',
        offset: data.params.offset,
        filter: data.params.filter,
      });
    },

    _handleProjectListFilterRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-project-list',
        filter: data.params.filter || null,
      });
    },

    _handleProjectRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        project: data.params[0],
        adminView: 'gr-project',
      });
    },

    _handlePluginListOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-plugin-list',
        offset: data.params[1] || 0,
        filter: null,
      });
    },

    _handlePluginListFilterOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-plugin-list',
        offset: data.params.offset,
        filter: data.params.filter,
      });
    },

    _handlePluginListFilterRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-plugin-list',
        filter: data.params.filter || null,
      });
    },

    _handlePluginListRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-plugin-list',
      });
    },

    _handleAdminPlaceholderRoute(data) {
      data.params.view = Gerrit.Nav.View.ADMIN;
      data.params.placeholder = true;
      this._setParams(data.params);
    },

    _handleQueryRoute(data) {
      data.params.view = Gerrit.Nav.View.SEARCH;
      this._setParams(data.params);
    },

    _handleChangeNumberLegacyRoute(ctx) {
      this._redirect('/c/' + encodeURIComponent(ctx.params[0]));
    },

    _handleChangeOrDiffRoute(ctx) {
      const isDiffView = ctx.params[8];

      // Parameter order is based on the regex group number matched.
      const params = {
        project: ctx.params[0],
        changeNum: ctx.params[1],
        basePatchNum: ctx.params[4],
        patchNum: ctx.params[6],
        path: ctx.params[8],
        view: isDiffView ? Gerrit.Nav.View.DIFF : Gerrit.Nav.View.CHANGE,
      };

      if (isDiffView) {
        const address = this._parseLineAddress(ctx.hash);
        if (address) {
          params.leftSide = address.leftSide;
          params.lineNum = address.lineNum;
        }
      }

      const needsRedirect = this._normalizePatchRangeParams(params);
      if (needsRedirect) {
        this._redirect(this._generateUrl(params));
      } else {
        this._setParams(params);
        this._restAPI.setInProjectLookup(params.changeNum, params.project);
      }
    },

    _handleChnageLegacyRoute(ctx) {
      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[3],
        patchNum: ctx.params[5],
        view: Gerrit.Nav.View.CHANGE,
      };

      this._normalizeLegacyRouteParams(params);
    },

    _handleDiffLegacyRoute(ctx) {
      // Check if path has an '@' which indicates it was using GWT style line
      // numbers. Even if the filename had an '@' in it, it would have already
      // been URI encoded. Redirect to hash version of path.
      if (ctx.path.includes('@')) {
        this._redirect(ctx.path.replace('@', '#'));
        return;
      }

      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[2],
        patchNum: ctx.params[4],
        path: ctx.params[5],
        view: Gerrit.Nav.View.DIFF,
      };

      const address = this._parseLineAddress(ctx.hash);
      if (address) {
        params.leftSide = address.leftSide;
        params.lineNum = address.lineNum;
      }

      this._normalizeLegacyRouteParams(params);
    },

    _handleAgreementsRoute(data) {
      data.params.view = Gerrit.Nav.View.AGREEMENTS;
      this._setParams(data.params);
    },

    _handleSettingsLegacyRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.SETTINGS,
        emailToken: data.params[0],
      });
    },

    _handleSettingsRoute(data) {
      this._setParams({view: Gerrit.Nav.View.SETTINGS});
    },

    _handleRegisterRoute(ctx) {
      this._setParams({justRegistered: true});
      const path = ctx.params[0] || '/';
      if (path[0] !== '/') { return; }
      this._redirect(this.getBaseUrl() + path);
    },
  });
})();
