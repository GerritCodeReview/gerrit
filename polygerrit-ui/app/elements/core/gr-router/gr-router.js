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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import page from 'page/page.mjs';
import {htmlTemplate} from './gr-router_html.js';
import {encodeURL, getBaseUrl} from '../../../utils/url-util.js';
import {GerritNav} from '../gr-navigation/gr-navigation.js';
import {appContext} from '../../../services/app-context.js';
import {patchNumEquals} from '../../../utils/patch-set-util.js';

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

  // Redirects /groups/self to /settings/#Groups for GWT compatibility
  GROUP_SELF: /^\/groups\/self/,

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

  PROJECT_OLD: /^\/admin\/(projects)\/?(.+)?$/,

  // Matches /admin/repos/<repo>
  REPO: /^\/admin\/repos\/([^,]+)$/,

  // Matches /admin/repos/<repo>,commands.
  REPO_COMMANDS: /^\/admin\/repos\/(.+),commands$/,

  // Matches /admin/repos/<repos>,access.
  REPO_ACCESS: /^\/admin\/repos\/(.+),access$/,

  // Matches /admin/repos/<repos>,access.
  REPO_DASHBOARDS: /^\/admin\/repos\/(.+),dashboards$/,

  // Matches /admin/repos[,<offset>][/].
  REPO_LIST_OFFSET: /^\/admin\/repos(,(\d+))?(\/)?$/,
  REPO_LIST_FILTER: '/admin/repos/q/filter::filter',
  REPO_LIST_FILTER_OFFSET: '/admin/repos/q/filter::filter,:offset',

  // Matches /admin/repos/<repo>,branches[,<offset>].
  BRANCH_LIST_OFFSET: /^\/admin\/repos\/(.+),branches(,(.+))?$/,
  BRANCH_LIST_FILTER: '/admin/repos/:repo,branches/q/filter::filter',
  BRANCH_LIST_FILTER_OFFSET:
      '/admin/repos/:repo,branches/q/filter::filter,:offset',

  // Matches /admin/repos/<repo>,tags[,<offset>].
  TAG_LIST_OFFSET: /^\/admin\/repos\/(.+),tags(,(.+))?$/,
  TAG_LIST_FILTER: '/admin/repos/:repo,tags/q/filter::filter',
  TAG_LIST_FILTER_OFFSET:
      '/admin/repos/:repo,tags/q/filter::filter,:offset',

  PLUGINS: /^\/plugins\/(.+)$/,

  PLUGIN_LIST: /^\/admin\/plugins(\/)?$/,

  // Matches /admin/plugins[,<offset>][/].
  PLUGIN_LIST_OFFSET: /^\/admin\/plugins(,(\d+))?(\/)?$/,
  PLUGIN_LIST_FILTER: '/admin/plugins/q/filter::filter',
  PLUGIN_LIST_FILTER_OFFSET: '/admin/plugins/q/filter::filter,:offset',

  QUERY: /^\/q\/([^,]+)(,(\d+))?$/,

  /**
   * Support vestigial params from GWT UI.
   *
   * @see Issue 7673.
   * @type {!RegExp}
   */
  QUERY_LEGACY_SUFFIX: /^\/q\/.+,n,z$/,

  // Matches /c/<changeNum>/[<basePatchNum>..][<patchNum>][/].
  CHANGE_LEGACY: /^\/c\/(\d+)\/?(((-?\d+|edit)(\.\.(\d+|edit))?))?\/?$/,
  CHANGE_NUMBER_LEGACY: /^\/(\d+)\/?/,

  // Matches
  // /c/<project>/+/<changeNum>/[<basePatchNum|edit>..][<patchNum|edit>].
  // TODO(kaspern): Migrate completely to project based URLs, with backwards
  // compatibility for change-only.
  CHANGE: /^\/c\/(.+)\/\+\/(\d+)(\/?((-?\d+|edit)(\.\.(\d+|edit))?))?\/?$/,

  // Matches /c/<project>/+/<changeNum>/[<patchNum|edit>],edit
  CHANGE_EDIT: /^\/c\/(.+)\/\+\/(\d+)(\/(\d+))?,edit\/?$/,

  // TODO(dhruvsri): look into removing patchNum and path from this url
  // Matches /c/<project>/+/<changeNum>/comment/<commentId>/<patchNum>/<path>/
  // Navigates to the diff view
  // This route is needed to resolve to patchNum vs latestPatchNum used in the
  // links generated in the emails.
  COMMENT: /^\/c\/(.+)\/\+\/(\d+)\/comment\/(\w+)\/?$/,

  // Matches
  // /c/<project>/+/<changeNum>/[<basePatchNum|edit>..]<patchNum|edit>/<path>.
  // TODO(kaspern): Migrate completely to project based URLs, with backwards
  // compatibility for change-only.
  // eslint-disable-next-line max-len
  DIFF: /^\/c\/(.+)\/\+\/(\d+)(\/((-?\d+|edit)(\.\.(\d+|edit))?(\/(.+))))\/?$/,

  // Matches /c/<project>/+/<changeNum>/[<patchNum|edit>]/<path>,edit[#lineNum]
  DIFF_EDIT: /^\/c\/(.+)\/\+\/(\d+)\/(\d+|edit)\/(.+),edit(#\d+)?$/,

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
  IMPROPERLY_ENCODED_PLUS: /^\/c\/(.+)\/ \/(.+)$/,

  PLUGIN_SCREEN: /^\/x\/([\w-]+)\/([\w-]+)\/?/,

  DOCUMENTATION_SEARCH_FILTER: '/Documentation/q/filter::filter',
  DOCUMENTATION_SEARCH: /^\/Documentation\/q\/(.*)$/,
  DOCUMENTATION: /^\/Documentation(\/)?(.+)?/,
};

