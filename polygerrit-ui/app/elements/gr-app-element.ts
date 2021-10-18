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
import '../styles/shared-styles';
import '../styles/themes/app-theme';
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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-app-element_html';
import {getBaseUrl} from '../utils/url-util';
import {
  KeyboardShortcutMixin,
  Shortcut,
  ShortcutListener,
} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GerritNav} from './core/gr-navigation/gr-navigation';
import {appContext} from '../services/app-context';
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
} from '../types/events';
import {ViewState} from '../types/types';
import {GerritView} from '../services/router/router-model';
import {LifeCycle} from '../constants/reporting';
import {fireIronAnnounce} from '../utils/event-util';
import {assertIsDefined} from '../utils/common-util';
import {listen} from '../services/shortcuts/shortcuts-service';

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
const base = KeyboardShortcutMixin(PolymerElement);

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

  @property({type: String})
  _feedbackUrl?: string;

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

  private reporting = appContext.reportingService;

  private readonly restApiService = appContext.restApiService;

  private readonly browserService = appContext.browserService;

  private resizeObserver?: ResizeObserver;

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
    // We just want to instantiate this service somewhere. It is reacting to
    // model changes and updates the config model, but at the moment the service
    // is not called from anywhere.
    appContext.configService;
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
    this.resizeObserver = this.browserService.observeWidth();
    this.resizeObserver.observe(this);
  }

  override ready() {
    super.ready();
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

      if (config && config.gerrit && config.gerrit.report_bug_url) {
        this._feedbackUrl = config.gerrit.report_bug_url;
      }
    });
    this.restApiService.getVersion().then(version => {
      this._version = version;
      this._logWelcome();
    });

    if (window.localStorage.getItem('dark-theme')) {
      applyDarkTheme();
    }

    // Note: this is evaluated here to ensure that it only happens after the
    // router has been initialized. @see Issue 7837
    this._settingsUrl = GerritNav.getUrlForSettings();

    this._viewState = {
      changeView: {
        changeNum: null,
        patchRange: null,
        selectedFileIndex: 0,
        showReplyDialog: false,
        showDownloadDialog: false,
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
    if (this._feedbackUrl) {
      console.info(`Please file bugs and feedback at: ${this._feedbackUrl}`);
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app-element': GrAppElement;
  }
}
