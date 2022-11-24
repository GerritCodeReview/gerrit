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
import {
  page,
  PageContext,
  PageNextCallback,
} from '../../../utils/page-wrapper-utils';
import {
  DashboardSection,
  GeneratedWebLink,
  GenerateUrlChangeViewParameters,
  GenerateUrlDashboardViewParameters,
  GenerateUrlDiffViewParameters,
  GenerateUrlEditViewParameters,
  GenerateUrlGroupViewParameters,
  GenerateUrlParameters,
  GenerateUrlRepoViewParameters,
  GenerateUrlSearchViewParameters,
  GenerateWebLinksChangeParameters,
  GenerateWebLinksEditParameters,
  GenerateWebLinksFileParameters,
  GenerateWebLinksParameters,
  GenerateWebLinksPatchsetParameters,
  GenerateWebLinksResolveConflictsParameters,
  GerritNav,
  GroupDetailView,
  isGenerateUrlDiffViewParameters,
  RepoDetailView,
  WeblinkType,
} from '../gr-navigation/gr-navigation';
import {getAppContext} from '../../../services/app-context';
import {convertToPatchSetNum} from '../../../utils/patch-set-util';
import {assertNever} from '../../../utils/common-util';
import {
  BasePatchSetNum,
  DashboardId,
  GroupId,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  ServerInfo,
  UrlEncodedCommentId,
  ParentPatchSetNum,
} from '../../../types/common';
import {
  AppElement,
  AppElementAgreementParam,
  AppElementParams,
} from '../../gr-app-types';
import {LocationChangeEventDetail} from '../../../types/events';
import {GerritView} from '../../../services/router/router-model';
import {firePageError} from '../../../utils/event-util';
import {addQuotesWhen} from '../../../utils/string-util';
import {windowLocationReload} from '../../../utils/dom-util';
import {
  encodeURL,
  getBaseUrl,
  toPath,
  toPathname,
  toSearchParams,
} from '../../../utils/url-util';
import {Execution, LifeCycle, Timing} from '../../../constants/reporting';

