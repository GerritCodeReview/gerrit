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
import './core/gr-router/gr-router';
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
import {css, html, LitElement} from 'lit';
import {getBaseUrl} from '../utils/url-util';
import {
  KeyboardShortcutMixin,
  Shortcut,
  ShortcutListener,
} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GerritNav} from './core/gr-navigation/gr-navigation';
import {getAppContext} from '../services/app-context';
import {flush} from '@polymer/polymer/lib/utils/flush';
import {customElement, observe, property} from '@polymer/decorators';
import {GrRouter} from './core/gr-router/gr-router';
import {
  AccountDetailInfo,
  ElementPropertyDeepChange,
  ServerInfo,
} from '../types/common';
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
  LocationChangeEvent,
  PageErrorEventDetail,
  RpcLogEvent,
  TitleChangeEventDetail,
  ValueChangedEvent,
} from '../types/events';
import {ChangeListViewState, ViewState} from '../types/types';
import {GerritView} from '../services/router/router-model';
import {LifeCycle} from '../constants/reporting';
import {fireIronAnnounce} from '../utils/event-util';
import {assertIsDefined} from '../utils/common-util';
import {listen} from '../services/shortcuts/shortcuts-service';
import {resolve, DIPolymerElement} from '../models/dependency';
import {browserModelToken} from '../models/browser/browser-model';
import {sharedStyles} from '../styles/shared-styles';

interface ErrorInfo {
  text: string;
  emoji?: string;
  moreInfo?: string;
}

export interface GrAppElement {
  $: {
    router: GrRouter;
    errorManager: GrErrorManager;
    errorView: HTMLDivElement;
    mainHeader: GrMainHeader;
  };
}

type DomIf = PolymerElement & {
  restamp: boolean;
};

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = KeyboardShortcutMixin(DIPolymerElement);

// TODO(TS): implement AppElement interface from gr-app-types.ts
@customElement('gr-app-element')
export class GrAppElement extends base {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the URL location changes.
   *
   * @event location-change
   */

  @property({type: Object})
  params?: AppElementParams;

  @property({type: Object, observer: '_accountChanged'})
  _account?: AccountDetailInfo;

  @property({type: Number})
  _lastGKeyPressTimestamp: number | null = null;

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({type: String})
  _version?: string;

  @property({type: Boolean})
  _showChangeListView?: boolean;

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

  @property({type: Object})
  _lastError?: ErrorInfo;

  @property({type: String})
  _lastSearchPage?: string;

  @property({type: String})
  _path?: string;

  @property({type: String, computed: '_computePluginScreenName(params)'})
  _pluginScreenName?: string;

  @property({type: String})
  _settingsUrl?: string;

  @property({type: Boolean})
  mobileSearch = false;

  @property({type: String})
  _loginUrl = '/login';

  @property({type: Boolean})
  loadRegistrationDialog = false;

  @property({type: Boolean})
  loadKeyboardShortcutsDialog = false;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes footer, header from a11y tree, when a dialog on view
  // (e.g. reply dialog) is open
  @property({type: Boolean})
  _footerHeaderAriaHidden = false;

  // TODO(milutin) - remove once new gr-dialog will do it out of the box
  // This removes main page from a11y tree, when a dialog on gr-app-element
  // (e.g. shortcut dialog) is open
  @property({type: Boolean})
  _mainAriaHidden = false;

  private reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getBrowserModel = resolve(this, browserModelToken);

