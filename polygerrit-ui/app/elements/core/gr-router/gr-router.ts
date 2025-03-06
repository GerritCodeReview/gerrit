/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Page, PageOptions, PageContext, PageNextCallback} from './gr-page';
import {NavigationService} from '../gr-navigation/gr-navigation';
import {getAppContext} from '../../../services/app-context';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  convertToPatchSetNum,
} from '../../../utils/patch-set-util';
import {assert, assertIsDefined} from '../../../utils/common-util';
import {
  BasePatchSetNum,
  GroupId,
  NumericChangeId,
  RevisionPatchSetNum,
  RepoName,
  UrlEncodedCommentId,
  PARENT,
  PatchSetNumber,
  BranchName,
} from '../../../types/common';
import {AppElement, AppElementParams} from '../../gr-app-types';
import {LocationChangeEventDetail} from '../../../types/events';
import {GerritView, RouterModel} from '../../../services/router/router-model';
import {fire, fireAlert, firePageError} from '../../../utils/event-util';
import {windowLocationReload} from '../../../utils/dom-util';
import {
  encodeURL,
  getBaseUrl,
  PatchRangeParams,
  toPath,
  toPathname,
  toSearchParams,
} from '../../../utils/url-util';
import {LifeCycle, Timing} from '../../../constants/reporting';
import {
  LATEST_ATTEMPT,
  stringToAttemptChoice,
} from '../../../models/checks/checks-util';
import {
  AdminChildView,
  AdminViewModel,
  AdminViewState,
  PLUGIN_LIST_ROUTE,
  SERVER_INFO_ROUTE,
} from '../../../models/views/admin';
import {
  AgreementViewModel,
  AgreementViewState,
} from '../../../models/views/agreement';
import {
  RepoDetailView,
  RepoViewModel,
  RepoViewState,
} from '../../../models/views/repo';
import {
  createGroupUrl,
  GroupDetailView,
  GroupViewModel,
  GroupViewState,
} from '../../../models/views/group';
import {
  ChangeChildView,
  ChangeViewModel,
  ChangeViewState,
  createChangeViewUrl,
  createDiffUrl,
} from '../../../models/views/change';
import {
  DashboardType,
  DashboardViewModel,
  DashboardViewState,
  PROJECT_DASHBOARD_ROUTE,
} from '../../../models/views/dashboard';
import {
  SettingsViewModel,
  SettingsViewState,
} from '../../../models/views/settings';
import {define} from '../../../models/dependency';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {
  DocumentationViewModel,
  DocumentationViewState,
} from '../../../models/views/documentation';
import {PluginViewModel, PluginViewState} from '../../../models/views/plugin';
import {SearchViewModel, SearchViewState} from '../../../models/views/search';
import {DashboardSection} from '../../../utils/dashboard-util';
import {Subscription} from 'rxjs';
import {
  addPath,
  findComment,
  getPatchRangeForCommentUrl,
  isInBaseOfPatchRange,
} from '../../../utils/comment-util';
import {isFileUnchanged} from '../../../utils/diff-util';
import {Route, ViewState} from '../../../models/views/base';
import {Model} from '../../../models/base/model';
import {
  InteractivePromise,
  interactivePromise,
  noAwait,
  timeoutPromise,
} from '../../../utils/async-util';
import {Finalizable} from '../../../types/types';
import {assign} from '../../../utils/location-util';

