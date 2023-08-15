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
import './core/gr-smart-search/gr-smart-search';
import './diff/gr-diff-view/gr-diff-view';
import './edit/gr-editor-view/gr-editor-view';
import './plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import './plugins/gr-endpoint-param/gr-endpoint-param';
import './plugins/gr-endpoint-slot/gr-endpoint-slot';
import './plugins/gr-external-style/gr-external-style';
import './plugins/gr-plugin-host/gr-plugin-host';
import './settings/gr-cla-view/gr-cla-view';
import './settings/gr-registration-dialog/gr-registration-dialog';
import './settings/gr-settings-view/gr-settings-view';
import {navigationToken} from './core/gr-navigation/gr-navigation';
import {loginUrl} from '../utils/url-util';
import {getAppContext} from '../services/app-context';
import {routerToken} from './core/gr-router/gr-router';
import {AccountDetailInfo, ServerInfo} from '../types/common';
import {
  constructServerErrorMsg,
  GrErrorManager,
} from './core/gr-error-manager/gr-error-manager';
import {GrOverlay} from './shared/gr-overlay/gr-overlay';
import {GrRegistrationDialog} from './settings/gr-registration-dialog/gr-registration-dialog';
import {
  AppElementJustRegisteredParams,
  AppElementParams,
  isAppElementJustRegisteredParams,
} from './gr-app-types';
import {GrMainHeader} from './core/gr-main-header/gr-main-header';
import {GrSettingsView} from './settings/gr-settings-view/gr-settings-view';
import {
  DialogChangeEventDetail,
  EventType,
  PageErrorEventDetail,
  RpcLogEvent,
  TitleChangeEventDetail,
} from '../types/events';
import {GerritView} from '../services/router/router-model';
import {LifeCycle} from '../constants/reporting';
import {fireIronAnnounce} from '../utils/event-util';
import {resolve} from '../models/dependency';
import {browserModelToken} from '../models/browser/browser-model';
import {configModelToken} from '../models/config/config-model';
import {sharedStyles} from '../styles/shared-styles';
import {LitElement, PropertyValues, html, css, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {Shortcut, ShortcutController} from './lit/shortcut-controller';
import {cache} from 'lit/directives/cache.js';
import {keyed} from 'lit/directives/keyed.js';
import {assertIsDefined} from '../utils/common-util';
import './gr-css-mixins';
import {isDarkTheme, prefersDarkColorScheme} from '../utils/theme-util';
import {AppTheme} from '../constants/constants';
import {subscribe} from './lit/subscription-controller';
import {PluginViewState} from '../models/views/plugin';
import {createSearchUrl, SearchViewState} from '../models/views/search';
import {createSettingsUrl} from '../models/views/settings';
import {createDashboardUrl} from '../models/views/dashboard';

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

  @query('#registrationOverlay') registrationOverlay?: GrOverlay;

  @query('#registrationDialog') registrationDialog?: GrRegistrationDialog;

  @query('#keyboardShortcuts') keyboardShortcuts?: GrOverlay;

  @query('gr-settings-view') settingsView?: GrSettingsView;

  @property({type: Object})
  params?: AppElementParams;

  @state() private account?: AccountDetailInfo;

  @state() private serverConfig?: ServerInfo;

  @state() private version?: string;

  @state() private view?: GerritView;

  @state() private lastError?: ErrorInfo;

  // private but used in test
  @state() lastSearchPage?: string;

  @state() private settingsUrl?: string;

  @state() private mobileSearch = false;

  @state() private loadRegistrationDialog = false;

  @state() private loadKeyboardShortcutsDialog = false;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes footer, header from a11y tree, when a dialog on view
  // (e.g. reply dialog) is open
  @state() private footerHeaderAriaHidden = false;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes main page from a11y tree, when a dialog on gr-app-element
  // (e.g. shortcut dialog) is open
  @state() private mainAriaHidden = false;

  // Triggers dom-if unsetting/setting restamp behaviour in lit
  @state() private invalidateChangeViewCache = false;

  // Triggers dom-if unsetting/setting restamp behaviour in lit
  @state() private invalidateDiffViewCache = false;

  @state() private theme = AppTheme.AUTO;

  @state() private themeEndpoint = 'app-theme-light';

  readonly getRouter = resolve(this, routerToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly shortcuts = new ShortcutController(this);

  private readonly userModel = getAppContext().userModel;

  private readonly routerModel = getAppContext().routerModel;

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();

    document.addEventListener(EventType.PAGE_ERROR, e => {
      this.handlePageError(e);
    });
    this.addEventListener(EventType.TITLE_CHANGE, e => {
      this.handleTitleChange(e);
    });
    this.addEventListener(EventType.DIALOG_CHANGE, e => {
      this.handleDialogChange(e as CustomEvent<DialogChangeEventDetail>);
    });
    document.addEventListener(EventType.LOCATION_CHANGE, () =>
      this.requestUpdate()
    );
    this.addEventListener(EventType.RECREATE_CHANGE_VIEW, () =>
      this.handleRecreateView()
    );
    this.addEventListener(EventType.RECREATE_DIFF_VIEW, () =>
      this.handleRecreateView()
    );
    document.addEventListener(EventType.GR_RPC_LOG, e => this.handleRpcLog(e));
    this.shortcuts.addAbstract(Shortcut.OPEN_SHORTCUT_HELP_DIALOG, () =>
      this.showKeyboardShortcuts()
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_USER_DASHBOARD, () =>
      this.getNavigation().setUrl(createDashboardUrl({user: 'self'}))
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

    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
      }
    );

    subscribe(
      this,
      () => this.userModel.preferenceTheme$,
      theme => {
        this.theme = theme;
        this.applyTheme();
      }
    );
    subscribe(
      this,
      () => this.routerModel.routerView$,
      view => {
        this.view = view;
        if (view) this.errorView?.classList.remove('show');
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
      css`
        :host {
          background-color: var(--background-color-tertiary);
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
          padding: var(--header-padding);
          border-bottom: var(--header-border-bottom);
          border-image: var(--header-border-image);
          border-right: 0;
          border-left: 0;
          border-top: 0;
          box-shadow: var(--header-box-shadow);
          /* Make sure the header is above the main content, to preserve box-shadow
            visibility. We need 2 here instead of 1, because dropdowns in the
            header should be shown on top of the sticky diff header, which has a
            z-index of 1. */
          z-index: 2;
        }
        footer {
          background: var(
            --footer-background,
            var(--footer-background-color, #eee)
          );
          border-top: var(--footer-border-top);
          display: flex;
          justify-content: space-between;
          padding: var(--spacing-m) var(--spacing-l);
          z-index: 100;
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
      <gr-main-header
        id="mainHeader"
        .searchQuery=${(this.params as SearchViewState)?.query}
        @mobile-search=${this.mobileSearchToggle}
        @show-keyboard-shortcuts=${this.showKeyboardShortcuts}
        .mobileSearchHidden=${!this.mobileSearch}
        .loginUrl=${loginUrl(this.serverConfig?.auth)}
        .loginText=${this.serverConfig?.auth.login_text ?? 'Sign in'}
        ?aria-hidden=${this.footerHeaderAriaHidden}
      >
      </gr-main-header>
      <main ?aria-hidden=${this.mainAriaHidden}>
        ${this.renderMobileSearch()} ${this.renderChangeListView()}
        ${this.renderDashboardView()} ${this.renderChangeView()}
        ${this.renderEditorView()} ${this.renderDiffView()}
        ${this.renderSettingsView()} ${this.renderAdminView()}
        ${this.renderPluginScreen()} ${this.renderCLAView()}
        ${this.renderDocumentationSearch()}
        <div id="errorView" class="errorView">
          <div class="errorEmoji">${this.lastError?.emoji}</div>
          <div class="errorText">${this.lastError?.text}</div>
          <div class="errorMoreInfo">${this.lastError?.moreInfo}</div>
        </div>
      </main>
      <footer ?aria-hidden=${this.footerHeaderAriaHidden}>
        <div>
          Powered by
          <a
            href="https://www.gerritcodereview.com/"
            rel="noopener"
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
      ${this.renderKeyboardShortcutsDialog()} ${this.renderRegistrationDialog()}
      <gr-endpoint-decorator name="plugin-overlay"></gr-endpoint-decorator>
      <gr-error-manager
        id="errorManager"
        .loginUrl=${loginUrl(this.serverConfig?.auth)}
        .loginText=${this.serverConfig?.auth.login_text ?? 'Sign in'}
      ></gr-error-manager>
      <gr-plugin-host id="plugins"></gr-plugin-host>
      <gr-external-style
        id="externalStyleForAll"
        name="app-theme"
      ></gr-external-style>
      <gr-external-style
        id="externalStyleForTheme"
        name=${this.themeEndpoint}
      ></gr-external-style>
    `;
  }

  private renderMobileSearch() {
    if (!this.mobileSearch) return nothing;
    return html`
      <gr-smart-search
        id="search"
        label="Search for changes"
        .searchQuery=${(this.params as SearchViewState)?.query}
      >
      </gr-smart-search>
    `;
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
    if (this.invalidateChangeViewCache) {
      this.updateComplete.then(() => (this.invalidateChangeViewCache = false));
      return nothing;
    }
    return cache(
      this.view === GerritView.CHANGE ? this.changeViewTemplate() : nothing
    );
  }

  // Template as not to create duplicates, for renderChangeView() only.
  private changeViewTemplate() {
    return html`
      <gr-change-view .backPage=${this.lastSearchPage}></gr-change-view>
    `;
  }

  private renderEditorView() {
    if (this.view !== GerritView.EDIT) return nothing;
    return html`<gr-editor-view></gr-editor-view>`;
  }

  private renderDiffView() {
    if (this.invalidateDiffViewCache) {
      this.updateComplete.then(() => (this.invalidateDiffViewCache = false));
      return nothing;
    }
    return cache(
      this.view === GerritView.DIFF ? this.diffViewTemplate() : nothing
    );
  }

  private diffViewTemplate() {
    return html`<gr-diff-view></gr-diff-view>`;
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
    const pluginViewState = this.params as PluginViewState;
    const pluginScreenName = this.computePluginScreenName();
    return keyed(
      pluginScreenName,
      html`
        <gr-endpoint-decorator .name=${pluginScreenName}>
          <gr-endpoint-param
            name="token"
            .value=${pluginViewState.screen}
          ></gr-endpoint-param>
        </gr-endpoint-decorator>
      `
    );
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
      <gr-overlay
        id="keyboardShortcuts"
        with-backdrop=""
        @iron-overlay-canceled=${this.onOverlayCanceled}
      >
        <gr-keyboard-shortcuts-dialog
          @close=${this.handleKeyboardShortcutDialogClose}
        ></gr-keyboard-shortcuts-dialog>
      </gr-overlay>
    `;
  }

  private renderRegistrationDialog() {
    if (!this.loadRegistrationDialog) return nothing;
    return html`
      <gr-overlay id="registrationOverlay" with-backdrop="">
        <gr-registration-dialog
          id="registrationDialog"
          .settingsUrl=${this.settingsUrl}
          @account-detail-update=${this.handleAccountDetailUpdate}
          @close=${this.handleRegistrationDialogClose}
        >
        </gr-registration-dialog>
      </gr-overlay>
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

  /**
   * Throws away the view and re-creates it. The view itself fires an event, if
   * it wants to be re-created.
   */
  private handleRecreateView() {
    this.invalidateChangeViewCache = true;
    this.invalidateDiffViewCache = true;
  }

  private async viewChanged() {
    if (
      this.params &&
      isAppElementJustRegisteredParams(this.params) &&
      this.params.justRegistered
    ) {
      this.loadRegistrationDialog = true;
      await this.updateComplete;
      assertIsDefined(this.registrationOverlay, 'registrationOverlay');
      assertIsDefined(this.registrationDialog, 'registrationDialog');
      await this.registrationOverlay.open();
      await this.registrationDialog.loadData().then(() => {
        this.registrationOverlay!.refit();
      });
    }
    // To fix bug announce read after each new view, we reset announce with
    // empty space
    fireIronAnnounce(this, ' ');
  }

  private applyTheme() {
    const showDarkTheme = isDarkTheme(this.theme);
    document.documentElement.classList.toggle('darkTheme', showDarkTheme);
    document.documentElement.classList.toggle('lightTheme', !showDarkTheme);
    if (showDarkTheme) {
      this.themeEndpoint = 'app-theme-dark';
      applyDarkTheme();
    } else {
      this.themeEndpoint = 'app-theme-light';
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
  }

  private handleTitleChange(e: CustomEvent<TitleChangeEventDetail>) {
    if (e.detail.title) {
      document.title = e.detail.title + ' · Gerrit Code Review';
    } else {
      document.title = '';
    }
  }

  private handleDialogChange(e: CustomEvent<DialogChangeEventDetail>) {
    if (e.detail.canceled) {
      this.footerHeaderAriaHidden = false;
    } else if (e.detail.opened) {
      this.footerHeaderAriaHidden = true;
    }
  }

  private async showKeyboardShortcuts() {
    this.loadKeyboardShortcutsDialog = true;
    await this.updateComplete;
    assertIsDefined(this.keyboardShortcuts, 'keyboardShortcuts');

    if (this.keyboardShortcuts.opened) {
      this.keyboardShortcuts.cancel();
      return;
    }
    this.footerHeaderAriaHidden = true;
    this.mainAriaHidden = true;
    await this.keyboardShortcuts.open();
  }

  private handleKeyboardShortcutDialogClose() {
    assertIsDefined(this.keyboardShortcuts, 'keyboardShortcuts');
    this.keyboardShortcuts.close();
  }

  onOverlayCanceled() {
    this.footerHeaderAriaHidden = false;
    this.mainAriaHidden = false;
  }

  private handleAccountDetailUpdate() {
    this.mainHeader?.reload();
    this.settingsView?.reloadAccountDetail();
  }

  private handleRegistrationDialogClose() {
    // The registration dialog is visible only if this.params is
    // instanceof AppElementJustRegisteredParams
    (this.params as AppElementJustRegisteredParams).justRegistered = false;
    assertIsDefined(this.registrationOverlay, 'registrationOverlay');
    this.registrationOverlay.close();
  }

  private computePluginScreenName() {
    if (this.view !== GerritView.PLUGIN_SCREEN) return '';
    if (this.params === undefined) return '';
    const pluginViewState = this.params as PluginViewState;
    if (!pluginViewState.plugin || !pluginViewState.screen) return '';
    return `${pluginViewState.plugin}-screen-${pluginViewState.screen}`;
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

  private mobileSearchToggle() {
    this.mobileSearch = !this.mobileSearch;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app-element': GrAppElement;
  }
}