  override keyboardShortcuts(): ShortcutListener[] {
    return [
      listen(Shortcut.OPEN_SHORTCUT_HELP_DIALOG, _ =>
        this._showKeyboardShortcuts()
      ),
      listen(Shortcut.GO_TO_USER_DASHBOARD, _ => this._goToUserDashboard()),
      listen(Shortcut.GO_TO_OPENED_CHANGES, _ => this._goToOpenedChanges()),
      listen(Shortcut.GO_TO_MERGED_CHANGES, _ => this._goToMergedChanges()),
      listen(Shortcut.GO_TO_ABANDONED_CHANGES, _ =>
        this._goToAbandonedChanges()
      ),
      listen(Shortcut.GO_TO_WATCHED_CHANGES, _ => this._goToWatchedChanges()),
    ];
  }

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
      this.handleRecreateView(GerritView.CHANGE)
    );
    this.addEventListener(EventType.RECREATE_DIFF_VIEW, () =>
      this.handleRecreateView(GerritView.DIFF)
    );
    document.addEventListener(EventType.GR_RPC_LOG, e => this._handleRpcLog(e));
    this._updateLoginUrl();
    this.reporting.appStarted();
    this.$.router.start();

    this.restApiService.getAccount().then(account => {
      this._account = account;
      if (account) {
        this.reporting.reportLifeCycle(LifeCycle.STARTED_AS_USER);
      } else {
        this.reporting.reportLifeCycle(LifeCycle.STARTED_AS_GUEST);
      }
    });
    this.restApiService.getConfig().then(config => {
      this._serverConfig = config;
    });
    this.restApiService.getVersion().then(version => {
      this._version = version;
      this._logWelcome();
    });

    const isDarkTheme = !!window.localStorage.getItem('dark-theme');
    document.documentElement.classList.toggle('darkTheme', isDarkTheme);
    document.documentElement.classList.toggle('lightTheme', !isDarkTheme);
    if (isDarkTheme) applyDarkTheme();

    // Note: this is evaluated here to ensure that it only happens after the
    // router has been initialized. @see Issue 7837
    this._settingsUrl = GerritNav.getUrlForSettings();

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
      }`];
  }

  override render() {
    return html`
    <gr-endpoint-decorator name="banner"></gr-endpoint-decorator>
    <gr-main-header
      id="mainHeader"
      search-query="[[params.query]]"
      on-mobile-search="_mobileSearchToggle"
      on-show-keyboard-shortcuts="handleShowKeyboardShortcuts"
      mobile-search-hidden="[[!mobileSearch]]"
      login-url="[[_loginUrl]]"
      aria-hidden="[[_footerHeaderAriaHidden]]"
    >
    </gr-main-header>
    <main aria-hidden="[[_mainAriaHidden]]">
      <template is="dom-if" if="[[mobileSearch]]">
        <gr-smart-search
          id="search"
          label="Search for changes"
          search-query="[[params.query]]"
          server-config="[[_serverConfig]]"
          hidden="[[!mobileSearch]]"
        >
        </gr-smart-search>
      </template>
      <template is="dom-if" if="[[_showChangeListView]]" restamp="true">
        <gr-change-list-view
          params="[[params]]"
          account="[[_account]]"
          view-state="[[_viewState.changeListView]]"
          on-view-state-changed="_handleViewStateChanged"
        ></gr-change-list-view>
      </template>
      <template is="dom-if" if="[[_showDashboardView]]" restamp="true">
        <gr-dashboard-view
          account="[[_account]]"
          params="[[params]]"
          view-state="{{_viewState.dashboardView}}"
        ></gr-dashboard-view>
      </template>
      <!-- Note that the change view does not have restamp="true" set, because we
           want to re-use it as long as the change number does not change. -->
      <template id="dom-if-change-view" is="dom-if" if="[[_showChangeView]]">
        <gr-change-view
          params="[[params]]"
          view-state="{{_viewState.changeView}}"
          back-page="[[_lastSearchPage]]"
        ></gr-change-view>
      </template>
      <template is="dom-if" if="[[_showEditorView]]" restamp="true">
        <gr-editor-view params="[[params]]"></gr-editor-view>
      </template>
      <!-- Note that the diff view does not have restamp="true" set, because we
           want to re-use it as long as the change number does not change. -->
      <template id="dom-if-diff-view" is="dom-if" if="[[_showDiffView]]">
        <gr-diff-view
          params="[[params]]"
          change-view-state="{{_viewState.changeView}}"
        ></gr-diff-view>
      </template>
      <template is="dom-if" if="[[_showSettingsView]]" restamp="true">
        <gr-settings-view
          params="[[params]]"
          on-account-detail-update="_handleAccountDetailUpdate"
        >
        </gr-settings-view>
      </template>
      <template is="dom-if" if="[[_showAdminView]]" restamp="true">
        <gr-admin-view path="[[_path]]" params="[[params]]"></gr-admin-view>
      </template>
      <template is="dom-if" if="[[_showPluginScreen]]" restamp="true">
        <gr-endpoint-decorator name="[[_pluginScreenName]]">
          <gr-endpoint-param
            name="token"
            value="[[params.screen]]"
          ></gr-endpoint-param>
        </gr-endpoint-decorator>
      </template>
      <template is="dom-if" if="[[_showCLAView]]" restamp="true">
        <gr-cla-view></gr-cla-view>
      </template>
      <template is="dom-if" if="[[_showDocumentationSearch]]" restamp="true">
        <gr-documentation-search params="[[params]]"> </gr-documentation-search>
      </template>
      <div id="errorView" class="errorView">
        <div class="errorEmoji">[[_lastError.emoji]]</div>
        <div class="errorText">[[_lastError.text]]</div>
        <div class="errorMoreInfo">[[_lastError.moreInfo]]</div>
      </div>
    </main>
    <footer r="contentinfo" aria-hidden="[[_footerHeaderAriaHidden]]">
      <div>
        Powered by
        <a href="https://www.gerritcodereview.com/" rel="noopener" target="_blank"
          >Gerrit Code Review</a
        >
        ([[_version]])
        <gr-endpoint-decorator name="footer-left"></gr-endpoint-decorator>
      </div>
      <div>
        Press “?” for keyboard shortcuts
        <gr-endpoint-decorator name="footer-right"></gr-endpoint-decorator>
      </div>
    </footer>
    <template is="dom-if" if="[[loadKeyboardShortcutsDialog]]">
      <gr-overlay
        id="keyboardShortcuts"
        with-backdrop=""
        on-iron-overlay-canceled="onOverlayCanceled"
      >
        <gr-keyboard-shortcuts-dialog
          on-close="_handleKeyboardShortcutDialogClose"
        ></gr-keyboard-shortcuts-dialog>
      </gr-overlay>
    </template>
    <template is="dom-if" if="[[loadRegistrationDialog]]">
      <gr-overlay id="registrationOverlay" with-backdrop="">
        <gr-registration-dialog
          id="registrationDialog"
          settings-url="[[_settingsUrl]]"
          on-account-detail-update="_handleAccountDetailUpdate"
          on-close="_handleRegistrationDialogClose"
        >
        </gr-registration-dialog>
      </gr-overlay>
    </template>
    <gr-endpoint-decorator name="plugin-overlay"></gr-endpoint-decorator>
    <gr-error-manager
      id="errorManager"
      login-url="[[_loginUrl]]"
    ></gr-error-manager>
    <gr-router id="router"></gr-router>
    <gr-plugin-host id="plugins" config="[[_serverConfig]]"> </gr-plugin-host>
    <gr-external-style
      id="externalStyleForAll"
      name="app-theme"
    ></gr-external-style>
    <gr-external-style
      id="externalStyleForTheme"
      name="[[getThemeEndpoint()]]"
    ></gr-external-style>`;
  }

  override connectedCallback() {
    super.connectedCallback();
    const resizeObserver = this.getBrowserModel().observeWidth();
    resizeObserver.observe(this);
  }

  _accountChanged(account?: AccountDetailInfo) {
    if (!account) return;

    // Preferences are cached when a user is logged in; warm them.
    this.restApiService.getPreferences();
    this.restApiService.getDiffPreferences();
    this.restApiService.getEditPreferences();
    this.$.errorManager.knownAccountId =
      (this._account && this._account._account_id) || null;
  }

  /**
   * Throws away the view and re-creates it. The view itself fires an event, if
   * it wants to be re-created.
   */
  private handleRecreateView(view: GerritView.DIFF | GerritView.CHANGE) {
    const isDiff = view === GerritView.DIFF;
    const domId = isDiff ? '#dom-if-diff-view' : '#dom-if-change-view';
    const domIf = this.root!.querySelector(domId) as DomIf;
    assertIsDefined(domIf, '<dom-if> for the view');
    // The rendering of DomIf is debounced, so just changing _show...View and
    // restamp properties back and forth won't work. That is why we are using
    // timeouts.
    // The first timeout is needed, because the _viewChanged() observer also
    // affects _show...View and would change _show...View=false directly back to
    // _show...View=true.
    setTimeout(() => {
      this._showChangeView = false;
      this._showDiffView = false;
      domIf.restamp = true;
      setTimeout(() => {
        this._showChangeView = this.params?.view === GerritView.CHANGE;
        this._showDiffView = this.params?.view === GerritView.DIFF;
        domIf.restamp = false;
      }, 1);
    }, 1);
  }

  @observe('params.*')
  _viewChanged() {
    const view = this.params?.view;
    this.$.errorView.classList.remove('show');
    this._showChangeListView = view === GerritView.SEARCH;
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
      '_showChangeListView',
      '_showDashboardView',
      '_showChangeView',
      '_showDiffView',
      '_showSettingsView',
      '_showAdminView',
    ];
    for (const showProp of props) {
      this.set(showProp, false);
    }

    this.$.errorView.classList.add('show');
    const response = e.detail.response;
    const err: ErrorInfo = {
      text: [response?.status, response?.statusText].join(' '),
    };
    if (response?.status === 404) {
      err.emoji = '¯\\_(ツ)_/¯';
      this._lastError = err;
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
          this._lastError = err;
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
      this._loginUrl =
        baseUrl +
        '/login/' +
        encodeURIComponent(
          '/' +
            window.location.pathname.substring(baseUrl.length) +
            window.location.search +
            window.location.hash
        );
    } else {
      this._loginUrl =
        '/login/' +
        encodeURIComponent(
          window.location.pathname +
            window.location.search +
            window.location.hash
        );
    }
  }

  @observe('params.*')
  _paramsChanged(
    paramsRecord: ElementPropertyDeepChange<GrAppElement, 'params'>
  ) {
    const params = paramsRecord.base;
    const viewsToCheck = [GerritView.SEARCH, GerritView.DASHBOARD];
    if (params?.view && viewsToCheck.includes(params.view)) {
      this._lastSearchPage = location.pathname;
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
      this._footerHeaderAriaHidden = false;
    } else if (e.detail.opened) {
      this._footerHeaderAriaHidden = true;
    }
  }

  handleShowKeyboardShortcuts() {
    this.loadKeyboardShortcutsDialog = true;
    flush();
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
    this._footerHeaderAriaHidden = true;
    this._mainAriaHidden = true;
  }

  _handleKeyboardShortcutDialogClose() {
    (
      this.shadowRoot!.querySelector('#keyboardShortcuts') as GrOverlay
    ).cancel();
  }

  onOverlayCanceled() {
    this._footerHeaderAriaHidden = false;
    this._mainAriaHidden = false;
  }

  _handleAccountDetailUpdate() {
    this.$.mainHeader.reload();
    if (this.params?.view === GerritView.SETTINGS) {
      (
        this.shadowRoot!.querySelector('gr-settings-view') as GrSettingsView
      ).reloadAccountDetail();
    }
  }

  _handleRegistrationDialogClose() {
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

  _computePluginScreenName(params: AppElementParams) {
    if (params.view !== GerritView.PLUGIN_SCREEN) return '';
    if (!params.plugin || !params.screen) return '';
    return `${params.plugin}-screen-${params.screen}`;
  }

  _logWelcome() {
    console.group('Runtime Info');
    console.info('Gerrit UI (PolyGerrit)');
    console.info(`Gerrit Server Version: ${this._version}`);
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

  _mobileSearchToggle() {
    this.mobileSearch = !this.mobileSearch;
  }

  getThemeEndpoint() {
    // For now, we only have dark mode and light mode
    return window.localStorage.getItem('dark-theme')
      ? 'app-theme-dark'
      : 'app-theme-light';
  }

  _handleViewStateChanged(e: ValueChangedEvent<ChangeListViewState>) {
    if (!this._viewState) return;
    this._viewState.changeListView = {
      ...this._viewState.changeListView,
      ...e.detail.value,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app-element': GrAppElement;
  }
}