// TODO: Move all patterns to view model files and use the `Route` interface,
// which will enforce using `RegExp` in its `urlPattern` property.
const RoutePattern = {
  ROOT: /^\/$/,

  DASHBOARD: /^\/dashboard\/(.+)$/,
  CUSTOM_DASHBOARD: /^\/dashboard\/?$/,
  LEGACY_PROJECT_DASHBOARD: /^\/projects\/(.+),dashboards\/(.+)/,

  AGREEMENTS: /^\/settings\/agreements\/?/,
  NEW_AGREEMENTS: /^\/settings\/new-agreement\/?/,
  REGISTER: /^\/register(\/.*)?$/,

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

  // Matches /admin/create-project
  LEGACY_CREATE_PROJECT: /^\/admin\/create-project\/?$/,

  // Matches /admin/create-project
  LEGACY_CREATE_GROUP: /^\/admin\/create-group\/?$/,

  PROJECT_OLD: /^\/admin\/(projects)\/?(.+)?$/,

  // Matches /admin/repos/<repo>
  REPO: /^\/admin\/repos\/([^,]+)$/,

  // Matches /admin/repos/<repo>,commands.
  REPO_COMMANDS: /^\/admin\/repos\/(.+),commands$/,

  // For creating a change, and going directly into editing mode for one file.
  REPO_EDIT_FILE:
    /^\/admin\/repos\/edit\/repo\/(.+)\/branch\/(.+)\/file\/(.+)$/,

  REPO_GENERAL: /^\/admin\/repos\/(.+),general$/,

  // Matches /admin/repos/<repos>,access.
  REPO_ACCESS: /^\/admin\/repos\/(.+),access$/,

  // Matches /admin/repos/<repos>,access.
  REPO_DASHBOARDS: /^\/admin\/repos\/(.+),dashboards$/,

  // Matches /admin/plugins with optional filter and offset.
  PLUGIN_LIST: /^\/admin\/plugins\/?(?:\/q\/filter:(.*?))?(?:,(\d+))?$/,
  // Matches /admin/groups with optional filter and offset.
  GROUP_LIST: /^\/admin\/groups\/?(?:\/q\/filter:(.*?))?(?:,(\d+))?$/,
  // Matches /admin/repos with optional filter and offset.
  REPO_LIST: /^\/admin\/repos\/?(?:\/q\/filter:(.*?))?(?:,(\d+))?$/,
  // Matches /admin/repos/$REPO,branches with optional filter and offset.
  BRANCH_LIST:
    /^\/admin\/repos\/(.+),branches\/?(?:\/q\/filter:(.*?))?(?:,(\d+))?$/,
  // Matches /admin/repos/$REPO,tags with optional filter and offset.
  TAG_LIST: /^\/admin\/repos\/(.+),tags\/?(?:\/q\/filter:(.*?))?(?:,(\d+))?$/,

  // Matches /admin/server-info.
  SERVER_INFO: /^\/admin\/server-info$/,

  QUERY: /^\/q\/(.+?)(,(\d+))?$/,

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

  DOCUMENTATION_SEARCH_FILTER: /^\/Documentation\/q\/filter:(.*)$/,
  DOCUMENTATION_SEARCH: /^\/Documentation\/q\/(.*)$/,
  DOCUMENTATION: /^\/Documentation(\/)?(.+)?/,
};

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
 * GWT UI would use @\d+ at the end of a path to indicate linenum.
 */
const LEGACY_LINENUM_PATTERN = /@([ab]?\d+)$/;

const LEGACY_QUERY_SUFFIX_PATTERN = /,n,z$/;

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

export const routerToken = define<GrRouter>('router');

export class GrRouter implements Finalizable, NavigationService {
  readonly _app = app;

  _isRedirecting?: boolean;

  // This variable is to differentiate between internal navigation (false)
  // and for first navigation in app after loaded from server (true).
  _isInitialLoad = true;

  private subscriptions: Subscription[] = [];

  private view?: GerritView;

  // While this set is not empty, the router will refuse to navigate to
  // other pages, but instead show an alert. It will also install a
  // `beforeUnload` handler that prevents the browser from closing the tab.
  private navigationBlockers: Set<string> = new Set<string>();

  // While navigationBlockers is not empty, this promise will continuously
  // check for navigationBlockers to become empty again.
  // This is undefined, iff navigationBlockers is empty.
  private navigationBlockerPromise?: InteractivePromise<void>;

  readonly page = new Page();

  constructor(
    private readonly reporting: ReportingService,
    private readonly routerModel: RouterModel,
    private readonly restApiService: RestApiService,
    private readonly adminViewModel: AdminViewModel,
    private readonly agreementViewModel: AgreementViewModel,
    private readonly changeViewModel: ChangeViewModel,
    private readonly dashboardViewModel: DashboardViewModel,
    private readonly documentationViewModel: DocumentationViewModel,
    private readonly groupViewModel: GroupViewModel,
    private readonly pluginViewModel: PluginViewModel,
    private readonly repoViewModel: RepoViewModel,
    private readonly searchViewModel: SearchViewModel,
    private readonly settingsViewModel: SettingsViewModel
  ) {
    this.subscriptions = [
      // TODO: Do the same for other view models.
      // We want to make sure that the current view model state is always
      // reflected back into the URL bar.
      this.changeViewModel.state$.subscribe(state => {
        if (!state) return;
        // Note that router model view must be updated before view model state.
        // So this check is slightly fragile, but should work.
        if (this.view !== GerritView.CHANGE) return;
        const browserUrl = new URL(window.location.toString());
        const stateUrl = new URL(createChangeViewUrl(state), browserUrl);

        // Keeping the hash and certain parameters are stop-gap solution. We
        // should find better ways of maintaining an overall consistent URL
        // state.
        stateUrl.hash = browserUrl.hash;
        for (const p of browserUrl.searchParams.entries()) {
          if (p[0] === 'experiment') stateUrl.searchParams.append(p[0], p[1]);
        }

        if (browserUrl.toString() !== stateUrl.toString()) {
          this.page.replace(stateUrl.toString(), {}, /* dispatch: */ false);
        }
      }),
      this.routerModel.routerView$.subscribe(view => (this.view = view)),
    ];
  }

  blockNavigation(reason: string): void {
    assert(!!reason, 'empty reason is not allowed');
    this.navigationBlockers.add(reason);
    if (this.navigationBlockers.size === 1) {
      this.navigationBlockerPromise = interactivePromise();
      window.addEventListener('beforeunload', this.beforeUnloadHandler);
    }
  }

