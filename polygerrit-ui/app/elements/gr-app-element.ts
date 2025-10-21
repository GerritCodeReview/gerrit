/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../styles/themes/app-theme';
import '../styles/themes/dark-theme';
import {
  applyTheme as applyDarkTheme,
  removeTheme as removeDarkTheme,
} from '../styles/themes/dark-theme';
import './admin/gr-admin-view/gr-admin-view';
import './documentation/gr-documentation-search/gr-documentation-search';
import './change-list/gr-change-list-view/gr-change-list-view';
import './change-list/gr-dashboard-view/gr-dashboard-view';
import './change/gr-change-view/gr-change-view';
import './core/gr-error-manager/gr-error-manager';
import './core/gr-keyboard-shortcuts-dialog/gr-keyboard-shortcuts-dialog';
import './core/gr-main-header/gr-main-header';
import './diff/gr-diff-view/gr-diff-view';
import './edit/gr-editor-view/gr-editor-view';
import './plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import './plugins/gr-plugin-host/gr-plugin-host';
import './plugins/gr-plugin-screen/gr-plugin-screen';
import './settings/gr-cla-view/gr-cla-view';
import './settings/gr-registration-dialog/gr-registration-dialog';
import './settings/gr-settings-view/gr-settings-view';
import {navigationToken} from './core/gr-navigation/gr-navigation';
import {getAppContext} from '../services/app-context';
import {routerToken} from './core/gr-router/gr-router';
import {AccountDetailInfo, NumericChangeId, ServerInfo} from '../types/common';
import {
  constructServerErrorMsg,
  GrErrorManager,
} from './core/gr-error-manager/gr-error-manager';
import {GrRegistrationDialog} from './settings/gr-registration-dialog/gr-registration-dialog';
import {
  AppElementJustRegisteredParams,
  AppElementParams,
  isAppElementJustRegisteredParams,
} from './gr-app-types';
import {GrMainHeader} from './core/gr-main-header/gr-main-header';
import {GrSettingsView} from './settings/gr-settings-view/gr-settings-view';
import {
  PageErrorEventDetail,
  RpcLogEvent,
  TitleChangeEventDetail,
} from '../types/events';
import {GerritView, routerModelToken} from '../services/router/router-model';
import {LifeCycle} from '../constants/reporting';
import {fireIronAnnounce} from '../utils/event-util';
import {resolve} from '../models/dependency';
import {browserModelToken} from '../models/browser/browser-model';
import {sharedStyles} from '../styles/shared-styles';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {Shortcut, ShortcutController} from './lit/shortcut-controller';
import {cache} from 'lit/directives/cache.js';
import {keyed} from 'lit/directives/keyed.js';
import {assertIsDefined} from '../utils/common-util';
import './gr-css-mixins';
import {isDarkTheme, prefersDarkColorScheme} from '../utils/theme-util';
import {AppTheme} from '../constants/constants';
import {subscribe} from './lit/subscription-controller';
import {
  createSearchUrl,
  searchViewModelToken,
  SearchViewState,
} from '../models/views/search';
import {createSettingsUrl} from '../models/views/settings';
import {createDashboardUrl, DashboardType} from '../models/views/dashboard';
import {userModelToken} from '../models/user/user-model';
import {modalStyles} from '../styles/gr-modal-styles';
import {AdminChildView, createAdminUrl} from '../models/views/admin';
import {ChangeChildView, changeViewModelToken} from '../models/views/change';
import {
  ALLOW_LISTED_FULL_SCREEN_PLUGINS,
  pluginViewModelToken,
} from '../models/views/plugin';

interface ErrorInfo {
  text: string;
  emoji?: string;
  moreInfo?: string;
}

// TODO(TS): implement AppElement interface from gr-app-types.ts
@customElement('gr-app-element')
export class GrAppElement extends LitElement {
  /**
   * Fired when the URL location changes.
   *
   * @event location-change
   */

  @query('#errorManager') errorManager?: GrErrorManager;

  @query('#errorView') errorView?: HTMLDivElement;

  @query('#mainHeader') mainHeader?: GrMainHeader;

  @query('#registrationModal') registrationModal?: HTMLDialogElement;

  @query('#registrationDialog') registrationDialog?: GrRegistrationDialog;