const RoutePattern = {
  ROOT: '/',

  DASHBOARD: /^\/dashboard\/(.+)$/,
  CUSTOM_DASHBOARD: /^\/dashboard\/?$/,
  PROJECT_DASHBOARD: /^\/p\/(.+)\/\+\/dashboard\/(.+)/,
  LEGACY_PROJECT_DASHBOARD: /^\/projects\/(.+),dashboards\/(.+)/,

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

  // Matches /admin/groups/[uuid-]<group>,info (backwards compat with gwtui)
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

  REPO_GENERAL: /^\/admin\/repos\/(.+),general$/,

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
  TAG_LIST_FILTER_OFFSET: '/admin/repos/:repo,tags/q/filter::filter,:offset',

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

  CHANGE_ID_QUERY: /^\/id\/(I[0-9a-f]{40})$/,

  // Matches /c/<changeNum>/[*][/].
  CHANGE_LEGACY: /^\/c\/(\d+)\/?(.*)$/,
  CHANGE_NUMBER_LEGACY: /^\/(\d+)\/?/,

  // Matches
  // /c/<project>/+/<changeNum>/[<basePatchNum|edit>..][<patchNum|edit>].
  // TODO(kaspern): Migrate completely to project based URLs, with backwards
  // compatibility for change-only.
  CHANGE: /^\/c\/(.+)\/\+\/(\d+)(\/?((-?\d+|edit)(\.\.(\d+|edit))?))?\/?$/,

  // Matches /c/<project>/+/<changeNum>/[<patchNum|edit>],edit
  CHANGE_EDIT: /^\/c\/(.+)\/\+\/(\d+)(\/(\d+))?,edit\/?$/,

  // Matches /c/<project>/+/<changeNum>/comment/<commentId>/
  // Navigates to the diff view
  // This route is needed to resolve to patchNum vs latestPatchNum used in the
  // links generated in the emails.
  COMMENT: /^\/c\/(.+)\/\+\/(\d+)\/comment\/(\w+)\/?$/,

  // Matches /c/<project>/+/<changeNum>/comments/<commentId>/
  // Navigates to the commentId inside the Comments Tab
  COMMENTS_TAB: /^\/c\/(.+)\/\+\/(\d+)\/comments(?:\/)?(\w+)?\/?$/,

  // Matches
  // /c/<project>/+/<changeNum>/[<basePatchNum|edit>..]<patchNum|edit>/<path>.
  // TODO(kaspern): Migrate completely to project based URLs, with backwards
  // compatibility for change-only.
  // eslint-disable-next-line max-len
  DIFF: /^\/c\/(.+)\/\+\/(\d+)(\/((-?\d+|edit)(\.\.(\d+|edit))?(\/(.+))))\/?$/,

  // Matches /c/<project>/+/<changeNum>/[<patchNum|edit>]/<path>,edit[#lineNum]
  DIFF_EDIT: /^\/c\/(.+)\/\+\/(\d+)\/(\d+|edit)\/(.+),edit(#\d+)?$/,

  // Matches diff routes using @\d+ to specify a file name (whether or not
  // the project name is included).
  // eslint-disable-next-line max-len
  DIFF_LEGACY_LINENUM:
    /^\/c\/((.+)\/\+\/)?(\d+)(\/?((-?\d+|edit)(\.\.(\d+|edit))?\/(.+))?)@[ab]?\d+$/,

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
// custom element having the id "pg-app", but it is made explicit here.
// If you move this code to other place, please update comment about
// gr-router and gr-app in the PolyGerritIndexHtml.soy file if needed
const app = document.querySelector('gr-app');
if (!app) {
  console.info('No gr-app found (running tests)');
}

// Setup listeners outside of the router component initialization.
(function () {
  window.addEventListener('WebComponentsReady', () => {
    getAppContext().reportingService.timeEnd(Timing.WEB_COMPONENTS_READY);
  });
})();

export interface PageContextWithQueryMap extends PageContext {
  queryMap: Map<string, string> | URLSearchParams;
}

type QueryStringItem = [string, string]; // [key, value]

export interface PatchRangeParams {
  patchNum?: PatchSetNum;
  basePatchNum?: BasePatchSetNum;
}

export class GrRouter {
  readonly _app = app;

  _isRedirecting?: boolean;

  // This variable is to differentiate between internal navigation (false)
  // and for first navigation in app after loaded from server (true).
  _isInitialLoad = true;

  private readonly reporting = getAppContext().reportingService;

  private readonly routerModel = getAppContext().routerModel;

  private readonly restApiService = getAppContext().restApiService;

  start() {
    if (!this._app) {
      return;
    }
    this.startRouter();
  }

  setParams(params: AppElementParams | GenerateUrlParameters) {
    if (
      'project' in params &&
      params.project !== undefined &&
      'changeNum' in params
    )
      this.restApiService.setInProjectLookup(params.changeNum, params.project);

    this.routerModel.updateState({
      view: params.view,
      changeNum: 'changeNum' in params ? params.changeNum : undefined,
      patchNum: 'patchNum' in params ? params.patchNum ?? undefined : undefined,
    });
    this.appElement().params = params;
  }

  private appElement(): AppElement {
    // In Polymer2 you have to reach through the shadow root of the app
    // element. This obviously breaks encapsulation.
    // TODO(brohlfs): Make this more elegant, e.g. by exposing app-element
    // explicitly in app, or by delegating to it.

    // It is expected that application has a GrAppElement(id=='app-element')
    // at the document level or inside the shadow root of the GrApp ('gr-app')
    // element.
    return (document.getElementById('app-element') ||
      document
        .querySelector('gr-app')!
        .shadowRoot!.getElementById('app-element')!) as AppElement;
  }

  redirect(url: string) {
    this._isRedirecting = true;
    page.redirect(url);
  }

  generateUrl(params: GenerateUrlParameters) {
    const base = getBaseUrl();
    let url = '';

    if (params.view === GerritView.SEARCH) {
      url = this.generateSearchUrl(params);
    } else if (params.view === GerritView.CHANGE) {
      url = this.generateChangeUrl(params);
    } else if (params.view === GerritView.DASHBOARD) {
      url = this.generateDashboardUrl(params);
    } else if (
      params.view === GerritView.DIFF ||
      params.view === GerritView.EDIT
    ) {
      url = this.generateDiffOrEditUrl(params);
    } else if (params.view === GerritView.GROUP) {
      url = this.generateGroupUrl(params);
    } else if (params.view === GerritView.REPO) {
      url = this.generateRepoUrl(params);
    } else if (params.view === GerritView.ROOT) {
      url = '/';
    } else if (params.view === GerritView.SETTINGS) {
      url = this.generateSettingsUrl();
    } else {
      assertNever(params, "Can't generate");
    }

    return base + url;
  }

  generateWeblinks(
    params: GenerateWebLinksParameters
  ): GeneratedWebLink[] | GeneratedWebLink {
    switch (params.type) {
      case WeblinkType.EDIT:
        return this.getEditWebLinks(params);
      case WeblinkType.FILE:
        return this.getFileWebLinks(params);
      case WeblinkType.CHANGE:
        return this.getChangeWeblinks(params);
      case WeblinkType.PATCHSET:
        return this.getPatchSetWeblink(params);
      case WeblinkType.RESOLVE_CONFLICTS:
        return this.getResolveConflictsWeblinks(params);
      default:
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        assertNever(params, `Unsupported weblink ${(params as any).type}!`);
    }
  }

  private getPatchSetWeblink(
    params: GenerateWebLinksPatchsetParameters
  ): GeneratedWebLink {
    const {commit, options} = params;
    const {weblinks, config} = options || {};
    const name = commit && commit.slice(0, 7);
    const weblink = this.getBrowseCommitWeblink(weblinks, config);
    if (!weblink || !weblink.url) {
      return {name};
    } else {
      return {name, url: weblink.url};
    }
  }

  private getResolveConflictsWeblinks(
    params: GenerateWebLinksResolveConflictsParameters
  ): GeneratedWebLink[] {
    return params.options?.weblinks ?? [];
  }

  firstCodeBrowserWeblink(weblinks: GeneratedWebLink[]) {
    // This is an ordered allowed list of web link types that provide direct
    // links to the commit in the url property.
    const codeBrowserLinks = ['gitiles', 'browse', 'gitweb'];
    for (let i = 0; i < codeBrowserLinks.length; i++) {
      const weblink = weblinks.find(
        weblink => weblink.name === codeBrowserLinks[i]
      );
      if (weblink) {
        return weblink;
      }
    }
    return null;
  }

  getBrowseCommitWeblink(weblinks?: GeneratedWebLink[], config?: ServerInfo) {
    if (!weblinks) {
      return null;
    }
    let weblink;
    // Use primary weblink if configured and exists.
    if (config?.gerrit?.primary_weblink_name) {
      const primaryWeblinkName = config.gerrit.primary_weblink_name;
      weblink = weblinks.find(weblink => weblink.name === primaryWeblinkName);
    }
    if (!weblink) {
      weblink = this.firstCodeBrowserWeblink(weblinks);
    }
    if (!weblink) {
      return null;
    }
    return weblink;
  }

  getChangeWeblinks(
    params: GenerateWebLinksChangeParameters
  ): GeneratedWebLink[] {
    const weblinks = params.options?.weblinks;
    const config = params.options?.config;
    if (!weblinks || !weblinks.length) return [];
    const commitWeblink = this.getBrowseCommitWeblink(weblinks, config);
    return weblinks.filter(
      weblink =>
        !commitWeblink ||
        !commitWeblink.name ||
        weblink.name !== commitWeblink.name
    );
  }

  private getEditWebLinks(
    params: GenerateWebLinksEditParameters
  ): GeneratedWebLink[] {
    return params.options?.weblinks ?? [];
  }

  private getFileWebLinks(
    params: GenerateWebLinksFileParameters
  ): GeneratedWebLink[] {
    return params.options?.weblinks ?? [];
  }

  private generateSearchUrl(params: GenerateUrlSearchViewParameters) {
    let offsetExpr = '';
    if (params.offset && params.offset > 0) {
      offsetExpr = `,${params.offset}`;
    }

    if (params.query) {
      return '/q/' + encodeURL(params.query, true) + offsetExpr;
    }

    const operators: string[] = [];
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
      operators.push(
        'topic:' +
          addQuotesWhen(
            encodeURL(params.topic, false),
            /[\s:]/.test(params.topic)
          )
      );
    }
    if (params.hashtag) {
      operators.push(
        'hashtag:' +
          addQuotesWhen(
            encodeURL(params.hashtag.toLowerCase(), false),
            /[\s:]/.test(params.hashtag)
          )
      );
    }
    if (params.statuses) {
      if (params.statuses.length === 1) {
        operators.push('status:' + encodeURL(params.statuses[0], false));
      } else if (params.statuses.length > 1) {
        operators.push(
          '(' +
            params.statuses
              .map(s => `status:${encodeURL(s, false)}`)
              .join(' OR ') +
            ')'
        );
      }
    }

    return '/q/' + operators.join('+') + offsetExpr;
  }

  private generateChangeUrl(params: GenerateUrlChangeViewParameters) {
    let range = this.getPatchRangeExpression(params);
    if (range.length) {
      range = '/' + range;
    }
    let suffix = `${range}`;
    let queryString = '';
    if (params.forceReload) {
      queryString = 'forceReload=true';
    }
    if (params.edit) {
      suffix += ',edit';
    }
    if (params.commentId) {
      suffix = suffix + `/comments/${params.commentId}`;
    }
    if (queryString) {
      suffix += '?' + queryString;
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

  private generateDashboardUrl(params: GenerateUrlDashboardViewParameters) {
    const repoName = params.repo || params.project || undefined;
    if (params.sections) {
      // Custom dashboard.
      const queryParams = this.sectionsToEncodedParams(
        params.sections,
        repoName
      );
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

  private sectionsToEncodedParams(
    sections: DashboardSection[],
    repoName?: RepoName
  ) {
    return sections.map(section => {
      // If there is a repo name provided, make sure to substitute it into the
      // ${repo} (or legacy ${project}) query tokens.
      const query = repoName
        ? section.query.replace(REPO_TOKEN_PATTERN, repoName)
        : section.query;
      return encodeURIComponent(section.name) + '=' + encodeURIComponent(query);
    });
  }

  private generateDiffOrEditUrl(
    params: GenerateUrlDiffViewParameters | GenerateUrlEditViewParameters
  ) {
    let range = this.getPatchRangeExpression(params);
    if (range.length) {
      range = '/' + range;
    }

    let suffix = `${range}/${encodeURL(params.path || '', true)}`;

    if (params.view === GerritView.EDIT) {
      suffix += ',edit';
    }

    if (params.lineNum) {
      suffix += '#';
      if (isGenerateUrlDiffViewParameters(params) && params.leftSide) {
        suffix += 'b';
      }
      suffix += params.lineNum;
    }

    if (isGenerateUrlDiffViewParameters(params) && params.commentId) {
      suffix = `/comment/${params.commentId}` + suffix;
    }

    if (params.project) {
      const encodedProject = encodeURL(params.project, true);
      return `/c/${encodedProject}/+/${params.changeNum}${suffix}`;
    } else {
      return `/c/${params.changeNum}${suffix}`;
    }
  }

  private generateGroupUrl(params: GenerateUrlGroupViewParameters) {
    let url = `/admin/groups/${encodeURL(`${params.groupId}`, true)}`;
    if (params.detail === GroupDetailView.MEMBERS) {
      url += ',members';
    } else if (params.detail === GroupDetailView.LOG) {
      url += ',audit-log';
    }
    return url;
  }

  private generateRepoUrl(params: GenerateUrlRepoViewParameters) {
    let url = `/admin/repos/${encodeURL(`${params.repoName}`, true)}`;
    if (params.detail === RepoDetailView.GENERAL) {
      url += ',general';
    } else if (params.detail === RepoDetailView.ACCESS) {
      url += ',access';
    } else if (params.detail === RepoDetailView.BRANCHES) {
      url += ',branches';
    } else if (params.detail === RepoDetailView.TAGS) {
      url += ',tags';
    } else if (params.detail === RepoDetailView.COMMANDS) {
      url += ',commands';
    } else if (params.detail === RepoDetailView.DASHBOARDS) {
      url += ',dashboards';
    }
    return url;
  }

  private generateSettingsUrl() {
    return '/settings';
  }

  /**
   * Given an object of parameters, potentially including a `patchNum` or a
   * `basePatchNum` or both, return a string representation of that range. If
   * no range is indicated in the params, the empty string is returned.
   */
  getPatchRangeExpression(params: PatchRangeParams) {
    let range = '';
    if (params.patchNum) {
      range = `${params.patchNum}`;
    }
    if (params.basePatchNum && params.basePatchNum !== ParentPatchSetNum) {
      range = `${params.basePatchNum}..${range}`;
    }
    return range;
  }

  /**
   * Normalizes the params object, and determines if the URL needs to be
   * modified to fit the proper schema.
   *
   */
  normalizePatchRangeParams(params: PatchRangeParams) {
    if (params.basePatchNum === undefined) {
      return false;
    }
    const hasPatchNum = params.patchNum !== undefined;
    let needsRedirect = false;

    // Diffing a patch against itself is invalid, so if the base and revision
    // patches are equal clear the base.
    if (params.patchNum && params.basePatchNum === params.patchNum) {
      needsRedirect = true;
      params.basePatchNum = ParentPatchSetNum;
    } else if (!hasPatchNum) {
      // Regexes set basePatchNum instead of patchNum when only one is
      // specified. Redirect is not needed in this case.
      params.patchNum = params.basePatchNum;
      params.basePatchNum = ParentPatchSetNum;
    }
    return needsRedirect;
  }

  /**
   * Redirect the user to login using the given return-URL for redirection
   * after authentication success.
   */
  redirectToLogin(returnUrl: string) {
    const basePath = getBaseUrl() || '';
    page('/login/' + encodeURIComponent(returnUrl.substring(basePath.length)));
  }

  /**
   * Hashes parsed by page.js exclude "inner" hashes, so a URL like "/a#b#c"
   * is parsed to have a hash of "b" rather than "b#c". Instead, this method
   * parses hashes correctly. Will return an empty string if there is no hash.
   *
   * @return Everything after the first '#' ("a#b#c" -> "b#c").
   */
  getHashFromCanonicalPath(canonicalPath: string) {
    return canonicalPath.split('#').slice(1).join('#');
  }

  parseLineAddress(hash: string) {
    const match = hash.match(LINE_ADDRESS_PATTERN);
    if (!match) {
      return null;
    }
    return {
      leftSide: !!match[1],
      lineNum: Number(match[2]),
    };
  }

  /**
   * Check to see if the user is logged in and return a promise that only
   * resolves if the user is logged in. If the user us not logged in, the
   * promise is rejected and the page is redirected to the login flow.
   *
   * @return A promise yielding the original route data
   * (if it resolves).
   */
  redirectIfNotLoggedIn(data: PageContext) {
    return this.restApiService.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        return Promise.resolve();
      } else {
        this.redirectToLogin(data.canonicalPath);
        return Promise.reject(new Error());
      }
    });
  }

  /**  Page.js middleware that warms the REST API's logged-in cache line. */
  private loadUserMiddleware(_: PageContext, next: PageNextCallback) {
    this.restApiService.getLoggedIn().then(() => {
      next();
    });
  }

  /**  Page.js middleware that try parse the querystring into queryMap. */
  private queryStringMiddleware(ctx: PageContext, next: PageNextCallback) {
    (ctx as PageContextWithQueryMap).queryMap = this.createQueryMap(ctx);
    next();
  }

  private createQueryMap(ctx: PageContext) {
    if (ctx.querystring) {
      // https://caniuse.com/#search=URLSearchParams
      if (window.URLSearchParams) {
        return new URLSearchParams(ctx.querystring);
      } else {
        this.reporting.reportExecution(Execution.REACHABLE_CODE, {
          id: 'noURLSearchParams',
        });
        return new Map(this.parseQueryString(ctx.querystring));
      }
    }
    return new Map<string, string>();
  }

  /**
   * Map a route to a method on the router.
   *
   * @param pattern The page.js pattern for the route.
   * @param handlerName The method name for the handler. If the
   * route is matched, the handler will be executed with `this` referring
   * to the component. Its return value will be discarded so that it does
   * not interfere with page.js.
   * @param authRedirect If true, then auth is checked before
   * executing the handler. If the user is not logged in, it will redirect
   * to the login flow and the handler will not be executed. The login
   * redirect specifies the matched URL to be used after successful auth.
   */
  mapRoute(
    pattern: string | RegExp,
    handlerName: string,
    handler: (ctx: PageContextWithQueryMap) => void,
    authRedirect?: boolean
  ) {
    page(
      pattern,
      (ctx, next) => this.loadUserMiddleware(ctx, next),
      (ctx, next) => this.queryStringMiddleware(ctx, next),
      ctx => {
        this.reporting.locationChanged(handlerName);
        const promise = authRedirect
          ? this.redirectIfNotLoggedIn(ctx)
          : Promise.resolve();
        promise.then(() => {
          handler(ctx as PageContextWithQueryMap);
        });
      }
    );
  }

  startRouter() {
    const base = getBaseUrl();
    if (base) {
      page.base(base);
    }

    GerritNav.setup(
      (url, redirect?) => {
        if (redirect) {
          page.redirect(url);
        } else {
          page.show(url);
        }
      },
      params => this.generateUrl(params),
      params => this.generateWeblinks(params),
      x => x
    );

    page.exit('*', (_, next) => {
      if (!this._isRedirecting) {
        this.reporting.beforeLocationChanged();
      }
      this._isRedirecting = false;
      this._isInitialLoad = false;
      next();
    });

    // Remove the tracking param 'usp' (User Source Parameter) from the URL,
    // just to have users look at cleaner URLs.
    page((ctx, next) => {
      if (window.URLSearchParams) {
        const pathname = toPathname(ctx.canonicalPath);
        const searchParams = toSearchParams(ctx.canonicalPath);
        if (searchParams.has('usp')) {
          const usp = searchParams.get('usp');
          this.reporting.reportLifeCycle(LifeCycle.USER_REFERRED_FROM, {usp});
          searchParams.delete('usp');
          this.redirect(toPath(pathname, searchParams));
          return;
        }
      }
      next();
    });

    // Middleware
    page((ctx, next) => {
      document.body.scrollTop = 0;

      if (ctx.hash.match(RoutePattern.PLUGIN_SCREEN)) {
        // Redirect all urls using hash #/x/plugin/screen to /x/plugin/screen
        // This is needed to allow plugins to add basic #/x/ screen links to
        // any location.
        this.redirect(ctx.hash);
        return;
      }

      // Fire asynchronously so that the URL is changed by the time the event
      // is processed.
      setTimeout(() => {
        const detail: LocationChangeEventDetail = {
          hash: window.location.hash,
          pathname: window.location.pathname,
        };
        document.dispatchEvent(
          new CustomEvent('location-change', {
            detail,
            composed: true,
            bubbles: true,
          })
        );
      }, 1);
      next();
    });

    this.mapRoute(RoutePattern.ROOT, 'handleRootRoute', ctx =>
      this.handleRootRoute(ctx)
    );

    this.mapRoute(RoutePattern.DASHBOARD, 'handleDashboardRoute', ctx =>
      this.handleDashboardRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.CUSTOM_DASHBOARD,
      'handleCustomDashboardRoute',
      ctx => this.handleCustomDashboardRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.PROJECT_DASHBOARD,
      'handleProjectDashboardRoute',
      ctx => this.handleProjectDashboardRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.LEGACY_PROJECT_DASHBOARD,
      'handleLegacyProjectDashboardRoute',
      ctx => this.handleLegacyProjectDashboardRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.GROUP_INFO,
      'handleGroupInfoRoute',
      ctx => this.handleGroupInfoRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.GROUP_AUDIT_LOG,
      'handleGroupAuditLogRoute',
      ctx => this.handleGroupAuditLogRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.GROUP_MEMBERS,
      'handleGroupMembersRoute',
      ctx => this.handleGroupMembersRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.GROUP_LIST_OFFSET,
      'handleGroupListOffsetRoute',
      ctx => this.handleGroupListOffsetRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.GROUP_LIST_FILTER_OFFSET,
      'handleGroupListFilterOffsetRoute',
      ctx => this.handleGroupListFilterOffsetRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.GROUP_LIST_FILTER,
      'handleGroupListFilterRoute',
      ctx => this.handleGroupListFilterRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.GROUP_SELF,
      'handleGroupSelfRedirectRoute',
      ctx => this.handleGroupSelfRedirectRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.GROUP,
      'handleGroupRoute',
      ctx => this.handleGroupRoute(ctx),
      true
    );

    this.mapRoute(RoutePattern.PROJECT_OLD, 'handleProjectsOldRoute', ctx =>
      this.handleProjectsOldRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.REPO_COMMANDS,
      'handleRepoCommandsRoute',
      ctx => this.handleRepoCommandsRoute(ctx),
      true
    );

    this.mapRoute(RoutePattern.REPO_GENERAL, 'handleRepoGeneralRoute', ctx =>
      this.handleRepoGeneralRoute(ctx)
    );

    this.mapRoute(RoutePattern.REPO_ACCESS, 'handleRepoAccessRoute', ctx =>
      this.handleRepoAccessRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.REPO_DASHBOARDS,
      'handleRepoDashboardsRoute',
      ctx => this.handleRepoDashboardsRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.BRANCH_LIST_OFFSET,
      'handleBranchListOffsetRoute',
      ctx => this.handleBranchListOffsetRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.BRANCH_LIST_FILTER_OFFSET,
      'handleBranchListFilterOffsetRoute',
      ctx => this.handleBranchListFilterOffsetRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.BRANCH_LIST_FILTER,
      'handleBranchListFilterRoute',
      ctx => this.handleBranchListFilterRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.TAG_LIST_OFFSET,
      'handleTagListOffsetRoute',
      ctx => this.handleTagListOffsetRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.TAG_LIST_FILTER_OFFSET,
      'handleTagListFilterOffsetRoute',
      ctx => this.handleTagListFilterOffsetRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.TAG_LIST_FILTER,
      'handleTagListFilterRoute',
      ctx => this.handleTagListFilterRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.LEGACY_CREATE_GROUP,
      'handleCreateGroupRoute',
      ctx => this.handleCreateGroupRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.LEGACY_CREATE_PROJECT,
      'handleCreateProjectRoute',
      ctx => this.handleCreateProjectRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.REPO_LIST_OFFSET,
      'handleRepoListOffsetRoute',
      ctx => this.handleRepoListOffsetRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.REPO_LIST_FILTER_OFFSET,
      'handleRepoListFilterOffsetRoute',
      ctx => this.handleRepoListFilterOffsetRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.REPO_LIST_FILTER,
      'handleRepoListFilterRoute',
      ctx => this.handleRepoListFilterRoute(ctx)
    );

    this.mapRoute(RoutePattern.REPO, 'handleRepoRoute', ctx =>
      this.handleRepoRoute(ctx)
    );

    this.mapRoute(RoutePattern.PLUGINS, 'handlePassThroughRoute', () =>
      this.handlePassThroughRoute()
    );

    this.mapRoute(
      RoutePattern.PLUGIN_LIST_OFFSET,
      'handlePluginListOffsetRoute',
      ctx => this.handlePluginListOffsetRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.PLUGIN_LIST_FILTER_OFFSET,
      'handlePluginListFilterOffsetRoute',
      ctx => this.handlePluginListFilterOffsetRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.PLUGIN_LIST_FILTER,
      'handlePluginListFilterRoute',
      ctx => this.handlePluginListFilterRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.PLUGIN_LIST,
      'handlePluginListRoute',
      ctx => this.handlePluginListRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.QUERY_LEGACY_SUFFIX,
      'handleQueryLegacySuffixRoute',
      ctx => this.handleQueryLegacySuffixRoute(ctx)
    );

    this.mapRoute(RoutePattern.QUERY, 'handleQueryRoute', ctx =>
      this.handleQueryRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.CHANGE_ID_QUERY,
      'handleChangeIdQueryRoute',
      ctx => this.handleChangeIdQueryRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.DIFF_LEGACY_LINENUM,
      'handleLegacyLinenum',
      ctx => this.handleLegacyLinenum(ctx)
    );

    this.mapRoute(
      RoutePattern.CHANGE_NUMBER_LEGACY,
      'handleChangeNumberLegacyRoute',
      ctx => this.handleChangeNumberLegacyRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.DIFF_EDIT,
      'handleDiffEditRoute',
      ctx => this.handleDiffEditRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.CHANGE_EDIT,
      'handleChangeEditRoute',
      ctx => this.handleChangeEditRoute(ctx),
      true
    );

    this.mapRoute(RoutePattern.COMMENT, 'handleCommentRoute', ctx =>
      this.handleCommentRoute(ctx)
    );

    this.mapRoute(RoutePattern.COMMENTS_TAB, 'handleCommentsRoute', ctx =>
      this.handleCommentsRoute(ctx)
    );

    this.mapRoute(RoutePattern.DIFF, 'handleDiffRoute', ctx =>
      this.handleDiffRoute(ctx)
    );

    this.mapRoute(RoutePattern.CHANGE, 'handleChangeRoute', ctx =>
      this.handleChangeRoute(ctx)
    );

    this.mapRoute(RoutePattern.CHANGE_LEGACY, 'handleChangeLegacyRoute', ctx =>
      this.handleChangeLegacyRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.AGREEMENTS,
      'handleAgreementsRoute',
      () => this.handleAgreementsRoute(),
      true
    );

    this.mapRoute(
      RoutePattern.NEW_AGREEMENTS,
      'handleNewAgreementsRoute',
      ctx => this.handleNewAgreementsRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.SETTINGS_LEGACY,
      'handleSettingsLegacyRoute',
      ctx => this.handleSettingsLegacyRoute(ctx),
      true
    );

    this.mapRoute(
      RoutePattern.SETTINGS,
      'handleSettingsRoute',
      ctx => this.handleSettingsRoute(ctx),
      true
    );

    this.mapRoute(RoutePattern.REGISTER, 'handleRegisterRoute', ctx =>
      this.handleRegisterRoute(ctx)
    );

    this.mapRoute(RoutePattern.LOG_IN_OR_OUT, 'handlePassThroughRoute', () =>
      this.handlePassThroughRoute()
    );

    this.mapRoute(
      RoutePattern.IMPROPERLY_ENCODED_PLUS,
      'handleImproperlyEncodedPlusRoute',
      ctx => this.handleImproperlyEncodedPlusRoute(ctx)
    );

    this.mapRoute(RoutePattern.PLUGIN_SCREEN, 'handlePluginScreen', ctx =>
      this.handlePluginScreen(ctx)
    );

    this.mapRoute(
      RoutePattern.DOCUMENTATION_SEARCH_FILTER,
      'handleDocumentationSearchRoute',
      ctx => this.handleDocumentationSearchRoute(ctx)
    );

    // redirects /Documentation/q/* to /Documentation/q/filter:*
    this.mapRoute(
      RoutePattern.DOCUMENTATION_SEARCH,
      'handleDocumentationSearchRedirectRoute',
      ctx => this.handleDocumentationSearchRedirectRoute(ctx)
    );

    // Makes sure /Documentation/* links work (don't return 404)
    this.mapRoute(
      RoutePattern.DOCUMENTATION,
      'handleDocumentationRedirectRoute',
      ctx => this.handleDocumentationRedirectRoute(ctx)
    );

    // Note: this route should appear last so it only catches URLs unmatched
    // by other patterns.
    this.mapRoute(RoutePattern.DEFAULT, 'handleDefaultRoute', () =>
      this.handleDefaultRoute()
    );

    page.start();
  }

  /**
   * @return if handling the route involves asynchrony, then a
   * promise is returned. Otherwise, synchronous handling returns null.
   */
  handleRootRoute(data: PageContextWithQueryMap) {
    if (data.querystring.match(/^closeAfterLogin/)) {
      // Close child window on redirect after login.
      window.close();
      return null;
    }
    let hash = this.getHashFromCanonicalPath(data.canonicalPath);
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
      this.redirect(newUrl);
      return null;
    }
    return this.restApiService.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        this.redirect('/dashboard/self');
      } else {
        this.redirect('/q/status:open+-is:wip');
      }
    });
  }

  /**
   * Decode an application/x-www-form-urlencoded string.
   *
   * @param qs The application/x-www-form-urlencoded string.
   * @return The decoded string.
   */
  private decodeQueryString(qs: string) {
    return decodeURIComponent(qs.replace(PLUS_PATTERN, ' '));
  }

  /**
   * Parse a query string (e.g. window.location.search) into an array of
   * name/value pairs.
   *
   * @param qs The application/x-www-form-urlencoded query string.
   * @return An array of name/value pairs, where each
   * element is a 2-element array.
   */
  parseQueryString(qs: string): Array<QueryStringItem> {
    qs = qs.replace(QUESTION_PATTERN, '');
    if (!qs) {
      return [];
    }
    const params: Array<[string, string]> = [];
    qs.split('&').forEach(param => {
      const idx = param.indexOf('=');
      let name;
      let value;
      if (idx < 0) {
        name = this.decodeQueryString(param);
        value = '';
      } else {
        name = this.decodeQueryString(param.substring(0, idx));
        value = this.decodeQueryString(param.substring(idx + 1));
      }
      if (name) {
        params.push([name, value]);
      }
    });
    return params;
  }

  /**
   * Handle dashboard routes. These may be user, or project dashboards.
   */
  handleDashboardRoute(data: PageContextWithQueryMap) {
    // User dashboard. We require viewing user to be logged in, else we
    // redirect to login for self dashboard or simple owner search for
    // other user dashboard.
    return this.restApiService.getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        if (data.params[0].toLowerCase() === 'self') {
          this.redirectToLogin(data.canonicalPath);
        } else {
          this.redirect('/q/owner:' + encodeURIComponent(data.params[0]));
        }
      } else {
        this.setParams({
          view: GerritView.DASHBOARD,
          user: data.params[0],
        });
      }
    });
  }

  /**
   * Handle custom dashboard routes.
   *
   * @param qs Optional query string associated with the route.
   * If not given, window.location.search is used. (Used by tests).
   */
  handleCustomDashboardRoute(
    _: PageContextWithQueryMap,
    qs: string = window.location.search
  ) {
    const queryParams = this.parseQueryString(qs);
    let title = 'Custom Dashboard';
    const titleParam = queryParams.find(
      elem => elem[0].toLowerCase() === 'title'
    );
    if (titleParam) {
      title = titleParam[1];
    }
    // Dashboards support a foreach param which adds a base query to any
    // additional query.
    const forEachParam = queryParams.find(
      elem => elem[0].toLowerCase() === 'foreach'
    );
    let forEachQuery: string | null = null;
    if (forEachParam) {
      forEachQuery = forEachParam[1];
    }
    const sectionParams = queryParams.filter(
      elem =>
        elem[0] &&
        elem[1] &&
        elem[0].toLowerCase() !== 'title' &&
        elem[0].toLowerCase() !== 'foreach'
    );
    const sections = sectionParams.map(elem => {
      const query = forEachQuery ? `${forEachQuery} ${elem[1]}` : elem[1];
      return {
        name: elem[0],
        query,
      };
    });

    if (sections.length > 0) {
      // Custom dashboard view.
      this.setParams({
        view: GerritView.DASHBOARD,
        user: 'self',
        sections,
        title,
      });
      return Promise.resolve();
    }

    // Redirect /dashboard/ -> /dashboard/self.
    this.redirect('/dashboard/self');
    return Promise.resolve();
  }

  handleProjectDashboardRoute(data: PageContextWithQueryMap) {
    const project = data.params[0] as RepoName;
    this.setParams({
      view: GerritView.DASHBOARD,
      project,
      dashboard: decodeURIComponent(data.params[1]) as DashboardId,
    });
    this.reporting.setRepoName(project);
  }

  handleLegacyProjectDashboardRoute(data: PageContextWithQueryMap) {
    this.redirect('/p/' + data.params[0] + '/+/dashboard/' + data.params[1]);
  }

  handleGroupInfoRoute(data: PageContextWithQueryMap) {
    this.redirect('/admin/groups/' + encodeURIComponent(data.params[0]));
  }

  handleGroupSelfRedirectRoute(_: PageContextWithQueryMap) {
    this.redirect('/settings/#Groups');
  }

  handleGroupRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.GROUP,
      groupId: data.params[0] as GroupId,
    });
  }

  handleGroupAuditLogRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.GROUP,
      detail: GroupDetailView.LOG,
      groupId: data.params[0] as GroupId,
    });
  }

  handleGroupMembersRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.GROUP,
      detail: GroupDetailView.MEMBERS,
      groupId: data.params[0] as GroupId,
    });
  }

  handleGroupListOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-admin-group-list',
      offset: data.params[1] || 0,
      filter: null,
      openCreateModal: data.hash === 'create',
    });
  }

  handleGroupListFilterOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-admin-group-list',
      offset: data.params['offset'],
      filter: data.params['filter'],
    });
  }

  handleGroupListFilterRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-admin-group-list',
      filter: data.params['filter'] || null,
    });
  }

  handleProjectsOldRoute(data: PageContextWithQueryMap) {
    let params = '';
    if (data.params[1]) {
      params = encodeURIComponent(data.params[1]);
      if (data.params[1].includes(',')) {
        params = encodeURIComponent(data.params[1]).replace('%2C', ',');
      }
    }

    this.redirect(`/admin/repos/${params}`);
  }

  handleRepoCommandsRoute(data: PageContextWithQueryMap) {
    const repo = data.params[0] as RepoName;
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.COMMANDS,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  handleRepoGeneralRoute(data: PageContextWithQueryMap) {
    const repo = data.params[0] as RepoName;
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.GENERAL,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  handleRepoAccessRoute(data: PageContextWithQueryMap) {
    const repo = data.params[0] as RepoName;
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.ACCESS,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  handleRepoDashboardsRoute(data: PageContextWithQueryMap) {
    const repo = data.params[0] as RepoName;
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.DASHBOARDS,
      repo,
    });
    this.reporting.setRepoName(repo);
  }

  handleBranchListOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.BRANCHES,
      repo: data.params[0] as RepoName,
      offset: data.params[2] || 0,
      filter: null,
    });
  }

  handleBranchListFilterOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.BRANCHES,
      repo: data.params['repo'] as RepoName,
      offset: data.params['offset'],
      filter: data.params['filter'],
    });
  }

  handleBranchListFilterRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.BRANCHES,
      repo: data.params['repo'] as RepoName,
      filter: data.params['filter'] || null,
    });
  }

  handleTagListOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.TAGS,
      repo: data.params[0] as RepoName,
      offset: data.params[2] || 0,
      filter: null,
    });
  }

  handleTagListFilterOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.TAGS,
      repo: data.params['repo'] as RepoName,
      offset: data.params['offset'],
      filter: data.params['filter'],
    });
  }

  handleTagListFilterRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.REPO,
      detail: RepoDetailView.TAGS,
      repo: data.params['repo'] as RepoName,
      filter: data.params['filter'] || null,
    });
  }

  handleRepoListOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-repo-list',
      offset: data.params[1] || 0,
      filter: null,
      openCreateModal: data.hash === 'create',
    });
  }

  handleRepoListFilterOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-repo-list',
      offset: data.params['offset'],
      filter: data.params['filter'],
    });
  }

  handleRepoListFilterRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-repo-list',
      filter: data.params['filter'] || null,
    });
  }

  handleCreateProjectRoute(_: PageContextWithQueryMap) {
    // Redirects the legacy route to the new route, which displays the project
    // list with a hash 'create'.
    this.redirect('/admin/repos#create');
  }

  handleCreateGroupRoute(_: PageContextWithQueryMap) {
    // Redirects the legacy route to the new route, which displays the group
    // list with a hash 'create'.
    this.redirect('/admin/groups#create');
  }

  handleRepoRoute(data: PageContextWithQueryMap) {
    this.redirect(data.path + ',general');
  }

  handlePluginListOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-plugin-list',
      offset: data.params[1] || 0,
      filter: null,
    });
  }

  handlePluginListFilterOffsetRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-plugin-list',
      offset: data.params['offset'],
      filter: data.params['filter'],
    });
  }

  handlePluginListFilterRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-plugin-list',
      filter: data.params['filter'] || null,
    });
  }

  handlePluginListRoute(_: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.ADMIN,
      adminView: 'gr-plugin-list',
    });
  }

  handleQueryRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.SEARCH,
      query: data.params[0],
      offset: data.params[2],
    });
  }

  handleChangeIdQueryRoute(data: PageContextWithQueryMap) {
    // TODO(pcc): This will need to indicate that this was a change ID query if
    // standard queries gain the ability to search places like commit messages
    // for change IDs.
    this.setParams({
      view: GerritNav.View.SEARCH,
      query: data.params[0],
    });
  }

  handleQueryLegacySuffixRoute(ctx: PageContextWithQueryMap) {
    this.redirect(ctx.path.replace(LEGACY_QUERY_SUFFIX_PATTERN, ''));
  }

  handleChangeNumberLegacyRoute(ctx: PageContextWithQueryMap) {
    this.redirect('/c/' + encodeURIComponent(ctx.params[0]));
  }

  handleChangeRoute(ctx: PageContextWithQueryMap) {
    // Parameter order is based on the regex group number matched.
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const params: GenerateUrlChangeViewParameters = {
      project: ctx.params[0] as RepoName,
      changeNum,
      basePatchNum: convertToPatchSetNum(ctx.params[4]) as BasePatchSetNum,
      patchNum: convertToPatchSetNum(ctx.params[6]),
      view: GerritView.CHANGE,
    };

    if (ctx.queryMap.has('forceReload')) {
      params.forceReload = true;
      history.replaceState(
        null,
        '',
        location.href.replace(/[?&]forceReload=true/, '')
      );
    }

    const tab = ctx.queryMap.get('tab');
    if (tab) params.tab = tab;
    const filter = ctx.queryMap.get('filter');
    if (filter) params.filter = filter;
    const select = ctx.queryMap.get('select');
    if (select) params.select = select;
    const attempt = ctx.queryMap.get('attempt');
    if (attempt) {
      const attemptInt = parseInt(attempt);
      if (!isNaN(attemptInt) && attemptInt > 0) {
        params.attempt = attemptInt;
      }
    }

    this.reporting.setRepoName(params.project);
    this.reporting.setChangeId(changeNum);
    this.redirectOrNavigate(params);
  }

  handleCommentRoute(ctx: PageContextWithQueryMap) {
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const params: GenerateUrlDiffViewParameters = {
      project: ctx.params[0] as RepoName,
      changeNum,
      commentId: ctx.params[2] as UrlEncodedCommentId,
      view: GerritView.DIFF,
      commentLink: true,
    };
    this.reporting.setRepoName(params.project);
    this.reporting.setChangeId(changeNum);
    this.redirectOrNavigate(params);
  }

  handleCommentsRoute(ctx: PageContextWithQueryMap) {
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const params: GenerateUrlChangeViewParameters = {
      project: ctx.params[0] as RepoName,
      changeNum,
      commentId: ctx.params[2] as UrlEncodedCommentId,
      view: GerritView.CHANGE,
    };
    this.reporting.setRepoName(params.project);
    this.reporting.setChangeId(changeNum);
    this.redirectOrNavigate(params);
  }

  handleDiffRoute(ctx: PageContextWithQueryMap) {
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    // Parameter order is based on the regex group number matched.
    const params: GenerateUrlDiffViewParameters = {
      project: ctx.params[0] as RepoName,
      changeNum,
      basePatchNum: convertToPatchSetNum(ctx.params[4]) as BasePatchSetNum,
      patchNum: convertToPatchSetNum(ctx.params[6]),
      path: ctx.params[8],
      view: GerritView.DIFF,
    };
    const address = this.parseLineAddress(ctx.hash);
    if (address) {
      params.leftSide = address.leftSide;
      params.lineNum = address.lineNum;
    }
    this.reporting.setRepoName(params.project);
    this.reporting.setChangeId(changeNum);
    this.redirectOrNavigate(params);
  }

  handleChangeLegacyRoute(ctx: PageContextWithQueryMap) {
    const changeNum = Number(ctx.params[0]) as NumericChangeId;
    if (!changeNum) {
      this.show404();
      return;
    }
    this.restApiService.getFromProjectLookup(changeNum).then(project => {
      // Show a 404 and terminate if the lookup request failed. Attempting
      // to redirect after failing to get the project loops infinitely.
      if (!project) {
        this.show404();
        return;
      }
      this.redirect(`/c/${project}/+/${changeNum}/${ctx.params[1]}`);
    });
  }

  handleLegacyLinenum(ctx: PageContextWithQueryMap) {
    this.redirect(ctx.path.replace(LEGACY_LINENUM_PATTERN, '#$1'));
  }

  handleDiffEditRoute(ctx: PageContextWithQueryMap) {
    // Parameter order is based on the regex group number matched.
    const project = ctx.params[0] as RepoName;
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    this.redirectOrNavigate({
      project,
      changeNum,
      // for edit view params, patchNum cannot be undefined
      patchNum: convertToPatchSetNum(ctx.params[2])!,
      path: ctx.params[3],
      lineNum: ctx.hash,
      view: GerritView.EDIT,
    });
    this.reporting.setRepoName(project);
    this.reporting.setChangeId(changeNum);
  }

  handleChangeEditRoute(ctx: PageContextWithQueryMap) {
    // Parameter order is based on the regex group number matched.
    const project = ctx.params[0] as RepoName;
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const params: GenerateUrlChangeViewParameters = {
      project,
      changeNum,
      patchNum: convertToPatchSetNum(ctx.params[3]),
      view: GerritView.CHANGE,
      edit: true,
      tab: ctx.queryMap.get('tab') ?? '',
    };
    if (ctx.queryMap.has('forceReload')) {
      params.forceReload = true;
      history.replaceState(
        null,
        '',
        location.href.replace(/[?&]forceReload=true/, '')
      );
    }
    this.redirectOrNavigate(params);

    this.reporting.setRepoName(project);
    this.reporting.setChangeId(changeNum);
  }

  /**
   * Normalize the patch range params for a the change or diff view and
   * redirect if URL upgrade is needed.
   */
  private redirectOrNavigate(params: GenerateUrlParameters & PatchRangeParams) {
    const needsRedirect = this.normalizePatchRangeParams(params);
    if (needsRedirect) {
      this.redirect(this.generateUrl(params));
    } else {
      this.setParams(params);
    }
  }

  handleAgreementsRoute() {
    this.redirect('/settings/#Agreements');
  }

  handleNewAgreementsRoute(data: PageContextWithQueryMap) {
    data.params['view'] = GerritView.AGREEMENTS;
    // TODO(TS): create valid object
    this.setParams(data.params as unknown as AppElementAgreementParam);
  }

  handleSettingsLegacyRoute(data: PageContextWithQueryMap) {
    // email tokens may contain '+' but no space.
    // The parameter parsing replaces all '+' with a space,
    // undo that to have valid tokens.
    const token = data.params[0].replace(/ /g, '+');
    this.setParams({
      view: GerritView.SETTINGS,
      emailToken: token,
    });
  }

  handleSettingsRoute(_: PageContextWithQueryMap) {
    this.setParams({view: GerritView.SETTINGS});
  }

  handleRegisterRoute(ctx: PageContextWithQueryMap) {
    this.setParams({justRegistered: true});
    let path = ctx.params[0] || '/';

    // Prevent redirect looping.
    if (path.startsWith('/register')) {
      path = '/';
    }

    if (path[0] !== '/') {
      return;
    }
    this.redirect(getBaseUrl() + path);
  }

  /**
   * Handler for routes that should pass through the router and not be caught
   * by the catchall _handleDefaultRoute handler.
   */
  handlePassThroughRoute() {
    windowLocationReload();
  }

  /**
   * URL may sometimes have /+/ encoded to / /.
   * Context: Issue 6888, Issue 7100
   */
  handleImproperlyEncodedPlusRoute(ctx: PageContextWithQueryMap) {
    let hash = this.getHashFromCanonicalPath(ctx.canonicalPath);
    if (hash.length) {
      hash = '#' + hash;
    }
    this.redirect(`/c/${ctx.params[0]}/+/${ctx.params[1]}${hash}`);
  }

  handlePluginScreen(ctx: PageContextWithQueryMap) {
    const view = GerritView.PLUGIN_SCREEN;
    const plugin = ctx.params[0];
    const screen = ctx.params[1];
    this.setParams({view, plugin, screen});
  }

  handleDocumentationSearchRoute(data: PageContextWithQueryMap) {
    this.setParams({
      view: GerritView.DOCUMENTATION_SEARCH,
      filter: data.params['filter'] || null,
    });
  }

  handleDocumentationSearchRedirectRoute(data: PageContextWithQueryMap) {
    this.redirect(
      '/Documentation/q/filter:' + encodeURIComponent(data.params[0])
    );
  }

  handleDocumentationRedirectRoute(data: PageContextWithQueryMap) {
    if (data.params[1]) {
      windowLocationReload();
    } else {
      // Redirect /Documentation to /Documentation/index.html
      this.redirect('/Documentation/index.html');
    }
  }

  /**
   * Catchall route for when no other route is matched.
   */
  handleDefaultRoute() {
    if (this._isInitialLoad) {
      // Server recognized this route as polygerrit, so we show 404.
      this.show404();
    } else {
      // Route can be recognized by server, so we pass it to server.
      this.handlePassThroughRoute();
    }
  }

  private show404() {
    // Note: the app's 404 display is tightly-coupled with catching 404
    // network responses, so we simulate a 404 response status to display it.
    // TODO: Decouple the gr-app error view from network responses.
    firePageError(new Response('', {status: 404}));
  }
}