  releaseNavigation(reason: string): void {
    assert(!!reason, 'empty reason is not allowed');
    this.navigationBlockers.delete(reason);
    if (this.navigationBlockers.size === 0) {
      window.removeEventListener('beforeunload', this.beforeUnloadHandler);
      this.navigationBlockerPromise?.resolve();
    }
  }

  private beforeUnloadHandler = (event: BeforeUnloadEvent) => {
    const reason = [...this.navigationBlockers][0];
    if (!reason) return;

    event.preventDefault(); // Cancel the event (per the standard).
    event.returnValue = reason; // Chrome requires returnValue to be set.
    // Note that we could as well just use '' instead of `reason`. Browsers will
    // just show a generic message anyway.
    return reason;
  };

  finalize(): void {
    for (const subscription of this.subscriptions) {
      subscription.unsubscribe();
    }
    this.subscriptions = [];
    this.page.stop();
    window.removeEventListener('beforeunload', this.beforeUnloadHandler);
  }

  start() {
    if (!this._app) {
      return;
    }
    this.startRouter();
  }

  setState(state: AppElementParams) {
    // TODO: Move this logic into the change model.
    if ('repo' in state && state.repo !== undefined && 'changeNum' in state)
      this.restApiService.addRepoNameToCache(state.changeNum, state.repo);

    this.routerModel.setState({view: state.view});
    // We are trying to reset the change (view) model when navigating to other
    // views, because we don't trust our reset logic at the moment. The models
    // singletons and might unintentionally keep state from one change to
    // another. TODO: Let's find some way to avoid that.
    if (state.view !== GerritView.CHANGE) {
      this.changeViewModel.setState(undefined);
    }
    this.appElement().params = state;
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
    this.page.redirect(url);
  }

  /**
   * Normalizes the patchset numbers of the params object.
   */
  normalizePatchRangeParams(params: PatchRangeParams) {
    if (params.basePatchNum === undefined) return;

    // Diffing a patch against itself is invalid, so if the base and revision
    // patches are equal clear the base.
    if (params.patchNum && params.basePatchNum === params.patchNum) {
      params.basePatchNum = PARENT;
      return;
    }
    // Regexes set basePatchNum instead of patchNum when only one is
    // specified.
    if (params.patchNum === undefined) {
      params.patchNum = params.basePatchNum as RevisionPatchSetNum;
      params.basePatchNum = PARENT;
    }
  }

  /**
   * Redirect the user to login using the given return-URL for redirection
   * after authentication success.
   */
  redirectToLogin(returnUrl: string) {
    const basePath = getBaseUrl() || '';
    // We are not using `this.getNavigation().setUrl()`, because the login
    // page is served directly from the backend and is not part of the web
    // app.
    assign(
      window.location,
      `${basePath}/login/${encodeURIComponent(
        returnUrl.substring(basePath.length)
      )}`
    );
  }