  @query('#keyboardShortcuts') keyboardShortcuts?: HTMLDialogElement;

  @query('gr-settings-view') settingsView?: GrSettingsView;

  @property({type: Object})
  params?: AppElementParams;

  @state() private account?: AccountDetailInfo;

  @state() private version?: string;

  @state() view?: GerritView;

  // TODO: Introduce a wrapper element for CHANGE, DIFF, EDIT view.
  @state() private childView?: ChangeChildView;

  // Used as a key for caching the CHANGE, DIFF, EDIT view.
  @state() private changeNum?: NumericChangeId;

  @state() private lastError?: ErrorInfo;

  // private but used in test
  @state() lastSearchPage?: string;

  @state() private settingsUrl?: string;

  @state() private loadRegistrationDialog = false;

  @state() private loadKeyboardShortcutsDialog = false;

  @state() private theme = AppTheme.AUTO;

  @state() private pluginScreenName = '';

  @state() serverConfig?: ServerInfo;

  readonly getRouter = resolve(this, routerToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly shortcuts = new ShortcutController(this);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getRouterModel = resolve(this, routerModelToken);

  private readonly getChangeViewModel = resolve(this, changeViewModelToken);

  private readonly getPluginViewModel = resolve(this, pluginViewModelToken);

  private readonly getSearchViewModel = resolve(this, searchViewModelToken);

  constructor() {
    super();

    document.addEventListener('page-error', e => {
      this.handlePageError(e);
    });
    document.addEventListener('title-change', e => {
      this.handleTitleChange(e);
    });
    document.addEventListener('location-change', () => this.requestUpdate());
    document.addEventListener('gr-rpc-log', e => this.handleRpcLog(e));
    this.shortcuts.addAbstract(Shortcut.OPEN_SHORTCUT_HELP_DIALOG, () =>
      this.showKeyboardShortcuts()
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_USER_DASHBOARD, () =>
      this.getNavigation().setUrl(
        createDashboardUrl({type: DashboardType.USER, user: 'self'})
      )
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_OPENED_CHANGES, () =>
      this.getNavigation().setUrl(createSearchUrl({statuses: ['open']}))
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_MERGED_CHANGES, () =>
      this.getNavigation().setUrl(createSearchUrl({statuses: ['merged']}))
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_ABANDONED_CHANGES, () =>
      this.getNavigation().setUrl(createSearchUrl({statuses: ['abandoned']}))
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_WATCHED_CHANGES, () =>
      this.getNavigation().setUrl(
        createSearchUrl({query: 'is:watched is:open'})
      )
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_REPOS, () =>
      this.getNavigation().setUrl(
        createAdminUrl({adminView: AdminChildView.REPOS})
      )
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_GROUPS, () =>
      this.getNavigation().setUrl(
        createAdminUrl({adminView: AdminChildView.GROUPS})
      )
    );

    subscribe(
      this,
      () => this.getUserModel().preferenceTheme$,
      theme => {
        this.theme = theme;
        this.applyTheme();
      }
    );
    subscribe(
      this,
      () => this.getRouterModel().routerView$,
      view => {
        this.view = view;
        if (view) this.errorView?.classList.remove('show');
      }
    );
    subscribe(
      this,
      () => this.getPluginViewModel().screenName$,
      screenName => (this.pluginScreenName = screenName)
    );
    subscribe(
      this,
      () => this.getChangeViewModel().childView$,
      childView => (this.childView = childView)
    );
    subscribe(
      this,
      () => this.getChangeViewModel().changeNum$,
      changeNum => {
        this.changeNum = changeNum;
      }
    );

    prefersDarkColorScheme().addEventListener('change', () => {
      if (this.theme === AppTheme.AUTO) {
        this.applyTheme();
      }
    });
  }

  override connectedCallback() {
    super.connectedCallback();
    const resizeObserver = this.getBrowserModel().observeWidth();
    resizeObserver.observe(this);

    this.reporting.appStarted();
    this.getRouter().start();

    this.restApiService.getAccount().then(account => {
      this.account = account;
      if (account) {
        this.reporting.reportLifeCycle(LifeCycle.STARTED_AS_USER);
      } else {
        this.reporting.reportLifeCycle(LifeCycle.STARTED_AS_GUEST);
      }
    });
    this.restApiService.getVersion().then(version => {
      this.version = version;
      this.logWelcome();
    });

    // Note: this is evaluated here to ensure that it only happens after the
    // router has been initialized. @see Issue 7837
    this.settingsUrl = createSettingsUrl();
  }

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        :host {
          background-color: var(--background-color-secondary);
          display: flex;
          flex-direction: column;
          min-height: 100%;
        }
        gr-main-header,
        footer {
          color: var(--primary-text-color);
        }
        gr-main-header {
          background: var(
            --header-background,
            var(--header-background-color, #eee)
          );
          /* Make sure the header is above the main content, to preserve
            box-shadow visibility. We need 111 here 1, because dropdowns in the
            header should be shown on top of the sticky diff header, which has a
            z-index of 110. */
          z-index: 111;
          position: sticky;
          top: 0;
          height: var(--main-header-height);
        }
        footer {
          background: var(
            --footer-background,
            var(--footer-background-color, #eee)
          );
          height: var(--main-footer-height);
          border-top: var(--footer-border-top);
          display: flex;
          justify-content: space-between;
          padding: var(--spacing-m) var(--spacing-l);
          z-index: 10;
        }
        main {
          flex: 1;
          padding-bottom: var(--spacing-xxl);
          position: relative;
        }
        .errorView {
          align-items: center;
          display: none;
          flex-direction: column;
          justify-content: center;
          position: absolute;
          top: 0;
          right: 0;
          bottom: 0;
          left: 0;
        }
        .errorView.show {
          display: flex;
        }
        .errorEmoji {
          font-size: 2.6rem;
        }
        .errorText,
        .errorMoreInfo {
          margin-top: var(--spacing-m);
        }
        .errorText {
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
        }
        .errorMoreInfo {
          color: var(--deemphasized-text-color);
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-css-mixins></gr-css-mixins>
      <gr-endpoint-decorator name="banner"></gr-endpoint-decorator>
      ${this.renderHeader()}
      <main>
        ${this.renderChangeListView()} ${this.renderDashboardView()}
        ${
          // `keyed(this.changeNum, ...)` makes sure that these views are not
          // re-used across changes, which is a precaution, because we have run
          // into issue with that. That could be re-considered at some point.
          keyed(
            this.changeNum,
            html`
              ${this.renderChangeView()} ${this.renderEditorView()}
              ${this.renderDiffView()}
            `
          )
        }
        ${this.renderSettingsView()} ${this.renderAdminView()}
        ${this.renderPluginScreen()} ${this.renderCLAView()}
        ${this.renderDocumentationSearch()}
        <div id="errorView" class="errorView">
          <div class="errorEmoji">${this.lastError?.emoji}</div>
          <div class="errorText">${this.lastError?.text}</div>
          <div class="errorMoreInfo">${this.lastError?.moreInfo}</div>
        </div>
      </main>
      ${this.renderFooter()} ${this.renderKeyboardShortcutsDialog()}
      ${this.renderRegistrationDialog()}
      <gr-endpoint-decorator name="plugin-overlay"></gr-endpoint-decorator>
      <gr-error-manager id="errorManager"></gr-error-manager>
      <gr-plugin-host id="plugins"></gr-plugin-host>
    `;
  }

  private renderHeader() {
    if (this.hideHeaderAndFooter()) return nothing;
    return html`
      <gr-main-header
        id="mainHeader"
        @show-keyboard-shortcuts=${this.showKeyboardShortcuts}
      >
      </gr-main-header>
    `;
  }

  private renderFooter() {
    if (this.hideHeaderAndFooter()) return nothing;
    return html`
      <footer>
        <div>
          Powered by
          <a
            href="https://www.gerritcodereview.com/"
            rel="noopener noreferrer"
            target="_blank"
            >Gerrit Code Review</a
          >
          (${this.version})
          <gr-endpoint-decorator name="footer-left"></gr-endpoint-decorator>
        </div>
        <div>
          Press “?” for keyboard shortcuts
          <gr-endpoint-decorator name="footer-right"></gr-endpoint-decorator>
        </div>
      </footer>
    `;
  }

  private hideHeaderAndFooter() {
    return (
      this.view === GerritView.PLUGIN_SCREEN &&
      ALLOW_LISTED_FULL_SCREEN_PLUGINS.includes(this.pluginScreenName)
    );
  }

  private renderChangeListView() {
    return cache(
      this.view === GerritView.SEARCH
        ? html` <gr-change-list-view></gr-change-list-view> `
        : nothing
    );
  }

  private renderDashboardView() {
    return cache(
      this.view === GerritView.DASHBOARD
        ? html`<gr-dashboard-view></gr-dashboard-view>`
        : nothing
    );
  }

  private renderChangeView() {
    // The `cache()` is required for re-using the change view when switching
    // back and forth between change, diff and editor views.
    return cache(
      this.isChangeView()
        ? html`<gr-change-view
            .backPage=${this.lastSearchPage}
          ></gr-change-view>`
        : nothing
    );
  }

  private isChangeView() {
    return (
      this.view === GerritView.CHANGE &&
      this.childView === ChangeChildView.OVERVIEW
    );
  }

  private renderEditorView() {
    // For some reason caching the editor view caused an issue (b/269308770).
    // We did not bother to root cause that issue, but instead let's forgo
    // caching of the editor view. It does not help much anyway.
    return this.isEditorView()
      ? html`<gr-editor-view></gr-editor-view>`
      : nothing;
  }

  private isEditorView() {
    return (
      this.view === GerritView.CHANGE && this.childView === ChangeChildView.EDIT
    );
  }

  private renderDiffView() {
    // The `cache()` is required for re-using the diff view when switching
    // back and forth between change, diff and editor views.
    return cache(
      this.isDiffView() ? html`<gr-diff-view></gr-diff-view>` : nothing
    );
  }

  private isDiffView() {
    return (
      this.view === GerritView.CHANGE && this.childView === ChangeChildView.DIFF
    );
  }

  private renderSettingsView() {
    if (this.view !== GerritView.SETTINGS) return nothing;
    return html`
      <gr-settings-view
        @account-detail-update=${this.handleAccountDetailUpdate}
      >
      </gr-settings-view>
    `;
  }

  private renderAdminView() {
    if (
      this.view !== GerritView.ADMIN &&
      this.view !== GerritView.GROUP &&
      this.view !== GerritView.REPO
    )
      return nothing;
    return html`<gr-admin-view></gr-admin-view>`;
  }

  private renderPluginScreen() {
    if (this.view !== GerritView.PLUGIN_SCREEN) return nothing;
    return html`<gr-plugin-screen></gr-plugin-screen>`;
  }

  private renderCLAView() {
    if (this.view !== GerritView.AGREEMENTS) return nothing;
    return html`<gr-cla-view></gr-cla-view>`;
  }

  private renderDocumentationSearch() {
    if (this.view !== GerritView.DOCUMENTATION_SEARCH) return nothing;
    return html`<gr-documentation-search></gr-documentation-search>`;
  }

  private renderKeyboardShortcutsDialog() {
    if (!this.loadKeyboardShortcutsDialog) return nothing;
    return html`
      <dialog id="keyboardShortcuts" tabindex="-1">
        <gr-keyboard-shortcuts-dialog
          @close=${this.handleKeyboardShortcutDialogClose}
        ></gr-keyboard-shortcuts-dialog>
      </dialog>
    `;
  }

  private renderRegistrationDialog() {
    if (!this.loadRegistrationDialog) return nothing;
    return html`
      <dialog id="registrationModal" tabindex="-1">
        <gr-registration-dialog
          id="registrationDialog"
          .settingsUrl=${this.settingsUrl}
          @account-detail-update=${this.handleAccountDetailUpdate}
          @close=${this.handleRegistrationDialogClose}
        >
        </gr-registration-dialog>
      </dialog>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('account')) {
      this.accountChanged();
    }

    if (changedProperties.has('params')) {
      this.viewChanged();
      this.paramsChanged();
    }
  }

  private accountChanged() {
    if (!this.account) return;

    // Preferences are cached when a user is logged in; warm them.
    this.restApiService.getPreferences();
    this.restApiService.getDiffPreferences();
    this.restApiService.getEditPreferences();
    if (this.errorManager)
      this.errorManager.knownAccountId =
        (this.account && this.account._account_id) || null;
  }

  private async viewChanged() {
    if (
      this.params &&
      isAppElementJustRegisteredParams(this.params) &&
      this.params.justRegistered
    ) {
      this.loadRegistrationDialog = true;
      await this.updateComplete;
      assertIsDefined(this.registrationModal, 'registrationModal');
      assertIsDefined(this.registrationDialog, 'registrationDialog');
      this.registrationModal.showModal();
      await this.registrationDialog.loadData();
    }
    // To fix bug announce read after each new view, we reset announce with
    // empty space
    fireIronAnnounce(this, ' ');
  }

  private applyTheme() {
    const showDarkTheme = isDarkTheme(this.theme);
    document.documentElement.classList.toggle('darkTheme', showDarkTheme);
    document.documentElement.classList.toggle('lightTheme', !showDarkTheme);
    // TODO: Remove this code for adding/removing dark theme style. We should
    // be able to just always add them once we have changed its css selector
    // from `html` to `html.darkTheme`.
    if (showDarkTheme) {
      applyDarkTheme();
    } else {
      removeDarkTheme();
    }
  }

  private handlePageError(e: CustomEvent<PageErrorEventDetail>) {
    this.view = undefined;
    this.errorView?.classList.add('show');
    const response = e.detail.response;
    const err: ErrorInfo = {
      text: [response?.status, response?.statusText].join(' '),
    };
    if (response?.status === 404) {
      err.emoji = '¯\\_(ツ)_/¯';
      this.lastError = err;
    } else {
      err.emoji = 'o_O';
      if (response) {
        response.text().then(text => {
          const trace =
            response.headers && response.headers.get('X-Gerrit-Trace');
          const {status, statusText} = response;
          err.moreInfo = constructServerErrorMsg({
            status,
            statusText,
            errorText: text,
            trace,
          });
          this.lastError = err;
        });
      }
    }
  }

  // private but used in test
  paramsChanged() {
    const viewsToCheck = [GerritView.SEARCH, GerritView.DASHBOARD];
    if (this.params?.view && viewsToCheck.includes(this.params.view)) {
      this.lastSearchPage = location.pathname;
    }

    // Clear search queries if you leave the search page.
    if (this.params?.view !== GerritView.SEARCH) {
      const state: SearchViewState = {
        view: GerritView.SEARCH,
        query: '',
        offset: '',
        loading: false,
      };
      this.getSearchViewModel().updateState(state);
    }
  }

  private handleTitleChange(e: CustomEvent<TitleChangeEventDetail>) {
    if (e.detail.title) {
      document.title = e.detail.title + ' · Gerrit Code Review';
    } else {
      document.title = '';
    }
  }

  private async showKeyboardShortcuts() {
    this.loadKeyboardShortcutsDialog = true;
    await this.updateComplete;
    assertIsDefined(this.keyboardShortcuts, 'keyboardShortcuts');

    if (this.keyboardShortcuts.hasAttribute('open')) {
      this.keyboardShortcuts.close();
      return;
    }
    this.keyboardShortcuts.showModal();
  }

  private handleKeyboardShortcutDialogClose() {
    assertIsDefined(this.keyboardShortcuts, 'keyboardShortcuts');
    this.keyboardShortcuts.close();
  }

  private handleAccountDetailUpdate() {
    this.mainHeader?.reload();
    this.settingsView?.reloadAccountDetail();
  }

  private handleRegistrationDialogClose() {
    // The registration dialog is visible only if this.params is
    // instanceof AppElementJustRegisteredParams
    (this.params as AppElementJustRegisteredParams).justRegistered = false;
    assertIsDefined(this.registrationModal, 'registrationModal');
    this.registrationModal.close();
  }

  private logWelcome() {
    console.group('Runtime Info');
    console.info('Gerrit UI (PolyGerrit)');
    console.info(`Gerrit Server Version: ${this.version}`);
    if (window.VERSION_INFO) {
      console.info(`UI Version Info: ${window.VERSION_INFO}`);
    }
    console.groupEnd();
  }

  /**
   * Intercept RPC log events emitted by REST API interfaces.
   * Note: the REST API interface cannot use gr-reporting directly because
   * that would create a cyclic dependency.
   */
  private handleRpcLog(e: RpcLogEvent) {
    this.reporting.reportRpcTiming(e.detail.anonymizedUrl, e.detail.elapsed);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app-element': GrAppElement;
  }
}