export const _testOnly_RoutePattern = RoutePattern;

/**
 * Pattern to recognize and parse the diff line locations as they appear in
 * the hash of diff URLs. In this format, a number on its own indicates that
 * line number in the revision of the diff. A number prefixed by either an 'a'
 * or a 'b' indicates that line number of the base of the diff.
 *
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

const REPO_TOKEN_PATTERN = /\${(project|repo)}/g;

// Polymer makes `app` intrinsically defined on the window by virtue of the
// custom element having the id "app", but it is made explicit here.
// If you move this code to other place, please update comment about
// gr-router and gr-app in the PolyGerritIndexHtml.soy file if needed
const app = document.querySelector('#app');
if (!app) {
  console.info('No gr-app found (running tests)');
}

// Setup listeners outside of the router component initialization.
(function() {
  window.addEventListener('WebComponentsReady', () => {
    appContext.reportingService.timeEnd('WebComponentsReady');
  });
})();

/**
 * @extends PolymerElement
 */
class GrRouter extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-router'; }

  static get properties() {
    return {
      _app: {
        type: Object,
        value: app,
      },
      _isRedirecting: Boolean,
      // This variable is to differentiate between internal navigation (false)
      // and for first navigation in app after loaded from server (true).
      _isInitialLoad: {
        type: Boolean,
        value: true,
      },
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  start() {
    if (!this._app) { return; }
    this._startRouter();
  }

  _setParams(params) {
    this._appElement().params = params;
  }

  _appElement() {
    // In Polymer2 you have to reach through the shadow root of the app
    // element. This obviously breaks encapsulation.
    // TODO(brohlfs): Make this more elegant, e.g. by exposing app-element
    // explicitly in app, or by delegating to it.
    return document.getElementById('app-element') ||
        document.getElementById('app').shadowRoot.getElementById(
            'app-element');
  }

  _redirect(url) {
    this._isRedirecting = true;
    page.redirect(url);
  }

  /**
   * @param {!Object} params
   * @return {string}
   */
  _generateUrl(params) {
    const base = getBaseUrl();
    let url = '';
    const Views = GerritNav.View;

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
    } else if (params.view === Views.REPO) {
      url = this._generateRepoUrl(params);
    } else if (params.view === Views.ROOT) {
      url = '/';
    } else if (params.view === Views.SETTINGS) {
      url = this._generateSettingsUrl(params);
    } else {
      throw new Error('Can\'t generate');
    }

    return base + url;
  }

  _generateWeblinks(params) {
    const type = params.type;
    switch (type) {
      case GerritNav.WeblinkType.FILE:
        return this._getFileWebLinks(params);
      case GerritNav.WeblinkType.CHANGE:
        return this._getChangeWeblinks(params);
      case GerritNav.WeblinkType.PATCHSET:
        return this._getPatchSetWeblink(params);
      default:
        console.warn(`Unsupported weblink ${type}!`);
    }
  }

  _getPatchSetWeblink(params) {
    const {commit, options} = params;
    const {weblinks, config} = options || {};
    const name = commit && commit.slice(0, 7);
    const weblink = this._getBrowseCommitWeblink(weblinks, config);
    if (!weblink || !weblink.url) {
      return {name};
    } else {
      return {name, url: weblink.url};
    }
  }

  _firstCodeBrowserWeblink(weblinks) {
    // This is an ordered allowed list of web link types that provide direct
    // links to the commit in the url property.
    const codeBrowserLinks = ['gitiles', 'browse', 'gitweb'];
    for (let i = 0; i < codeBrowserLinks.length; i++) {
      const weblink =
        weblinks.find(weblink => weblink.name === codeBrowserLinks[i]);
      if (weblink) { return weblink; }
    }
    return null;
  }

  _getBrowseCommitWeblink(weblinks, config) {
    if (!weblinks) { return null; }
    let weblink;
    // Use primary weblink if configured and exists.
    if (config && config.gerrit && config.gerrit.primary_weblink_name) {
      weblink = weblinks.find(
          weblink => weblink.name === config.gerrit.primary_weblink_name
      );
    }
    if (!weblink) {
      weblink = this._firstCodeBrowserWeblink(weblinks);
    }
    if (!weblink) { return null; }
    return weblink;
  }

  _getChangeWeblinks({repo, commit, options: {weblinks, config}}) {
    if (!weblinks || !weblinks.length) return [];
    const commitWeblink = this._getBrowseCommitWeblink(weblinks, config);
    return weblinks.filter(weblink =>
      !commitWeblink ||
      !commitWeblink.name ||
      weblink.name !== commitWeblink.name);
  }

  _getFileWebLinks({repo, commit, file, options: {weblinks}}) {
    return weblinks;
  }

  /**
   * @param {!Object} params
   * @return {string}
   */
  _generateSearchUrl(params) {
    let offsetExpr = '';
    if (params.offset && params.offset > 0) {
      offsetExpr = ',' + params.offset;
    }

    if (params.query) {
      return '/q/' + encodeURL(params.query, true) + offsetExpr;
    }

    const operators = [];
    if (params.owner) {
      operators.push('owner:' + encodeURL(params.owner, false));
    }
    if (params.project) {
      operators.push('project:' + encodeURL(params.project, false));
    }
    if (params.branch) {
      operators.push('branch:' + encodeURL(params.branch, false));
    }
    if (params.topic) {
      operators.push('topic:"' + encodeURL(params.topic, false) + '"');
    }
    if (params.hashtag) {
      operators.push('hashtag:"' +
          encodeURL(params.hashtag.toLowerCase(), false) + '"');
    }
    if (params.statuses) {
      if (params.statuses.length === 1) {
        operators.push(
            'status:' + encodeURL(params.statuses[0], false));
      } else if (params.statuses.length > 1) {
        operators.push(
            '(' +
            params.statuses.map(s => `status:${encodeURL(s, false)}`)
                .join(' OR ') +
            ')');
      }
    }

    return '/q/' + operators.join('+') + offsetExpr;
  }

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
    } else if (params.edit) {
      suffix += ',edit';
    }
    if (params.messageHash) {
      suffix += params.messageHash;
    }
    if (params.project) {
      const encodedProject = encodeURL(params.project, true);
      return `/c/${encodedProject}/+/${params.changeNum}${suffix}`;
    } else {
      return `/c/${params.changeNum}${suffix}`;
    }
  }

  /**
   * @param {!Object} params
   * @return {string}
   */
  _generateDashboardUrl(params) {
    const repoName = params.repo || params.project || null;
    if (params.sections) {
      // Custom dashboard.
      const queryParams = this._sectionsToEncodedParams(params.sections,
          repoName);
      if (params.title) {
        queryParams.push('title=' + encodeURIComponent(params.title));
      }
      const user = params.user ? params.user : '';
      return `/dashboard/${user}?${queryParams.join('&')}`;
    } else if (repoName) {
      // Project dashboard.
      const encodedRepo = encodeURL(repoName, true);
      return `/p/${encodedRepo}/+/dashboard/${params.dashboard}`;
    } else {
      // User dashboard.
      return `/dashboard/${params.user || 'self'}`;
    }
  }

  /**
   * @param {!Array<!{name: string, query: string}>} sections
   * @param {string=} opt_repoName
   * @return {!Array<string>}
   */
  _sectionsToEncodedParams(sections, opt_repoName) {
    return sections.map(section => {
      // If there is a repo name provided, make sure to substitute it into the
      // ${repo} (or legacy ${project}) query tokens.
      const query = opt_repoName ?
        section.query.replace(REPO_TOKEN_PATTERN, opt_repoName) :
        section.query;
      return encodeURIComponent(section.name) + '=' +
          encodeURIComponent(query);
    });
  }

  /**
   * @param {!Object} params
   * @return {string}
   */
  _generateDiffOrEditUrl(params) {
    let range = this._getPatchRangeExpression(params);
    if (range.length) { range = '/' + range; }

    let suffix = `${range}/${encodeURL(params.path, true)}`;

    if (params.view === GerritNav.View.EDIT) { suffix += ',edit'; }

    if (params.lineNum) {
      suffix += '#';
      if (params.leftSide) { suffix += 'b'; }
      suffix += params.lineNum;
    }

    if (params.project) {
      const encodedProject = encodeURL(params.project, true);
      return `/c/${encodedProject}/+/${params.changeNum}${suffix}`;
    } else {
      return `/c/${params.changeNum}${suffix}`;
    }
  }

  /**
   * @param {!Object} params
   * @return {string}
   */
  _generateGroupUrl(params) {
    let url = `/admin/groups/${encodeURL(params.groupId + '', true)}`;
    if (params.detail === GerritNav.GroupDetailView.MEMBERS) {
      url += ',members';
    } else if (params.detail === GerritNav.GroupDetailView.LOG) {
      url += ',audit-log';
    }
    return url;
  }

  /**
   * @param {!Object} params
   * @return {string}
   */
  _generateRepoUrl(params) {
    let url = `/admin/repos/${encodeURL(params.repoName + '', true)}`;
    if (params.detail === GerritNav.RepoDetailView.ACCESS) {
      url += ',access';
    } else if (params.detail === GerritNav.RepoDetailView.BRANCHES) {
      url += ',branches';
    } else if (params.detail === GerritNav.RepoDetailView.TAGS) {
      url += ',tags';
    } else if (params.detail === GerritNav.RepoDetailView.COMMANDS) {
      url += ',commands';
    } else if (params.detail === GerritNav.RepoDetailView.DASHBOARDS) {
      url += ',dashboards';
    }
    return url;
  }

  /**
   * @param {!Object} params
   * @return {string}
   */
  _generateSettingsUrl(params) {
    return '/settings';
  }

  /**
   * Given an object of parameters, potentially including a `patchNum` or a
   * `basePatchNum` or both, return a string representation of that range. If
   * no range is indicated in the params, the empty string is returned.
   *
   * @param {!Object} params
   * @return {string}
   */
  _getPatchRangeExpression(params) {
    let range = '';
    if (params.patchNum) { range = '' + params.patchNum; }
    if (params.basePatchNum) { range = params.basePatchNum + '..' + range; }
    return range;
  }

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
          // Show a 404 and terminate if the lookup request failed. Attempting
          // to redirect after failing to get the project loops infinitely.
          if (!project) {
            this._show404();
            return;
          }

          params.project = project;
          this._normalizePatchRangeParams(params);
          this._redirect(this._generateUrl(params));
        });
  }

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
        patchNumEquals(params.basePatchNum, params.patchNum)) {
      needsRedirect = true;
      params.basePatchNum = null;
    } else if (hasBasePatchNum && !hasPatchNum) {
      // Regexes set basePatchNum instead of patchNum when only one is
      // specified. Redirect is not needed in this case.
      params.patchNum = params.basePatchNum;
      params.basePatchNum = null;
    }
    return needsRedirect;
  }

  /**
   * Redirect the user to login using the given return-URL for redirection
   * after authentication success.
   *
   * @param {string} returnUrl
   */
  _redirectToLogin(returnUrl) {
    const basePath = getBaseUrl() || '';
    page(
        '/login/' + encodeURIComponent(returnUrl.substring(basePath.length)));
  }

  /**
   * Hashes parsed by page.js exclude "inner" hashes, so a URL like "/a#b#c"
   * is parsed to have a hash of "b" rather than "b#c". Instead, this method
   * parses hashes correctly. Will return an empty string if there is no hash.
   *
   * @param {!string} canonicalPath
   * @return {!string} Everything after the first '#' ("a#b#c" -> "b#c").
   */
  _getHashFromCanonicalPath(canonicalPath) {
    return canonicalPath.split('#').slice(1)
        .join('#');
  }

  _parseLineAddress(hash) {
    const match = hash.match(LINE_ADDRESS_PATTERN);
    if (!match) { return null; }
    return {
      leftSide: !!match[1],
      lineNum: parseInt(match[2], 10),
    };
  }

  /**
   * Check to see if the user is logged in and return a promise that only
   * resolves if the user is logged in. If the user us not logged in, the
   * promise is rejected and the page is redirected to the login flow.
   *
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
        return Promise.reject(new Error());
      }
    });
  }

  /**  Page.js middleware that warms the REST API's logged-in cache line. */
  _loadUserMiddleware(ctx, next) {
    this.$.restAPI.getLoggedIn().then(() => { next(); });
  }

  /**  Page.js middleware that try parse the querystring into queryMap. */
  _queryStringMiddleware(ctx, next) {
    let queryMap = new Map();
    if (ctx.querystring) {
      // https://caniuse.com/#search=URLSearchParams
      if (window.URLSearchParams) {
        queryMap = new URLSearchParams(ctx.querystring);
      } else {
        queryMap = new Map(this._parseQueryString(ctx.querystring));
      }
    }
    ctx.queryMap = queryMap;
    next();
  }

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
    page(pattern,
        (ctx, next) => this._loadUserMiddleware(ctx, next),
        (ctx, next) => this._queryStringMiddleware(ctx, next),
        data => {
          this.reporting.locationChanged(handlerName);
          const promise = opt_authRedirect ?
            this._redirectIfNotLoggedIn(data) : Promise.resolve();
          promise.then(() => { this[handlerName](data); });
        });
  }

  _startRouter() {
    const base = getBaseUrl();
    if (base) {
      page.base(base);
    }

    GerritNav.setup(
        (url, opt_redirect) => {
          if (opt_redirect) {
            page.redirect(url);
          } else {
            page.show(url);
          }
        },
        this._generateUrl.bind(this),
        params => this._generateWeblinks(params),
        x => x
    );

    page.exit('*', (ctx, next) => {
      if (!this._isRedirecting) {
        this.reporting.beforeLocationChanged();
      }
      this._isRedirecting = false;
      this._isInitialLoad = false;
      next();
    });

    // Middleware
    page((ctx, next) => {
      document.body.scrollTop = 0;

      if (ctx.hash.match(RoutePattern.PLUGIN_SCREEN)) {
        // Redirect all urls using hash #/x/plugin/screen to /x/plugin/screen
        // This is needed to allow plugins to add basic #/x/ screen links to
        // any location.
        this._redirect(ctx.hash);
        return;
      }

      // Fire asynchronously so that the URL is changed by the time the event
      // is processed.
      this.async(() => {
        this.dispatchEvent(new CustomEvent('location-change', {
          detail: {
            hash: window.location.hash,
            pathname: window.location.pathname,
          },
          composed: true, bubbles: true,
        }));
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

    this._mapRoute(RoutePattern.GROUP_SELF, '_handleGroupSelfRedirectRoute',
        true);

    this._mapRoute(RoutePattern.GROUP, '_handleGroupRoute', true);

    this._mapRoute(RoutePattern.PROJECT_OLD,
        '_handleProjectsOldRoute');

    this._mapRoute(RoutePattern.REPO_COMMANDS,
        '_handleRepoCommandsRoute', true);

    this._mapRoute(RoutePattern.REPO_ACCESS,
        '_handleRepoAccessRoute');

    this._mapRoute(RoutePattern.REPO_DASHBOARDS,
        '_handleRepoDashboardsRoute');

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

    this._mapRoute(RoutePattern.REPO_LIST_OFFSET,
        '_handleRepoListOffsetRoute');

    this._mapRoute(RoutePattern.REPO_LIST_FILTER_OFFSET,
        '_handleRepoListFilterOffsetRoute');

    this._mapRoute(RoutePattern.REPO_LIST_FILTER,
        '_handleRepoListFilterRoute');

    this._mapRoute(RoutePattern.REPO, '_handleRepoRoute');

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

    this._mapRoute(RoutePattern.CHANGE_EDIT, '_handleChangeEditRoute', true);

    this._mapRoute(RoutePattern.COMMENT, '_handleCommentRoute');

    this._mapRoute(RoutePattern.DIFF, '_handleDiffRoute');

    this._mapRoute(RoutePattern.CHANGE, '_handleChangeRoute');

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

    this._mapRoute(RoutePattern.PLUGIN_SCREEN, '_handlePluginScreen');

    this._mapRoute(RoutePattern.DOCUMENTATION_SEARCH_FILTER,
        '_handleDocumentationSearchRoute');

    // redirects /Documentation/q/* to /Documentation/q/filter:*
    this._mapRoute(RoutePattern.DOCUMENTATION_SEARCH,
        '_handleDocumentationSearchRedirectRoute');

    // Makes sure /Documentation/* links work (doin't return 404)
    this._mapRoute(RoutePattern.DOCUMENTATION,
        '_handleDocumentationRedirectRoute');

    // Note: this route should appear last so it only catches URLs unmatched
    // by other patterns.
    this._mapRoute(RoutePattern.DEFAULT, '_handleDefaultRoute');

    page.start();
  }

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
      const base = getBaseUrl();
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
        this._redirect('/q/status:open+-is:wip');
      }
    });
  }

  /**
   * Decode an application/x-www-form-urlencoded string.
   *
   * @param {string} qs The application/x-www-form-urlencoded string.
   * @return {string} The decoded string.
   */
  _decodeQueryString(qs) {
    return decodeURIComponent(qs.replace(PLUS_PATTERN, ' '));
  }

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
  }

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
          view: GerritNav.View.DASHBOARD,
          user: data.params[0],
        });
      }
    });
  }

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
    // Dashboards support a foreach param which adds a base query to any
    // additional query.
    const forEachParam = queryParams.find(
        elem => elem[0].toLowerCase() === 'foreach');
    let forEachQuery = null;
    if (forEachParam) {
      forEachQuery = forEachParam[1];
    }
    const sectionParams = queryParams.filter(
        elem => elem[0] && elem[1] && elem[0].toLowerCase() !== 'title' &&
        elem[0].toLowerCase() !== 'foreach');
    const sections = sectionParams.map(elem => {
      const query = forEachQuery ? `${forEachQuery} ${elem[1]}` : elem[1];
      return {
        name: elem[0],
        query,
      };
    });

    if (sections.length > 0) {
      // Custom dashboard view.
      this._setParams({
        view: GerritNav.View.DASHBOARD,
        user: 'self',
        sections,
        title,
      });
      return Promise.resolve();
    }

    // Redirect /dashboard/ -> /dashboard/self.
    this._redirect('/dashboard/self');
    return Promise.resolve();
  }

  _handleProjectDashboardRoute(data) {
    const project = data.params[0];
    this._setParams({
      view: GerritNav.View.DASHBOARD,
      project,
      dashboard: decodeURIComponent(data.params[1]),
    });
    this.reporting.setRepoName(project);
  }

  _handleGroupInfoRoute(data) {
    this._redirect('/admin/groups/' + encodeURIComponent(data.params[0]));
  }

  _handleGroupSelfRedirectRoute(data) {
    this._redirect('/settings/#Groups');
  }

  _handleGroupRoute(data) {
    this._setParams({
      view: GerritNav.View.GROUP,
      groupId: data.params[0],
    });
  }

  _handleGroupAuditLogRoute(data) {
    this._setParams({
      view: GerritNav.View.GROUP,
      detail: GerritNav.GroupDetailView.LOG,
      groupId: data.params[0],
    });
  }

  _handleGroupMembersRoute(data) {
    this._setParams({
      view: GerritNav.View.GROUP,
      detail: GerritNav.GroupDetailView.MEMBERS,
      groupId: data.params[0],
    });
  }

  _handleGroupListOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-admin-group-list',
      offset: data.params[1] || 0,
      filter: null,
      openCreateModal: data.hash === 'create',
    });
  }

  _handleGroupListFilterOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-admin-group-list',
      offset: data.params.offset,
      filter: data.params.filter,
    });
  }

  _handleGroupListFilterRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-admin-group-list',
      filter: data.params.filter || null,
    });
  }

  _handleProjectsOldRoute(data) {
    let params = '';
    if (data.params[1]) {
      params = encodeURIComponent(data.params[1]);
      if (data.params[1].includes(',')) {
        params =
            encodeURIComponent(data.params[1]).replace('%2C', ',');
      }
    }

    this._redirect(`/admin/repos/${params}`);
  }

  _handleRepoCommandsRoute(data) {
    const repo = data.params[0];
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.COMMANDS,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  _handleRepoAccessRoute(data) {
    const repo = data.params[0];
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.ACCESS,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  _handleRepoDashboardsRoute(data) {
    const repo = data.params[0];
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.DASHBOARDS,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  _handleBranchListOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.BRANCHES,
      repo: data.params[0],
      offset: data.params[2] || 0,
      filter: null,
    });
  }

  _handleBranchListFilterOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.BRANCHES,
      repo: data.params.repo,
      offset: data.params.offset,
      filter: data.params.filter,
    });
  }

  _handleBranchListFilterRoute(data) {
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.BRANCHES,
      repo: data.params.repo,
      filter: data.params.filter || null,
    });
  }

  _handleTagListOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.TAGS,
      repo: data.params[0],
      offset: data.params[2] || 0,
      filter: null,
    });
  }

  _handleTagListFilterOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.TAGS,
      repo: data.params.repo,
      offset: data.params.offset,
      filter: data.params.filter,
    });
  }

  _handleTagListFilterRoute(data) {
    this._setParams({
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.TAGS,
      repo: data.params.repo,
      filter: data.params.filter || null,
    });
  }

  _handleRepoListOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-repo-list',
      offset: data.params[1] || 0,
      filter: null,
      openCreateModal: data.hash === 'create',
    });
  }

  _handleRepoListFilterOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-repo-list',
      offset: data.params.offset,
      filter: data.params.filter,
    });
  }

  _handleRepoListFilterRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-repo-list',
      filter: data.params.filter || null,
    });
  }

  _handleCreateProjectRoute(data) {
    // Redirects the legacy route to the new route, which displays the project
    // list with a hash 'create'.
    this._redirect('/admin/repos#create');
  }

  _handleCreateGroupRoute(data) {
    // Redirects the legacy route to the new route, which displays the group
    // list with a hash 'create'.
    this._redirect('/admin/groups#create');
  }

  _handleRepoRoute(data) {
    const repo = data.params[0];
    this._setParams({
      view: GerritNav.View.REPO,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  _handlePluginListOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-plugin-list',
      offset: data.params[1] || 0,
      filter: null,
    });
  }

  _handlePluginListFilterOffsetRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-plugin-list',
      offset: data.params.offset,
      filter: data.params.filter,
    });
  }

  _handlePluginListFilterRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-plugin-list',
      filter: data.params.filter || null,
    });
  }

  _handlePluginListRoute(data) {
    this._setParams({
      view: GerritNav.View.ADMIN,
      adminView: 'gr-plugin-list',
    });
  }

  _handleQueryRoute(data) {
    this._setParams({
      view: GerritNav.View.SEARCH,
      query: data.params[0],
      offset: data.params[2],
    });
  }

  _handleQueryLegacySuffixRoute(ctx) {
    this._redirect(ctx.path.replace(LEGACY_QUERY_SUFFIX_PATTERN, ''));
  }

  _handleChangeNumberLegacyRoute(ctx) {
    this._redirect('/c/' + encodeURIComponent(ctx.params[0]));
  }

  _handleChangeRoute(ctx) {
    // Parameter order is based on the regex group number matched.
    const params = {
      project: ctx.params[0],
      changeNum: ctx.params[1],
      basePatchNum: ctx.params[4],
      patchNum: ctx.params[6],
      view: GerritNav.View.CHANGE,
      queryMap: ctx.queryMap,
    };

    this.reporting.setRepoName(params.project);
    this._redirectOrNavigate(params);
  }

  _handleCommentRoute(ctx) {
    const params = {
      project: ctx.params[0],
      changeNum: ctx.params[1],
      commentId: ctx.params[2],
      view: GerritNav.View.DIFF,
      commentLink: true,
    };
    this.reporting.setRepoName(params.project);
    this._redirectOrNavigate(params);
  }

  _handleDiffRoute(ctx) {
    // Parameter order is based on the regex group number matched.
    const params = {
      project: ctx.params[0],
      changeNum: ctx.params[1],
      basePatchNum: ctx.params[4],
      patchNum: ctx.params[6],
      path: ctx.params[8],
      view: GerritNav.View.DIFF,
    };

    const address = this._parseLineAddress(ctx.hash);
    if (address) {
      params.leftSide = address.leftSide;
      params.lineNum = address.lineNum;
    }
    this.reporting.setRepoName(params.project);
    this._redirectOrNavigate(params);
  }

  _handleChangeLegacyRoute(ctx) {
    // Parameter order is based on the regex group number matched.
    const params = {
      changeNum: ctx.params[0],
      basePatchNum: ctx.params[3],
      patchNum: ctx.params[5],
      view: GerritNav.View.CHANGE,
      querystring: ctx.querystring,
    };

    this._normalizeLegacyRouteParams(params);
  }

  _handleLegacyLinenum(ctx) {
    this._redirect(ctx.path.replace(LEGACY_LINENUM_PATTERN, '#$1'));
  }

  _handleDiffLegacyRoute(ctx) {
    // Parameter order is based on the regex group number matched.
    const params = {
      changeNum: ctx.params[0],
      basePatchNum: ctx.params[2],
      patchNum: ctx.params[4],
      path: ctx.params[5],
      view: GerritNav.View.DIFF,
    };

    const address = this._parseLineAddress(ctx.hash);
    if (address) {
      params.leftSide = address.leftSide;
      params.lineNum = address.lineNum;
    }

    this._normalizeLegacyRouteParams(params);
  }

  _handleDiffEditRoute(ctx) {
    // Parameter order is based on the regex group number matched.
    const project = ctx.params[0];
    this._redirectOrNavigate({
      project,
      changeNum: ctx.params[1],
      patchNum: ctx.params[2],
      path: ctx.params[3],
      lineNum: ctx.hash,
      view: GerritNav.View.EDIT,
    });
    this.reporting.setRepoName(project);
  }

  _handleChangeEditRoute(ctx) {
    // Parameter order is based on the regex group number matched.
    const project = ctx.params[0];
    this._redirectOrNavigate({
      project,
      changeNum: ctx.params[1],
      patchNum: ctx.params[3],
      view: GerritNav.View.CHANGE,
      edit: true,
    });
    this.reporting.setRepoName(project);
  }

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
    }
  }

  _handleAgreementsRoute() {
    this._redirect('/settings/#Agreements');
  }

  _handleNewAgreementsRoute(data) {
    data.params.view = GerritNav.View.AGREEMENTS;
    this._setParams(data.params);
  }

  _handleSettingsLegacyRoute(data) {
    // email tokens may contain '+' but no space.
    // The parameter parsing replaces all '+' with a space,
    // undo that to have valid tokens.
    const token = data.params[0].replace(/ /g, '+');
    this._setParams({
      view: GerritNav.View.SETTINGS,
      emailToken: token,
    });
  }

  _handleSettingsRoute(data) {
    this._setParams({view: GerritNav.View.SETTINGS});
  }

  _handleRegisterRoute(ctx) {
    this._setParams({justRegistered: true});
    let path = ctx.params[0] || '/';

    // Prevent redirect looping.
    if (path.startsWith('/register')) { path = '/'; }

    if (path[0] !== '/') { return; }
    this._redirect(getBaseUrl() + path);
  }

  /**
   * Handler for routes that should pass through the router and not be caught
   * by the catchall _handleDefaultRoute handler.
   */
  _handlePassThroughRoute() {
    location.reload();
  }

  /**
   * URL may sometimes have /+/ encoded to / /.
   * Context: Issue 6888, Issue 7100
   */
  _handleImproperlyEncodedPlusRoute(ctx) {
    let hash = this._getHashFromCanonicalPath(ctx.canonicalPath);
    if (hash.length) { hash = '#' + hash; }
    this._redirect(`/c/${ctx.params[0]}/+/${ctx.params[1]}${hash}`);
  }

  _handlePluginScreen(ctx) {
    const view = GerritNav.View.PLUGIN_SCREEN;
    const plugin = ctx.params[0];
    const screen = ctx.params[1];
    this._setParams({view, plugin, screen});
  }

  _handleDocumentationSearchRoute(data) {
    this._setParams({
      view: GerritNav.View.DOCUMENTATION_SEARCH,
      filter: data.params.filter || null,
    });
  }

  _handleDocumentationSearchRedirectRoute(data) {
    this._redirect('/Documentation/q/filter:' +
        encodeURIComponent(data.params[0]));
  }

  _handleDocumentationRedirectRoute(data) {
    if (data.params[1]) {
      location.reload();
    } else {
      // Redirect /Documentation to /Documentation/index.html
      this._redirect('/Documentation/index.html');
    }
  }

  /**
   * Catchall route for when no other route is matched.
   */
  _handleDefaultRoute() {
    if (this._isInitialLoad) {
      // Server recognized this route as polygerrit, so we show 404.
      this._show404();
    } else {
      // Route can be recognized by server, so we pass it to server.
      this._handlePassThroughRoute();
    }
  }

  _show404() {
    // Note: the app's 404 display is tightly-coupled with catching 404
    // network responses, so we simulate a 404 response status to display it.
    // TODO: Decouple the gr-app error view from network responses.
    this._appElement().dispatchEvent(new CustomEvent('page-error',
        {detail: {response: {status: 404}}}));
  }
}

customElements.define(GrRouter.is, GrRouter);