  /**
   * Hashes parsed by gr-page exclude "inner" hashes, so a URL like "/a#b#c"
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
   * @return A promise yielding the original route ctx
   * (if it resolves).
   */
  redirectIfNotLoggedIn(path: string) {
    return this.restApiService.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        return Promise.resolve();
      } else {
        this.redirectToLogin(path);
        return Promise.reject(new Error());
      }
    });
  }

  /**  gr-page middleware that warms the REST API's logged-in cache line. */
  private loadUserMiddleware(_: PageContext, next: PageNextCallback) {
    noAwait(this.restApiService.getLoggedIn());
    next();
  }

  /**
   * Map a route to a method on the router.
   *
   * @param pattern The regex pattern for the route.
   * @param handlerName The method name for the handler. If the
   * route is matched, the handler will be executed with `this` referring
   * to the component. Its return value will be discarded.
   * TODO: Get rid of this parameter. This is really not something that the
   * router wants to be concerned with. The reporting service and the view
   * models should figure that out between themselves.
   * @param authRedirect If true, then auth is checked before
   * executing the handler. If the user is not logged in, it will redirect
   * to the login flow and the handler will not be executed. The login
   * redirect specifies the matched URL to be used after successful auth.
   */
  mapRoute(
    pattern: RegExp,
    handlerName: string,
    handler: (ctx: PageContext) => void,
    authRedirect?: boolean
  ) {
    this.page.registerRoute(pattern, (ctx, next) =>
      this.loadUserMiddleware(ctx, next)
    );
    this.page.registerRoute(pattern, ctx => {
      this.reporting.locationChanged(handlerName);
      const promise = authRedirect
        ? this.redirectIfNotLoggedIn(ctx.canonicalPath)
        : Promise.resolve();
      promise.then(() => {
        handler(ctx);
      });
    });
  }

  /**
   * Convenience wrapper of `mapRoute()` for when you have a `Route` object that
   * can deal with state creation. Takes care of setting the view model state,
   * which is currently duplicated lots of times for direct callers of
   * `mapRoute()`.
   */
  mapRouteState<T extends ViewState>(
    route: Route<T>,
    viewModel: Model<T | undefined>,
    handlerName: string,
    authRedirect?: boolean
  ) {
    const handler = (ctx: PageContext) => {
      const state = route.createState(ctx);
      // Note that order is important: `this.setState()` must be called before
      // `viewModel.setState()`. Otherwise the chain of model subscriptions
      // would be very different. Some views may want app element to swap the
      // top level view first. Also, `this.setState()` has some special change
      // view model resetting logic. Eventually the order might not be important
      // anymore, but be careful! :-)
      this.setState(state as AppElementParams);
      viewModel.setState(state);
    };
    this.mapRoute(route.urlPattern, handlerName, handler, authRedirect);
  }

  /**
   * This is similar to letting the browser navigate to this URL when the user
   * clicks it, or to just setting `window.location.href` directly.
   *
   * This adds a new entry to the browser location history. Consier using
   * `replaceUrl()`, if you want to avoid that.
   *
   * page.show() eventually just calls `window.history.pushState()`.
   */
  setUrl(url: string) {
    // TODO: Use window.location.assign() instead of page.show(), if the URL is
    // external, i.e. not handled by the router.
    this.page.show(url);
  }

  /**
   * Navigate to this URL, but replace the current URL in the history instead of
   * adding a new one (which is what `setUrl()` would do).
   *
   * this.page.redirect() eventually just calls `window.history.replaceState()`.
   */
  replaceUrl(url: string) {
    // TODO: Use window.location.replace() instead of page.redirect(), if the
    // URL is external, i.e. not handled by the router.
    this.redirect(url);
  }

  private dispatchLocationChangeEvent() {
    const detail: LocationChangeEventDetail = {
      hash: window.location.hash,
      pathname: window.location.pathname,
    };
    fire(document, 'location-change', detail);
  }

  _testOnly_startRouter() {
    this.startRouter({dispatch: false, base: getBaseUrl()});
  }

  startRouter(opts: PageOptions = {dispatch: true, base: getBaseUrl()}) {
    this.page.registerExitRoute(/(.*)/, (_, next) => {
      if (!this._isRedirecting) {
        this.reporting.beforeLocationChanged();
      }
      this._isRedirecting = false;
      this._isInitialLoad = false;
      next();
    });

    // Remove the tracking param 'usp' (User Source Parameter) from the URL,
    // just to have users look at cleaner URLs.
    this.page.registerRoute(/(.*)/, (ctx, next) => {
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

    // Block navigation while navigationBlockers exist. But wait 1 second for
    // those blockers to resolve. If they do, then still navigate. We don't want
    // to annoy users by forcing them to navigate twice only because it took
    // another 200ms for a comment to save or something similar.
    this.page.registerRoute(/(.*)/, (_, next) => {
      if (this.navigationBlockers.size === 0) {
        next();
        return;
      }

      const msg = 'Waiting 1 second for navigation blockers to resolve ...';
      fireAlert(document, msg);
      Promise.race([this.navigationBlockerPromise, timeoutPromise(1000)]).then(
        () => {
          if (this.navigationBlockers.size === 0) {
            next();
          } else {
            const reason = [...this.navigationBlockers][0];
            fireAlert(document, `Navigation is blocked by: ${reason}`);
          }
        }
      );
    });

    // Middleware
    this.page.registerRoute(/(.*)/, (ctx, next) => {
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
        this.dispatchLocationChangeEvent();
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

    this.mapRouteState(
      PROJECT_DASHBOARD_ROUTE,
      this.dashboardViewModel,
      'handleProjectDashboardRoute'
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
      RoutePattern.GROUP_LIST,
      'handleGroupListRoute',
      ctx => this.handleGroupListRoute(ctx),
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

    this.mapRoute(
      RoutePattern.REPO_EDIT_FILE,
      'handleRepoEditFileRoute',
      ctx => this.handleRepoEditFileRoute(ctx),
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

    this.mapRoute(RoutePattern.BRANCH_LIST, 'handleBranchListRoute', ctx =>
      this.handleBranchListRoute(ctx)
    );

    this.mapRoute(RoutePattern.TAG_LIST, 'handleTagListRoute', ctx =>
      this.handleTagListRoute(ctx)
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

    this.mapRoute(RoutePattern.REPO_LIST, 'handleRepoListRoute', ctx =>
      this.handleRepoListRoute(ctx)
    );

    this.mapRoute(RoutePattern.REPO, 'handleRepoRoute', ctx =>
      this.handleRepoRoute(ctx)
    );

    this.mapRoute(
      RoutePattern.PLUGIN_LIST,
      'handlePluginListFilterRoute',
      ctx => this.handlePluginListFilterRoute(ctx),
      true
    );

    this.mapRouteState(
      PLUGIN_LIST_ROUTE,
      this.adminViewModel,
      'handlePluginListRoute',
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
      () => this.handleNewAgreementsRoute(),
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

    this.mapRoute(
      RoutePattern.IMPROPERLY_ENCODED_PLUS,
      'handleImproperlyEncodedPlusRoute',
      ctx => this.handleImproperlyEncodedPlusRoute(ctx)
    );

    this.mapRoute(RoutePattern.PLUGIN_SCREEN, 'handlePluginScreen', ctx =>
      this.handlePluginScreen(ctx)
    );

    this.mapRouteState(
      SERVER_INFO_ROUTE,
      this.adminViewModel,
      'handleServerInfoRoute',
      true
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

    this.page.start(opts);
  }

  /**
   * @return if handling the route involves asynchrony, then a
   * promise is returned. Otherwise, synchronous handling returns null.
   */
  handleRootRoute(ctx: PageContext) {
    if (ctx.querystring.match(/^closeAfterLogin/)) {
      // Close child window on redirect after login.
      window.close();
      return null;
    }
    let hash = this.getHashFromCanonicalPath(ctx.canonicalPath);
    // For backward compatibility with GWT links.
    if (hash) {
      // In certain login flows the server may redirect to a hash without
      // a leading slash, which gr-page doesn't handle correctly.
      if (hash[0] !== '/') {
        hash = '/' + hash;
      }
      if (hash.includes('/ /') && ctx.canonicalPath.includes('/+/')) {
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
   * Handle dashboard routes. These may be user, or project dashboards.
   */
  handleDashboardRoute(ctx: PageContext) {
    // User dashboard. We require viewing user to be logged in, else we
    // redirect to login for self dashboard or simple owner search for
    // other user dashboard.
    return this.restApiService.getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        if (ctx.params[0].toLowerCase() === 'self') {
          this.redirectToLogin(ctx.canonicalPath);
        } else {
          this.redirect('/q/owner:' + encodeURL(ctx.params[0]));
        }
      } else {
        const state: DashboardViewState = {
          view: GerritView.DASHBOARD,
          type: DashboardType.USER,
          user: ctx.params[0],
        };
        // Note that router model view must be updated before view models.
        this.setState(state);
        this.dashboardViewModel.setState(state);
      }
    });
  }

  handleCustomDashboardRoute(ctx: PageContext) {
    const queryParams = new URLSearchParams(ctx.querystring);

    let title = 'Custom Dashboard';
    const titleParam = queryParams.get('title');
    if (titleParam) title = titleParam;
    queryParams.delete('title');

    let forEachQuery = '';
    const forEachParam = queryParams.get('foreach');
    if (forEachParam) forEachQuery = forEachParam + ' ';
    queryParams.delete('foreach');

    const sections: DashboardSection[] = [];
    for (const [name, query] of queryParams) {
      if (!name || !query) continue;
      sections.push({name, query: `${forEachQuery}${query}`});
    }

    if (sections.length === 0) {
      this.redirect('/dashboard/self');
      return Promise.resolve();
    }

    const state: DashboardViewState = {
      view: GerritView.DASHBOARD,
      type: DashboardType.CUSTOM,
      user: 'self',
      sections,
      title,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.dashboardViewModel.setState(state);
    return Promise.resolve();
  }

  handleLegacyProjectDashboardRoute(ctx: PageContext) {
    this.redirect('/p/' + ctx.params[0] + '/+/dashboard/' + ctx.params[1]);
  }

  handleGroupInfoRoute(ctx: PageContext) {
    const groupId = ctx.params[0] as GroupId;
    this.redirect(createGroupUrl({groupId}));
  }

  handleGroupSelfRedirectRoute(_: PageContext) {
    this.redirect('/settings/#Groups');
  }

  handleGroupRoute(ctx: PageContext) {
    const state: GroupViewState = {
      view: GerritView.GROUP,
      groupId: ctx.params[0] as GroupId,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.groupViewModel.setState(state);
  }

  handleGroupAuditLogRoute(ctx: PageContext) {
    const state: GroupViewState = {
      view: GerritView.GROUP,
      detail: GroupDetailView.LOG,
      groupId: ctx.params[0] as GroupId,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.groupViewModel.setState(state);
  }

  handleGroupMembersRoute(ctx: PageContext) {
    const state: GroupViewState = {
      view: GerritView.GROUP,
      detail: GroupDetailView.MEMBERS,
      groupId: ctx.params[0] as GroupId,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.groupViewModel.setState(state);
  }

  handleGroupListRoute(ctx: PageContext) {
    const state: AdminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.GROUPS,
      offset: ctx.params[1] || '0',
      filter: ctx.params[0] ?? null,
      openCreateModal:
        !ctx.params[0] && !ctx.params[1] && ctx.hash === 'create',
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.adminViewModel.setState(state);
  }

  handleProjectsOldRoute(ctx: PageContext) {
    let params = '';
    if (ctx.params[1]) {
      params = encodeURIComponent(ctx.params[1]);
      if (ctx.params[1].includes(',')) {
        params = encodeURIComponent(ctx.params[1]).replace('%2C', ',');
      }
    }

    // TODO: Change the route pattern to match `repo` and `detailView`
    // separately, and then use `createRepoUrl()` here.
    this.redirect(`/admin/repos/${params}`);
  }

  handleRepoCommandsRoute(ctx: PageContext) {
    const repo = ctx.params[0] as RepoName;
    const state: RepoViewState = {
      view: GerritView.REPO,
      detail: RepoDetailView.COMMANDS,
      repo,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.repoViewModel.setState(state);
    this.reporting.setRepoName(repo);
  }

  handleRepoEditFileRoute(ctx: PageContext) {
    const repo = ctx.params[0] as RepoName;
    const branch = ctx.params[1] as BranchName;
    const path = ctx.params[2];
    const state: RepoViewState = {
      view: GerritView.REPO,
      detail: RepoDetailView.COMMANDS,
      repo,
      createEdit: {branch, path},
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.repoViewModel.setState(state);
    this.reporting.setRepoName(repo);
  }

  handleRepoGeneralRoute(ctx: PageContext) {
    const repo = ctx.params[0] as RepoName;
    const state: RepoViewState = {
      view: GerritView.REPO,
      detail: RepoDetailView.GENERAL,
      repo,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.repoViewModel.setState(state);
    this.reporting.setRepoName(repo);
  }

  handleRepoAccessRoute(ctx: PageContext) {
    const repo = ctx.params[0] as RepoName;
    const state: RepoViewState = {
      view: GerritView.REPO,
      detail: RepoDetailView.ACCESS,
      repo,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.repoViewModel.setState(state);
    this.reporting.setRepoName(repo);
  }

  handleRepoDashboardsRoute(ctx: PageContext) {
    const repo = ctx.params[0] as RepoName;
    const state: RepoViewState = {
      view: GerritView.REPO,
      detail: RepoDetailView.DASHBOARDS,
      repo,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.repoViewModel.setState(state);
    this.reporting.setRepoName(repo);
  }

  handleBranchListRoute(ctx: PageContext) {
    const state: RepoViewState = {
      view: GerritView.REPO,
      detail: RepoDetailView.BRANCHES,
      repo: ctx.params[0] as RepoName,
      offset: ctx.params[2] || '0',
      filter: ctx.params[1] ?? null,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.repoViewModel.setState(state);
  }

  handleTagListRoute(ctx: PageContext) {
    const state: RepoViewState = {
      view: GerritView.REPO,
      detail: RepoDetailView.TAGS,
      repo: ctx.params[0] as RepoName,
      offset: ctx.params[2] || '0',
      filter: ctx.params[1] ?? null,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.repoViewModel.setState(state);
  }

  handleRepoListRoute(ctx: PageContext) {
    const state: AdminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.REPOS,
      offset: ctx.params[1] || '0',
      filter: ctx.params[0] ?? null,
      openCreateModal:
        !ctx.params[0] && !ctx.params[1] && ctx.hash === 'create',
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.adminViewModel.setState(state);
  }

  handleCreateProjectRoute(_: PageContext) {
    // Redirects the legacy route to the new route, which displays the project
    // list with a hash 'create'.
    this.redirect('/admin/repos#create');
  }

  handleCreateGroupRoute(_: PageContext) {
    // Redirects the legacy route to the new route, which displays the group
    // list with a hash 'create'.
    this.redirect('/admin/groups#create');
  }

  handleRepoRoute(ctx: PageContext) {
    this.redirect(ctx.path + ',general');
  }

  handlePluginListFilterRoute(ctx: PageContext) {
    const state: AdminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.PLUGINS,
      offset: ctx.params[1] || '0',
      filter: ctx.params[0] ?? null,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.adminViewModel.setState(state);
  }

  handleQueryRoute(ctx: PageContext) {
    const state: SearchViewState = {
      view: GerritView.SEARCH,
      query: ctx.params[0],
      offset: ctx.params[2] || '0',
      loading: false,
    };
    // Note that router model view must be updated before view models.
    this.setState(state as AppElementParams);
    this.searchViewModel.updateState(state);
  }

  handleChangeIdQueryRoute(ctx: PageContext) {
    // TODO(pcc): This will need to indicate that this was a change ID query if
    // standard queries gain the ability to search places like commit messages
    // for change IDs.
    const state: SearchViewState = {
      view: GerritView.SEARCH,
      query: ctx.params[0],
      offset: '0',
      loading: false,
    };
    // Note that router model view must be updated before view models.
    this.setState(state as AppElementParams);
    this.searchViewModel.updateState(state);
  }

  handleQueryLegacySuffixRoute(ctx: PageContext) {
    this.redirect(ctx.path.replace(LEGACY_QUERY_SUFFIX_PATTERN, ''));
  }

  handleChangeNumberLegacyRoute(ctx: PageContext) {
    this.redirect(
      '/c/' +
        ctx.params[0] +
        (ctx.querystring.length > 0 ? `?${ctx.querystring}` : '')
    );
  }

  handleChangeRoute(ctx: PageContext) {
    // Parameter order is based on the regex group number matched.
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const state: ChangeViewState = {
      repo: ctx.params[0] as RepoName,
      changeNum,
      basePatchNum: convertToPatchSetNum(ctx.params[4]) as BasePatchSetNum,
      patchNum: convertToPatchSetNum(ctx.params[6]) as RevisionPatchSetNum,
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
    };

    const queryMap = new URLSearchParams(ctx.querystring);
    if (queryMap.has('openReplyDialog')) state.openReplyDialog = true;

    const tab = queryMap.get('tab');
    if (queryMap.has('forceReload')) state.forceReload = true;
    if (tab) state.tab = tab;
    const checksPatchset = Number(queryMap.get('checksPatchset'));
    if (Number.isInteger(checksPatchset) && checksPatchset > 0) {
      state.checksPatchset = checksPatchset as PatchSetNumber;
    }
    const filter = queryMap.get('filter');
    if (filter) state.filter = filter;
    const checksResultsFilter = queryMap.get('checksResultsFilter');
    if (checksResultsFilter) state.checksResultsFilter = checksResultsFilter;
    const attempt = stringToAttemptChoice(queryMap.get('attempt'));
    if (attempt && attempt !== LATEST_ATTEMPT) state.attempt = attempt;
    const selected = queryMap.get('checksRunsSelected');
    if (selected) state.checksRunsSelected = new Set(selected.split(','));

    assertIsDefined(state.repo, 'project');
    this.reporting.setRepoName(state.repo);
    this.reporting.setChangeId(changeNum);
    this.normalizePatchRangeParams(state);
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.changeViewModel.setState(state);
  }

  async handleCommentRoute(ctx: PageContext) {
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const repo = ctx.params[0] as RepoName;
    const commentId = ctx.params[2] as UrlEncodedCommentId;

    this.restApiService.addRepoNameToCache(changeNum, repo);
    const [comments, robotComments, drafts, change] = await Promise.all([
      this.restApiService.getDiffComments(changeNum),
      this.restApiService.getDiffRobotComments(changeNum),
      this.restApiService.getDiffDrafts(changeNum),
      this.restApiService.getChangeDetail(changeNum),
    ]);

    const comment =
      findComment(addPath(comments), commentId) ??
      findComment(addPath(robotComments), commentId) ??
      findComment(addPath(drafts), commentId);
    const path = comment?.path;
    const patchsets = computeAllPatchSets(change);
    const latestPatchNum = computeLatestPatchNum(patchsets);
    if (!comment || !path || !latestPatchNum) {
      this.show404();
      return;
    }
    let {basePatchNum, patchNum} = getPatchRangeForCommentUrl(
      comment,
      latestPatchNum
    );

    if (basePatchNum !== PARENT) {
      const diff = await this.restApiService.getDiff(
        changeNum,
        basePatchNum,
        patchNum,
        path
      );
      if (diff && isFileUnchanged(diff)) {
        fireAlert(
          document,
          `File is unchanged between Patchset ${basePatchNum} and ${patchNum}.
           Showing diff of Base vs ${basePatchNum}.`
        );
        patchNum = basePatchNum as RevisionPatchSetNum;
        basePatchNum = PARENT;
      }
    }

    const diffUrl = createDiffUrl({
      changeNum,
      repo,
      patchNum,
      basePatchNum,
      diffView: {
        path,
        lineNum: comment.line,
        leftSide: isInBaseOfPatchRange(comment, {basePatchNum, patchNum}),
      },
    });
    this.redirect(diffUrl);
  }

  handleCommentsRoute(ctx: PageContext) {
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const state: ChangeViewState = {
      repo: ctx.params[0] as RepoName,
      changeNum,
      commentId: ctx.params[2] as UrlEncodedCommentId,
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
    };
    const queryMap = new URLSearchParams(ctx.querystring);
    if (queryMap.has('forceReload')) state.forceReload = true;
    assertIsDefined(state.repo);
    this.reporting.setRepoName(state.repo);
    this.reporting.setChangeId(changeNum);
    this.normalizePatchRangeParams(state);
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.changeViewModel.setState(state);
  }

  handleDiffRoute(ctx: PageContext) {
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    // Parameter order is based on the regex group number matched.
    const state: ChangeViewState = {
      repo: ctx.params[0] as RepoName,
      changeNum,
      basePatchNum: convertToPatchSetNum(ctx.params[4]) as BasePatchSetNum,
      patchNum: convertToPatchSetNum(ctx.params[6]) as RevisionPatchSetNum,
      view: GerritView.CHANGE,
      childView: ChangeChildView.DIFF,
      diffView: {path: ctx.params[8]},
    };
    const queryMap = new URLSearchParams(ctx.querystring);
    const checksPatchset = Number(queryMap.get('checksPatchset'));
    if (Number.isInteger(checksPatchset) && checksPatchset > 0) {
      state.checksPatchset = checksPatchset as PatchSetNumber;
    }
    if (queryMap.has('forceReload')) state.forceReload = true;
    const address = this.parseLineAddress(ctx.hash);
    if (address) {
      state.diffView!.leftSide = address.leftSide;
      state.diffView!.lineNum = address.lineNum;
    }
    this.reporting.setRepoName(state.repo ?? '');
    this.reporting.setChangeId(changeNum);
    this.normalizePatchRangeParams(state);
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.changeViewModel.setState(state);
  }

  handleChangeLegacyRoute(ctx: PageContext) {
    const changeNum = Number(ctx.params[0]) as NumericChangeId;
    if (!changeNum) {
      this.show404();
      return;
    }
    this.restApiService.getRepoName(changeNum).then(project => {
      // Show a 404 and terminate if the lookup request failed. Attempting
      // to redirect after failing to get the project loops infinitely.
      if (!project) {
        this.show404();
        return;
      }
      this.redirect(
        `/c/${project}/+/${changeNum}/${ctx.params[1]}` +
          (ctx.querystring.length > 0 ? `?${ctx.querystring}` : '') +
          (ctx.hash.length > 0 ? `#${ctx.hash}` : '')
      );
    });
  }

  handleLegacyLinenum(ctx: PageContext) {
    this.redirect(ctx.path.replace(LEGACY_LINENUM_PATTERN, '#$1'));
  }

  handleDiffEditRoute(ctx: PageContext) {
    // Parameter order is based on the regex group number matched.
    const project = ctx.params[0] as RepoName;
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const state: ChangeViewState = {
      repo: project,
      changeNum,
      // for edit view params, patchNum cannot be undefined
      patchNum: convertToPatchSetNum(ctx.params[2]) as RevisionPatchSetNum,
      view: GerritView.CHANGE,
      childView: ChangeChildView.EDIT,
      editView: {path: ctx.params[3], lineNum: Number(ctx.hash)},
    };
    const queryMap = new URLSearchParams(ctx.querystring);
    if (queryMap.has('forceReload')) state.forceReload = true;
    this.normalizePatchRangeParams(state);
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.changeViewModel.setState(state);
    this.reporting.setRepoName(project);
    this.reporting.setChangeId(changeNum);
  }

  handleChangeEditRoute(ctx: PageContext) {
    // Parameter order is based on the regex group number matched.
    const project = ctx.params[0] as RepoName;
    const changeNum = Number(ctx.params[1]) as NumericChangeId;
    const queryMap = new URLSearchParams(ctx.querystring);
    const state: ChangeViewState = {
      repo: project,
      changeNum,
      patchNum: convertToPatchSetNum(ctx.params[3]) as RevisionPatchSetNum,
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      edit: true,
    };
    const tab = queryMap.get('tab');
    if (tab) state.tab = tab;
    if (queryMap.has('forceReload')) state.forceReload = true;
    this.normalizePatchRangeParams(state);
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.changeViewModel.setState(state);
    this.reporting.setRepoName(project);
    this.reporting.setChangeId(changeNum);
  }

  handleAgreementsRoute() {
    this.redirect('/settings/#Agreements');
  }

  handleNewAgreementsRoute() {
    const state: AgreementViewState = {
      view: GerritView.AGREEMENTS,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.agreementViewModel.setState(state);
  }

  handleSettingsLegacyRoute(ctx: PageContext) {
    // email tokens may contain '+' but no space.
    // The parameter parsing replaces all '+' with a space,
    // undo that to have valid tokens.
    const token = ctx.params[0].replace(/ /g, '+');
    const state: SettingsViewState = {
      view: GerritView.SETTINGS,
      emailToken: token,
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.settingsViewModel.setState(state);
  }

  handleSettingsRoute(_: PageContext) {
    const state: SettingsViewState = {view: GerritView.SETTINGS};
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.settingsViewModel.setState(state);
  }

  handleRegisterRoute(ctx: PageContext) {
    this.setState({justRegistered: true});
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
   * URL may sometimes have /+/ encoded to / /.
   * Context: Issue 6888, Issue 7100
   */
  handleImproperlyEncodedPlusRoute(ctx: PageContext) {
    let hash = this.getHashFromCanonicalPath(ctx.canonicalPath);
    if (hash.length) {
      hash = '#' + hash;
    }
    this.redirect(`/c/${ctx.params[0]}/+/${ctx.params[1]}${hash}`);
  }

  handlePluginScreen(ctx: PageContext) {
    const state: PluginViewState = {
      view: GerritView.PLUGIN_SCREEN,
      plugin: ctx.params[0],
      screen: ctx.params[1],
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.pluginViewModel.setState(state);
  }

  handleDocumentationSearchRoute(ctx: PageContext) {
    const state: DocumentationViewState = {
      view: GerritView.DOCUMENTATION_SEARCH,
      filter: ctx.params[0] ?? '',
    };
    // Note that router model view must be updated before view models.
    this.setState(state);
    this.documentationViewModel.setState(state);
  }

  handleDocumentationSearchRedirectRoute(ctx: PageContext) {
    this.redirect('/Documentation/q/filter:' + encodeURL(ctx.params[0]));
  }

  handleDocumentationRedirectRoute(ctx: PageContext) {
    if (ctx.params[1]) {
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
      this.windowReload();
    }
  }

  // Allows stubbing in tests.
  windowReload() {
    windowLocationReload();
  }

  private show404() {
    // Note: the app's 404 display is tightly-coupled with catching 404
    // network responses, so we simulate a 404 response status to display it.
    // TODO: Decouple the gr-app error view from network responses.
    firePageError(new Response('', {status: 404}));
  }
}
