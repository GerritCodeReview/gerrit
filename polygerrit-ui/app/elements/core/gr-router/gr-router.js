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

    DASHBOARD: /^\/dashboard\/(.+)$/,
    CUSTOM_DASHBOARD: /^\/dashboard\/?$/,
    PROJECT_DASHBOARD: /^\/p\/(.+)\/\+\/dashboard\/(.+)/,

    AGREEMENTS: /^\/settings\/agreements\/?/,
    NEW_AGREEMENTS: /^\/settings\/new-agreement\/?/,
    REGISTER: /^\/register(\/.*)?$/,

    // Pattern for login and logout URLs intended to be passed-through. May
    // include a return URL.
    LOG_IN_OR_OUT: /\/log(in|out)(\/(.+))?$/,

    // Pattern for a catchall route when no other pattern is matched.
    DEFAULT: /.*/,

    // Matches /admin/groups/[uuid-]<group>
    GROUP: /^\/admin\/groups\/(?:uuid-)?([^,]+)$/,

    // Matches /admin/groups/[uuid-]<group>,info (backwords compat with gwtui)
    // Redirects to /admin/groups/[uuid-]<group>
    GROUP_INFO: /^\/admin\/groups\/(?:uuid-)?(.+),info$/,

    // Matches /admin/groups/<group>,audit-log
    GROUP_AUDIT_LOG: /^\/admin\/groups\/(?:uuid-)?(.+),audit-log$/,

    // Matches /admin/groups/[uuid-]<group>,members
    GROUP_MEMBERS: /^\/admin\/groups\/(?:uuid-)?(.+),members$/,

    // Matches /admin/groups[,<offset>][/].
    GROUP_LIST_OFFSET: /^\/admin\/groups(,(\d+))?(\/)?$/,
    GROUP_LIST_FILTER: '/admin/groups/q/filter::filter',
    GROUP_LIST_FILTER_OFFSET: '/admin/groups/q/filter::filter,:offset',

    // Matches /admin/create-project
    LEGACY_CREATE_PROJECT: /^\/admin\/create-project\/?$/,

    // Matches /admin/create-project
    LEGACY_CREATE_GROUP: /^\/admin\/create-group\/?$/,

    // Matches /admin/projects/<project>
    PROJECT: /^\/admin\/projects\/([^,]+)$/,

    // Matches /admin/projects/<project>,commands.
    PROJECT_COMMANDS: /^\/admin\/projects\/(.+),commands$/,

    // Matches /admin/projects/<project>,access.
    PROJECT_ACCESS: /^\/admin\/projects\/(.+),access$/,

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

    PLUGINS: /^\/plugins\/(.+)$/,

    PLUGIN_LIST: /^\/admin\/plugins(\/)?$/,

    // Matches /admin/plugins[,<offset>][/].
    PLUGIN_LIST_OFFSET: /^\/admin\/plugins(,(\d+))?(\/)?$/,
    PLUGIN_LIST_FILTER: '/admin/plugins/q/filter::filter',
    PLUGIN_LIST_FILTER_OFFSET: '/admin/plugins/q/filter::filter,:offset',

    QUERY: /^\/q\/([^,]+)(,(\d+))?$/,

    /**
     * Support vestigial params from GWT UI.
     * @see Issue 7673.
     * @type {!RegExp}
     */
    QUERY_LEGACY_SUFFIX: /^\/q\/.+,n,z$/,

    // Matches /c/<changeNum>/[<basePatchNum>..][<patchNum>][/].
    CHANGE_LEGACY: /^\/c\/(\d+)\/?(((-?\d+|edit)(\.\.(\d+|edit))?))?\/?$/,
    CHANGE_NUMBER_LEGACY: /^\/(\d+)\/?/,

    // Matches
    // /c/<project>/+/<changeNum>/
    //     [<basePatchNum|edit>..][<patchNum|edit>]/[path].
    // TODO(kaspern): Migrate completely to project based URLs, with backwards
    // compatibility for change-only.
    // eslint-disable-next-line max-len
    CHANGE_OR_DIFF: /^\/c\/(.+)\/\+\/(\d+)(\/?((-?\d+|edit)(\.\.(\d+|edit))?(\/(.+))?))?\/?$/,

    // Matches /c/<project>/+/<changeNum>/edit/<path>,edit
    // eslint-disable-next-line max-len
    DIFF_EDIT: /^\/c\/(.+)\/\+\/(\d+)\/edit\/(.+),edit$/,

    // Matches non-project-relative
    // /c/<changeNum>/[<basePatchNum>..]<patchNum>/<path>.
    DIFF_LEGACY: /^\/c\/(\d+)\/((-?\d+|edit)(\.\.(\d+|edit))?)\/(.+)/,

    // Matches diff routes using @\d+ to specify a file name (whether or not
    // the project name is included).
    // eslint-disable-next-line max-len
    DIFF_LEGACY_LINENUM: /^\/c\/((.+)\/\+\/)?(\d+)(\/?((-?\d+|edit)(\.\.(\d+|edit))?\/(.+))?)@[ab]?\d+$/,

    SETTINGS: /^\/settings\/?/,
    SETTINGS_LEGACY: /^\/settings\/VE\/(\S+)/,

    // Matches /c/<changeNum>/ /<URL tail>
    // Catches improperly encoded URLs (context: Issue 7100)
    IMPROPERLY_ENCODED_PLUS: /^\/c\/(.+)\/\ \/(.+)$/,
  };

  /**
   * Pattern to recognize and parse the diff line locations as they appear in
   * the hash of diff URLs. In this format, a number on its own indicates that
   * line number in the revision of the diff. A number prefixed by either an 'a'
   * or a 'b' indicates that line number of the base of the diff.
   * @type {RegExp}
   */
  const LINE_ADDRESS_PATTERN = /^([ab]?)(\d+)$/;

  /**
   * Pattern to recognize '+' in url-encoded strings for replacement with ' '.
   */
  const PLUS_PATTERN = /\+/g;

  /**
   * Pattern to recognize leading '?' in window.location.search, for stripping.
   */
  const QUESTION_PATTERN = /^\?*/;

  /**
   * GWT UI would use @\d+ at the end of a path to indicate linenum.
   */
  const LEGACY_LINENUM_PATTERN = /@([ab]?\d+)$/;

  const LEGACY_QUERY_SUFFIX_PATTERN = /,n,z$/;

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
      _app: {
        type: Object,
        value: app,
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    start() {
      if (!this._app) { return; }
      this._startRouter();
    },

    _setParams(params) {
      this._app.params = params;
    },

    _redirect(url) {
      page.redirect(url);
    },

    /**
     * @param {!Object} params
     * @return {string}
     */
    _generateUrl(params) {
      const base = this.getBaseUrl();
      let url = '';
      const Views = Gerrit.Nav.View;

      if (params.view === Views.SEARCH) {
        url = this._generateSearchUrl(params);
      } else if (params.view === Views.CHANGE) {
        url = this._generateChangeUrl(params);
      } else if (params.view === Views.DASHBOARD) {
        url = this._generateDashboardUrl(params);
      } else if (params.view === Views.DIFF || params.view === Views.EDIT) {
        url = this._generateDiffOrEditUrl(params);
      } else if (params.view === Views.GROUP) {
        url = this._generateGroupUrl(params);
      } else if (params.view === Views.SETTINGS) {
        url = this._generateSettingsUrl(params);
      } else {
        throw new Error('Can\'t generate');
      }

      return base + url;
    },

    /**
     * @param {!Object} params
     * @return {string}
     */
    _generateSearchUrl(params) {
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
      return '/q/' + operators.join('+');
    },

    /**
     * @param {!Object} params
     * @return {string}
     */
    _generateChangeUrl(params) {
      let range = this._getPatchRangeExpression(params);
      if (range.length) { range = '/' + range; }
      let suffix = `${range}`;
      if (params.querystring) {
        suffix += '?' + params.querystring;
      }
      if (params.project) {
        return `/c/${params.project}/+/${params.changeNum}${suffix}`;
      } else {
        return `/c/${params.changeNum}${suffix}`;
      }
    },

    /**
     * @param {!Object} params
     * @return {string}
     */
    _generateDashboardUrl(params) {
      if (params.sections) {
        // Custom dashboard.
        const queryParams = params.sections.map(section => {
          return encodeURIComponent(section.name) + '=' +
              encodeURIComponent(section.query);
        });
        if (params.title) {
          queryParams.push('title=' + encodeURIComponent(params.title));
        }
        const user = params.user ? params.user : '';
        return `/dashboard/${user}?${queryParams.join('&')}`;
      } else if (params.project) {
        // Project dashboard.
        return `/p/${params.project}/+/dashboard/${params.dashboard}`;
      } else {
        // User dashboard.
        return `/dashboard/${params.user || 'self'}`;
      }
    },

    /**
     * @param {!Object} params
     * @return {string}
     */
    _generateDiffOrEditUrl(params) {
      let range = this._getPatchRangeExpression(params);
      if (range.length) { range = '/' + range; }

      let suffix = `${range}/${this.encodeURL(params.path, true)}`;

      if (params.view === Gerrit.Nav.View.EDIT) { suffix += ',edit'; }

      if (params.lineNum) {
        suffix += '#';
        if (params.leftSide) { suffix += 'b'; }
        suffix += params.lineNum;
      }

      if (params.project) {
        return `/c/${params.project}/+/${params.changeNum}${suffix}`;
      } else {
        return `/c/${params.changeNum}${suffix}`;
      }
    },

    /**
     * @param {!Object} params
     * @return {string}
     */
    _generateGroupUrl(params) {
      let url = `/admin/groups/${this.encodeURL(params.groupId + '', true)}`;
      if (params.detail === Gerrit.Nav.GroupDetailView.MEMBERS) {
        url += ',members';
      } else if (params.detail === Gerrit.Nav.GroupDetailView.LOG) {
        url += ',audit-log';
      }
      return url;
    },

    /**
     * @param {!Object} params
     * @return {string}
     */
    _generateSettingsUrl(params) {
      return '/settings';
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

      return this.$.restAPI.getFromProjectLookup(params.changeNum)
          .then(project => {
            // Do nothing if the lookup request failed. This avoids an infinite
            // loop of project lookups.
            if (!project) { return; }

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

      // Diffing a patch against itself is invalid, so if the base and revision
      // patches are equal clear the base.
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
      return this.$.restAPI.getLoggedIn().then(loggedIn => {
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
      this.$.restAPI.getLoggedIn().then(() => { next(); });
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

      this._mapRoute(RoutePattern.CUSTOM_DASHBOARD,
          '_handleCustomDashboardRoute');

      this._mapRoute(RoutePattern.PROJECT_DASHBOARD,
          '_handleProjectDashboardRoute');

      this._mapRoute(RoutePattern.GROUP_INFO, '_handleGroupInfoRoute', true);

      this._mapRoute(RoutePattern.GROUP_AUDIT_LOG, '_handleGroupAuditLogRoute',
          true);

      this._mapRoute(RoutePattern.GROUP_MEMBERS, '_handleGroupMembersRoute',
          true);

      this._mapRoute(RoutePattern.GROUP_LIST_OFFSET,
          '_handleGroupListOffsetRoute', true);

      this._mapRoute(RoutePattern.GROUP_LIST_FILTER_OFFSET,
          '_handleGroupListFilterOffsetRoute', true);

      this._mapRoute(RoutePattern.GROUP_LIST_FILTER,
          '_handleGroupListFilterRoute', true);

      this._mapRoute(RoutePattern.GROUP, '_handleGroupRoute', true);

      this._mapRoute(RoutePattern.PROJECT_COMMANDS,
          '_handleProjectCommandsRoute', true);

      this._mapRoute(RoutePattern.PROJECT_ACCESS,
          '_handleProjectAccessRoute');

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

      this._mapRoute(RoutePattern.LEGACY_CREATE_GROUP,
          '_handleCreateGroupRoute', true);

      this._mapRoute(RoutePattern.LEGACY_CREATE_PROJECT,
          '_handleCreateProjectRoute', true);

      this._mapRoute(RoutePattern.PROJECT_LIST_OFFSET,
          '_handleProjectListOffsetRoute');

      this._mapRoute(RoutePattern.PROJECT_LIST_FILTER_OFFSET,
          '_handleProjectListFilterOffsetRoute');

      this._mapRoute(RoutePattern.PROJECT_LIST_FILTER,
          '_handleProjectListFilterRoute');

      this._mapRoute(RoutePattern.PROJECT, '_handleProjectRoute');

      this._mapRoute(RoutePattern.PLUGINS, '_handlePassThroughRoute');

      this._mapRoute(RoutePattern.PLUGIN_LIST_OFFSET,
          '_handlePluginListOffsetRoute', true);

      this._mapRoute(RoutePattern.PLUGIN_LIST_FILTER_OFFSET,
          '_handlePluginListFilterOffsetRoute', true);

      this._mapRoute(RoutePattern.PLUGIN_LIST_FILTER,
          '_handlePluginListFilterRoute', true);

      this._mapRoute(RoutePattern.PLUGIN_LIST, '_handlePluginListRoute', true);

      this._mapRoute(RoutePattern.QUERY_LEGACY_SUFFIX,
          '_handleQueryLegacySuffixRoute');

      this._mapRoute(RoutePattern.QUERY, '_handleQueryRoute');

      this._mapRoute(RoutePattern.DIFF_LEGACY_LINENUM, '_handleLegacyLinenum');

      this._mapRoute(RoutePattern.CHANGE_NUMBER_LEGACY,
          '_handleChangeNumberLegacyRoute');

      this._mapRoute(RoutePattern.DIFF_EDIT, '_handleDiffEditRoute', true);

      this._mapRoute(RoutePattern.CHANGE_OR_DIFF, '_handleChangeOrDiffRoute');

      this._mapRoute(RoutePattern.CHANGE_LEGACY, '_handleChangeLegacyRoute');

      this._mapRoute(RoutePattern.DIFF_LEGACY, '_handleDiffLegacyRoute');

      this._mapRoute(RoutePattern.AGREEMENTS, '_handleAgreementsRoute', true);

      this._mapRoute(RoutePattern.NEW_AGREEMENTS, '_handleNewAgreementsRoute',
          true);

      this._mapRoute(RoutePattern.SETTINGS_LEGACY,
          '_handleSettingsLegacyRoute', true);

      this._mapRoute(RoutePattern.SETTINGS, '_handleSettingsRoute', true);

      this._mapRoute(RoutePattern.REGISTER, '_handleRegisterRoute');

      this._mapRoute(RoutePattern.LOG_IN_OR_OUT, '_handlePassThroughRoute');

      this._mapRoute(RoutePattern.IMPROPERLY_ENCODED_PLUS,
          '_handleImproperlyEncodedPlusRoute');

      // Note: this route should appear last so it only catches URLs unmatched
      // by other patterns.
      this._mapRoute(RoutePattern.DEFAULT, '_handleDefaultRoute');

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
      return this.$.restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          this._redirect('/dashboard/self');
        } else {
          this._redirect('/q/status:open');
        }
      });
    },

    /**
     * Decode an application/x-www-form-urlencoded string.
     *
     * @param {string} qs The application/x-www-form-urlencoded string.
     * @return {string} The decoded string.
     */
    _decodeQueryString(qs) {
      return decodeURIComponent(qs.replace(PLUS_PATTERN, ' '));
    },

    /**
     * Parse a query string (e.g. window.location.search) into an array of
     * name/value pairs.
     *
     * @param {string} qs The application/x-www-form-urlencoded query string.
     * @return {!Array<!Array<string>>} An array of name/value pairs, where each
     *     element is a 2-element array.
     */
    _parseQueryString(qs) {
      qs = qs.replace(QUESTION_PATTERN, '');
      if (!qs) {
        return [];
      }
      const params = [];
      qs.split('&').forEach(param => {
        const idx = param.indexOf('=');
        let name;
        let value;
        if (idx < 0) {
          name = this._decodeQueryString(param);
          value = '';
        } else {
          name = this._decodeQueryString(param.substring(0, idx));
          value = this._decodeQueryString(param.substring(idx + 1));
        }
        if (name) {
          params.push([name, value]);
        }
      });
      return params;
    },

    /**
     * Handle dashboard routes. These may be user, or project dashboards.
     *
     * @param {!Object} data The parsed route data.
     */
    _handleDashboardRoute(data) {
      // User dashboard. We require viewing user to be logged in, else we
      // redirect to login for self dashboard or simple owner search for
      // other user dashboard.
      return this.$.restAPI.getLoggedIn().then(loggedIn => {
        if (!loggedIn) {
          if (data.params[0].toLowerCase() === 'self') {
            this._redirectToLogin(data.canonicalPath);
          } else {
            this._redirect('/q/owner:' + encodeURIComponent(data.params[0]));
          }
        } else {
          this._setParams({
            view: Gerrit.Nav.View.DASHBOARD,
            user: data.params[0],
          });
        }
      });
    },

    /**
     * Handle custom dashboard routes.
     *
     * @param {!Object} data The parsed route data.
     * @param {string=} opt_qs Optional query string associated with the route.
     *     If not given, window.location.search is used. (Used by tests).
     */
    _handleCustomDashboardRoute(data, opt_qs) {
      // opt_qs may be provided by a test, and it may have a falsy value
      const qs = opt_qs !== undefined ? opt_qs : window.location.search;
      const queryParams = this._parseQueryString(qs);
      let title = 'Custom Dashboard';
      const titleParam = queryParams.find(
          elem => elem[0].toLowerCase() === 'title');
      if (titleParam) {
        title = titleParam[1];
      }
      const sectionParams = queryParams.filter(
          elem => elem[0] && elem[1] && elem[0].toLowerCase() !== 'title');
      const sections = sectionParams.map(elem => {
        return {
          name: elem[0],
          query: elem[1],
        };
      });

      if (sections.length > 0) {
        // Custom dashboard view.
        this._setParams({
          view: Gerrit.Nav.View.DASHBOARD,
          user: 'self',
          sections,
          title,
        });
        return Promise.resolve();
      }

      // Redirect /dashboard/ -> /dashboard/self.
      this._redirect('/dashboard/self');
      return Promise.resolve();
    },

    _handleProjectDashboardRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.DASHBOARD,
        project: data.params[0],
        dashboard: decodeURIComponent(data.params[1]),
      });
    },

    _handleGroupInfoRoute(data) {
      this._redirect('/admin/groups/' + encodeURIComponent(data.params[0]));
    },

    _handleGroupRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.GROUP,
        groupId: data.params[0],
      });
    },

    _handleGroupAuditLogRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.GROUP,
        detail: Gerrit.Nav.GroupDetailView.LOG,
        groupId: data.params[0],
      });
    },

    _handleGroupMembersRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.GROUP,
        detail: Gerrit.Nav.GroupDetailView.MEMBERS,
        groupId: data.params[0],
      });
    },

    _handleGroupListOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-group-list',
        offset: data.params[1] || 0,
        filter: null,
        openCreateModal: data.hash === 'create',
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

    _handleProjectCommandsRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-commands',
        detailType: 'commands',
        project: data.params[0],
      });
    },

    _handleProjectAccessRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-access',
        detailType: 'access',
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
        adminView: 'gr-project-list',
        offset: data.params[1] || 0,
        filter: null,
        openCreateModal: data.hash === 'create',
      });
    },

    _handleProjectListFilterOffsetRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-list',
        offset: data.params.offset,
        filter: data.params.filter,
      });
    },

    _handleProjectListFilterRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-list',
        filter: data.params.filter || null,
      });
    },

    _handleCreateProjectRoute(data) {
      // Redirects the legacy route to the new route, which displays the project
      // list with a hash 'create'.
      this._redirect('/admin/projects#create');
    },

    _handleCreateGroupRoute(data) {
      // Redirects the legacy route to the new route, which displays the group
      // list with a hash 'create'.
      this._redirect('/admin/groups#create');
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

    _handleQueryRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.SEARCH,
        query: data.params[0],
        offset: data.params[2],
      });
    },

    _handleQueryLegacySuffixRoute(ctx) {
      this._redirect(ctx.path.replace(LEGACY_QUERY_SUFFIX_PATTERN, ''));
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

      this._redirectOrNavigate(params);
    },

    _handleChangeLegacyRoute(ctx) {
      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[3],
        patchNum: ctx.params[5],
        view: Gerrit.Nav.View.CHANGE,
        querystring: ctx.querystring,
      };

      this._normalizeLegacyRouteParams(params);
    },

    _handleLegacyLinenum(ctx) {
      this._redirect(ctx.path.replace(LEGACY_LINENUM_PATTERN, '#$1'));
    },

    _handleDiffLegacyRoute(ctx) {
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

    _handleDiffEditRoute(ctx) {
      // Parameter order is based on the regex group number matched.
      this._redirectOrNavigate({
        project: ctx.params[0],
        changeNum: ctx.params[1],
        path: ctx.params[2],
        view: Gerrit.Nav.View.EDIT,
      });
    },

    /**
     * Normalize the patch range params for a the change or diff view and
     * redirect if URL upgrade is needed.
     */
    _redirectOrNavigate(params) {
      const needsRedirect = this._normalizePatchRangeParams(params);
      if (needsRedirect) {
        this._redirect(this._generateUrl(params));
      } else {
        this._setParams(params);
        this.$.restAPI.setInProjectLookup(params.changeNum,
            params.project);
      }
    },

    // TODO fix this so it properly redirects
    // to /settings#Agreements (Scrolls down)
    _handleAgreementsRoute(data) {
      this._redirect('/settings/#Agreements');
    },

    _handleNewAgreementsRoute(data) {
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
      let path = ctx.params[0] || '/';

      // Prevent redirect looping.
      if (path.startsWith('/register')) { path = '/'; }

      if (path[0] !== '/') { return; }
      this._redirect(this.getBaseUrl() + path);
    },

    /**
     * Handler for routes that should pass through the router and not be caught
     * by the catchall _handleDefaultRoute handler.
     */
    _handlePassThroughRoute() {
      location.reload();
    },


    /**
     * URL may sometimes have /+/ encoded to / /.
     * Context: Issue 6888, Issue 7100
     */
    _handleImproperlyEncodedPlusRoute(ctx) {
      let hash = this._getHashFromCanonicalPath(ctx.canonicalPath);
      if (hash.length) { hash = '#' + hash; }
      this._redirect(`/c/${ctx.params[0]}/+/${ctx.params[1]}${hash}`);
    },

    /**
     * Catchall route for when no other route is matched.
     */
    _handleDefaultRoute() {
      // Note: the app's 404 display is tightly-coupled with catching 404
      // network responses, so we simulate a 404 response status to display it.
      // TODO: Decouple the gr-app error view from network responses.
      this._app.dispatchEvent(new CustomEvent('page-error',
          {detail: {response: {status: 404}}}));
    },
  });
})();
