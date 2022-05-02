/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import '../styles/themes/app-theme';
import '../styles/themes/dark-theme';
import {applyTheme as applyDarkTheme} from '../styles/themes/dark-theme';
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
import {getBaseUrl} from '../utils/url-util';
import {Shortcut} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GerritNav} from './core/gr-navigation/gr-navigation';
import {getAppContext} from '../services/app-context';
import {flush} from '@polymer/polymer/lib/utils/flush';
import {GrRouter} from './core/gr-router/gr-router';
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
  AppElementPluginScreenParams,
  AppElementSearchParam,
  isAppElementJustRegisteredParams,
} from './gr-app-types';
import {GrMainHeader} from './core/gr-main-header/gr-main-header';
import {GrSettingsView} from './settings/gr-settings-view/gr-settings-view';
import {
  DialogChangeEventDetail,
  EventType,
  LocationChangeEvent,
  PageErrorEventDetail,
  RpcLogEvent,
  TitleChangeEventDetail,
  ValueChangedEvent,
} from '../types/events';
import {ChangeListViewState, ChangeViewState, ViewState} from '../types/types';
import {GerritView} from '../services/router/router-model';
import {LifeCycle} from '../constants/reporting';
import {fireIronAnnounce} from '../utils/event-util';
import {resolve} from '../models/dependency';
import {browserModelToken} from '../models/browser/browser-model';
import {sharedStyles} from '../styles/shared-styles';
import {LitElement, PropertyValues, html, css, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {ShortcutController} from './lit/shortcut-controller';

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

  @property({type: Object})
  params?: AppElementParams;

  @state() private account?: AccountDetailInfo;

  @property({type: Number})
  _lastGKeyPressTimestamp: number | null = null;

  @state() private serverConfig?: ServerInfo;

  @state() private version?: string;

  @state() private showChangeListView?: boolean;

  @property({type: Boolean})
  _showDashboardView?: boolean;

  @property({type: Boolean})
  _showChangeView?: boolean;

  @property({type: Boolean})
  _showDiffView?: boolean;

  @property({type: Boolean})
  _showSettingsView?: boolean;

  @property({type: Boolean})
  _showAdminView?: boolean;

  @property({type: Boolean})
  _showCLAView?: boolean;

  @property({type: Boolean})
  _showEditorView?: boolean;

  @property({type: Boolean})
  _showPluginScreen?: boolean;

  @property({type: Boolean})
  _showDocumentationSearch?: boolean;

  @property({type: Object})
  _viewState?: ViewState;

  @state() lastError?: ErrorInfo;

  @property({type: String})
  lastSearchPage?: string;

  @property({type: String})
  _path?: string;

  @state() private settingsUrl?: string;

  @property({type: Boolean})
  mobileSearch = false;

  @state() private loginUrl = '/login';

  @property({type: Boolean})
  loadRegistrationDialog = false;

  @property({type: Boolean})
  loadKeyboardShortcutsDialog = false;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes footer, header from a11y tree, when a dialog on view
  // (e.g. reply dialog) is open
  @state() private footerHeaderAriaHidden = false;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes main page from a11y tree, when a dialog on gr-app-element
  // (e.g. shortcut dialog) is open
  @state() private mainAriaHidden = false;

  readonly router = new GrRouter();

  private reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly shortcuts = new ShortcutController(this);

  constructor() {
    super();
    document.addEventListener(EventType.PAGE_ERROR, e => {
      this._handlePageError(e);
    });
    this.addEventListener(EventType.TITLE_CHANGE, e => {
      this._handleTitleChange(e);
    });
    this.addEventListener(EventType.DIALOG_CHANGE, e => {
      this._handleDialogChange(e as CustomEvent<DialogChangeEventDetail>);
    });
    this.addEventListener(EventType.LOCATION_CHANGE, e =>
      this._handleLocationChange(e)
    );
    this.addEventListener(EventType.RECREATE_CHANGE_VIEW, () =>
      this.handleRecreateView()
    );
    this.addEventListener(EventType.RECREATE_DIFF_VIEW, () =>
      this.handleRecreateView()
    );
    document.addEventListener(EventType.GR_RPC_LOG, e => this._handleRpcLog(e));
    this.shortcuts.addAbstract(Shortcut.OPEN_SHORTCUT_HELP_DIALOG, () =>
      this._showKeyboardShortcuts()
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_USER_DASHBOARD, () =>
      this._goToUserDashboard()
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_OPENED_CHANGES, () =>
      this._goToOpenedChanges()
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_MERGED_CHANGES, () =>
      this._goToMergedChanges()
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_ABANDONED_CHANGES, () =>
      this._goToAbandonedChanges()
    );
    this.shortcuts.addAbstract(Shortcut.GO_TO_WATCHED_CHANGES, () =>
      this._goToWatchedChanges()
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    const resizeObserver = this.getBrowserModel().observeWidth();
    resizeObserver.observe(this);

    this._updateLoginUrl();
    this.reporting.appStarted();
    this.router.start();

    this.restApiService.getAccount().then(account => {
      this.account = account;
      if (account) {
        this.reporting.reportLifeCycle(LifeCycle.STARTED_AS_USER);
      } else {
        this.reporting.reportLifeCycle(LifeCycle.STARTED_AS_GUEST);
      }
    });
    this.restApiService.getConfig().then(config => {
      this.serverConfig = config;
    });
    this.restApiService.getVersion().then(version => {
      this.version = version;
      this._logWelcome();
    });

    const isDarkTheme = !!window.localStorage.getItem('dark-theme');
    document.documentElement.classList.toggle('darkTheme', isDarkTheme);
    document.documentElement.classList.toggle('lightTheme', !isDarkTheme);
    if (isDarkTheme) applyDarkTheme();

    // Note: this is evaluated here to ensure that it only happens after the
    // router has been initialized. @see Issue 7837
    this.settingsUrl = GerritNav.getUrlForSettings();

    this._viewState = {
      changeView: {
        changeNum: null,
        patchRange: null,
        selectedFileIndex: 0,
        showReplyDialog: false,
        diffMode: null,
        numFilesShown: null,
      },
      changeListView: {
        query: null,
        offset: 0,
        selectedChangeIndex: 0,
      },
      dashboardView: {},
    };
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
    if (!this.params) return nothing;
    return html`
      <gr-endpoint-decorator name="banner"></gr-endpoint-decorator>
      <gr-main-header
        id="mainHeader"
        .searchQuery=${(this.params as AppElementSearchParam).query}
        @mobile-search=${this.mobileSearchToggle}
        @show-keyboard-shortcuts=${this.handleShowKeyboardShortcuts}
        .mobileSearchHidden=${!this.mobileSearch}
        .loginUrl=${this.loginUrl}
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
          <gr-endpoint-decorator .name=${'footer-left'}></gr-endpoint-decorator>
        </div>
        <div>
          Press “?” for keyboard shortcuts
          <gr-endpoint-decorator
            .name=${'footer-right'}
          ></gr-endpoint-decorator>
        </div>
      </footer>
      ${this.renderKeyboardShortcutsDialog()} ${this.renderRegistrationDialog()}
      <gr-endpoint-decorator .name=${'plugin-overlay'}></gr-endpoint-decorator>
      <gr-error-manager
        id="errorManager"
        .loginUrl=${this.loginUrl}
      ></gr-error-manager>
      <gr-plugin-host id="plugins" .config=${this.serverConfig}>
      </gr-plugin-host>
      <gr-external-style
        id="externalStyleForAll"
        .name=${'app-theme'}
      ></gr-external-style>
      <gr-external-style
        id="externalStyleForTheme"
        .name=${this.getThemeEndpoint()}
      ></gr-external-style>
    `;
  }

  private renderMobileSearch() {
    if (!this.mobileSearch) return nothing;
    return html`
      <gr-smart-search
        id="search"
        label="Search for changes"
        .searchQuery=${(this.params as AppElementSearchParam).query}
        .serverConfig=${this.serverConfig}
      >
      </gr-smart-search>
    `;
  }

  private renderChangeListView() {
    if (!this.showChangeListView) return nothing;
    return html`
      <gr-change-list-view
        .params=${this.params}
        .account=${this.account}
        .viewState=${this._viewState?.changeListView}
        @view-state-changed=${this.handleViewStateChanged}
      ></gr-change-list-view>
    `;
  }

  private renderDashboardView() {
    if (!this._showDashboardView) return nothing;
    return html`
      <gr-dashboard-view
        .account=${this.account}
        .params=${this.params}
        .viewState=${this._viewState?.dashboardView}
      ></gr-dashboard-view>
    `;
  }

  private renderChangeView() {
    if (!this._showChangeView) return nothing;
    return html`
      <gr-change-view
        .params=${this.params}
        .viewState=${this._viewState?.changeView}
        .backPage=${this.lastSearchPage}
        @view-state-changed=${this.handleViewStateChangeViewChanged}
      ></gr-change-view>
    `;
  }

  private renderEditorView() {
    if (!this._showEditorView) return nothing;
    return html`<gr-editor-view .params=${this.params}></gr-editor-view>`;
  }

  private renderDiffView() {
    if (!this._showDiffView) return nothing;
    return html`
      <gr-diff-view
        .params=${this.params}
        .changeViewState=${this._viewState?.changeView}
        @change-view-state-changed=${this.handleViewStateChangeViewChanged}
      ></gr-diff-view>
    `;
  }

  private renderSettingsView() {
    if (!this._showSettingsView) return nothing;
    return html`
      <gr-settings-view
        .params=${this.params}
        @account-detail-update=${this.handleAccountDetailUpdate}
      >
      </gr-settings-view>
    `;
  }

  private renderAdminView() {
    if (!this._showAdminView) return nothing;
    return html`<gr-admin-view
      .path=${this._path}
      .params=${this.params}
    ></gr-admin-view>`;
  }

  private renderPluginScreen() {
    if (!this._showPluginScreen) return nothing;
    return html`
      <gr-endpoint-decorator .name=${this.computePluginScreenName()}>
        <gr-endpoint-param
          .name=${'token'}
          .value=${(this.params as AppElementPluginScreenParams).screen}
        ></gr-endpoint-param>
      </gr-endpoint-decorator>
    `;
  }

  private renderCLAView() {
    if (!this._showCLAView) return nothing;
    return html`<gr-cla-view></gr-cla-view>`;
  }

  private renderDocumentationSearch() {
    if (!this._showDocumentationSearch) return nothing;
    return html`
      <gr-documentation-search .params=${this.params}></gr-documentation-search>
    `;
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
    this._showChangeView = this.params?.view === GerritView.CHANGE;
    this._showDiffView = this.params?.view === GerritView.DIFF;
  }

  viewChanged() {
    const view = this.params?.view;
    this.errorView?.classList.remove('show');
    this.showChangeListView = view === GerritView.SEARCH;
    this._showDashboardView = view === GerritView.DASHBOARD;
    this._showChangeView = view === GerritView.CHANGE;
    this._showDiffView = view === GerritView.DIFF;
    this._showSettingsView = view === GerritView.SETTINGS;
    // _showAdminView must be in sync with the gr-admin-view AdminViewParams type
    this._showAdminView =
      view === GerritView.ADMIN ||
      view === GerritView.GROUP ||
      view === GerritView.REPO;
    this._showCLAView = view === GerritView.AGREEMENTS;
    this._showEditorView = view === GerritView.EDIT;
    const isPluginScreen = view === GerritView.PLUGIN_SCREEN;
    this._showPluginScreen = false;
    // Navigation within plugin screens does not restamp gr-endpoint-decorator
    // because _showPluginScreen value does not change. To force restamp,
    // change _showPluginScreen value between true and false.
    if (isPluginScreen) {
      setTimeout(() => (this._showPluginScreen = true), 1);
    }
    this._showDocumentationSearch = view === GerritView.DOCUMENTATION_SEARCH;
    if (
      this.params &&
      isAppElementJustRegisteredParams(this.params) &&
      this.params.justRegistered
    ) {
      this.loadRegistrationDialog = true;
      flush();
      const registrationOverlay = this.shadowRoot!.querySelector(
        '#registrationOverlay'
      ) as GrOverlay;
      const registrationDialog = this.shadowRoot!.querySelector(
        '#registrationDialog'
      ) as GrRegistrationDialog;
      registrationOverlay.open();
      registrationDialog.loadData().then(() => {
        registrationOverlay.refit();
      });
    }
    // To fix bug announce read after each new view, we reset announce with
    // empty space
    fireIronAnnounce(this, ' ');
  }

  _handlePageError(e: CustomEvent<PageErrorEventDetail>) {
    const props = [
      'showChangeListView',
      '_showDashboardView',
      '_showChangeView',
      '_showDiffView',
      '_showSettingsView',
      '_showAdminView',
    ];
    for (const showProp of props) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (this as any)[showProp as any] = false;
    }

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

  _handleLocationChange(e: LocationChangeEvent) {
    this._updateLoginUrl();

    const hash = e.detail.hash.substring(1);
    let pathname = e.detail.pathname;
    if (pathname.startsWith('/c/') && Number(hash) > 0) {
      pathname += '@' + hash;
    }
    this._path = pathname;
  }

  _updateLoginUrl() {
    const baseUrl = getBaseUrl();
    if (baseUrl) {
      // Strip the canonical path from the path since needing canonical in
      // the path is unneeded and breaks the url.
      this.loginUrl =
        baseUrl +
        '/login/' +
        encodeURIComponent(
          '/' +
            window.location.pathname.substring(baseUrl.length) +
            window.location.search +
            window.location.hash
        );
    } else {
      this.loginUrl =
        '/login/' +
        encodeURIComponent(
          window.location.pathname +
            window.location.search +
            window.location.hash
        );
    }
  }

  // private but used in test
  paramsChanged() {
    const viewsToCheck = [GerritView.SEARCH, GerritView.DASHBOARD];
    if (this.params?.view && viewsToCheck.includes(this.params.view)) {
      this.lastSearchPage = location.pathname;
    }
  }

  _handleTitleChange(e: CustomEvent<TitleChangeEventDetail>) {
    if (e.detail.title) {
      document.title = e.detail.title + ' · Gerrit Code Review';
    } else {
      document.title = '';
    }
  }

  _handleDialogChange(e: CustomEvent<DialogChangeEventDetail>) {
    if (e.detail.canceled) {
      this.footerHeaderAriaHidden = false;
    } else if (e.detail.opened) {
      this.footerHeaderAriaHidden = true;
    }
  }

  private handleShowKeyboardShortcuts() {
    this.loadKeyboardShortcutsDialog = true;
    (this.shadowRoot!.querySelector('#keyboardShortcuts') as GrOverlay).open();
  }

  _showKeyboardShortcuts() {
    // same shortcut should close the dialog if pressed again
    // when dialog is open
    this.loadKeyboardShortcutsDialog = true;
    flush();
    const keyboardShortcuts = this.shadowRoot!.querySelector(
      '#keyboardShortcuts'
    ) as GrOverlay;
    if (!keyboardShortcuts) return;
    if (keyboardShortcuts.opened) {
      keyboardShortcuts.cancel();
      return;
    }
    keyboardShortcuts.open();
    this.footerHeaderAriaHidden = true;
    this.mainAriaHidden = true;
  }

  private handleKeyboardShortcutDialogClose() {
    (
      this.shadowRoot!.querySelector('#keyboardShortcuts') as GrOverlay
    ).cancel();
  }

  onOverlayCanceled() {
    this.footerHeaderAriaHidden = false;
    this.mainAriaHidden = false;
  }

  private handleAccountDetailUpdate() {
    this.mainHeader?.reload();
    if (this.params?.view === GerritView.SETTINGS) {
      (
        this.shadowRoot!.querySelector('gr-settings-view') as GrSettingsView
      ).reloadAccountDetail();
    }
  }

  private handleRegistrationDialogClose() {
    // The registration dialog is visible only if this.params is
    // instanceof AppElementJustRegisteredParams
    (this.params as AppElementJustRegisteredParams).justRegistered = false;
    (
      this.shadowRoot!.querySelector('#registrationOverlay') as GrOverlay
    ).close();
  }

  _goToOpenedChanges() {
    GerritNav.navigateToStatusSearch('open');
  }

  _goToUserDashboard() {
    GerritNav.navigateToUserDashboard();
  }

  _goToMergedChanges() {
    GerritNav.navigateToStatusSearch('merged');
  }

  _goToAbandonedChanges() {
    GerritNav.navigateToStatusSearch('abandoned');
  }

  _goToWatchedChanges() {
    // The query is hardcoded, and doesn't respect custom menu entries
    GerritNav.navigateToSearchQuery('is:watched is:open');
  }

  private computePluginScreenName() {
    if (this.params?.view !== GerritView.PLUGIN_SCREEN) return '';
    if (!this.params.plugin || !this.params.screen) return '';
    return `${this.params.plugin}-screen-${this.params.screen}`;
  }

  _logWelcome() {
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
  _handleRpcLog(e: RpcLogEvent) {
    this.reporting.reportRpcTiming(e.detail.anonymizedUrl, e.detail.elapsed);
  }

  private mobileSearchToggle() {
    this.mobileSearch = !this.mobileSearch;
  }

  getThemeEndpoint() {
    // For now, we only have dark mode and light mode
    return window.localStorage.getItem('dark-theme')
      ? 'app-theme-dark'
      : 'app-theme-light';
  }

  private handleViewStateChanged(e: ValueChangedEvent<ChangeListViewState>) {
    if (!this._viewState) return;
    this._viewState.changeListView = {
      ...this._viewState.changeListView,
      ...e.detail.value,
    };
  }

  private handleViewStateChangeViewChanged(
    e: ValueChangedEvent<ChangeViewState>
  ) {
    if (!this._viewState) return;
    this._viewState.changeView = {
      ...this._viewState.changeView,
      ...e.detail.value,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app-element': GrAppElement;
  }
}
