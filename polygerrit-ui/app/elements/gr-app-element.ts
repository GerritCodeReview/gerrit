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
  SPECIAL_SHORTCUT,
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
  CustomKeyboardEvent,
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

  @property({type: Object})
  keyEventTarget = document.body;

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

  override keyboardShortcuts() {
    return {
      [Shortcut.OPEN_SHORTCUT_HELP_DIALOG]: '_showKeyboardShortcuts',
      [Shortcut.GO_TO_USER_DASHBOARD]: '_goToUserDashboard',
      [Shortcut.GO_TO_OPENED_CHANGES]: '_goToOpenedChanges',
      [Shortcut.GO_TO_MERGED_CHANGES]: '_goToMergedChanges',
      [Shortcut.GO_TO_ABANDONED_CHANGES]: '_goToAbandonedChanges',
      [Shortcut.GO_TO_WATCHED_CHANGES]: '_goToWatchedChanges',
    };
  }

  constructor() {
    super();
    // We just want to instantiate this service somewhere. It is reacting to
    // model changes and updates the config model, but at the moment the service
    // is not called from anywhere.
    appContext.configService;
    this._bindKeyboardShortcuts();
    document.addEventListener(EventType.PAGE_ERROR, e => {
      this._handlePageError(e);
    });
    this.addEventListener(EventType.TITLE_CHANGE, e => {
      this._handleTitleChange(e);
    });
    this.addEventListener(EventType.DIALOG_CHANGE, e => {
      this._handleDialogChange(e as CustomEvent<DialogChangeEventDetail>);
    });
    this.addEventListener('location-change', e =>
      this._handleLocationChange(e)
    );
    this.addEventListener(EventType.RECREATE_CHANGE_VIEW, () =>
      this.handleRecreateView(GerritView.CHANGE)
    );
    this.addEventListener(EventType.RECREATE_DIFF_VIEW, () =>
      this.handleRecreateView(GerritView.DIFF)
    );
    document.addEventListener('gr-rpc-log', e => this._handleRpcLog(e));
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

  _bindKeyboardShortcuts() {
    this.bindShortcut(
      Shortcut.SEND_REPLY,
      SPECIAL_SHORTCUT.DOC_ONLY,
      'ctrl+enter',
      'meta+enter'
    );
    this.bindShortcut(Shortcut.EMOJI_DROPDOWN, SPECIAL_SHORTCUT.DOC_ONLY, ':');

    this.bindShortcut(Shortcut.OPEN_SHORTCUT_HELP_DIALOG, '?');
    this.bindShortcut(
      Shortcut.GO_TO_USER_DASHBOARD,
      SPECIAL_SHORTCUT.GO_KEY,
      'i'
    );
    this.bindShortcut(
      Shortcut.GO_TO_OPENED_CHANGES,
      SPECIAL_SHORTCUT.GO_KEY,
      'o'
    );
    this.bindShortcut(
      Shortcut.GO_TO_MERGED_CHANGES,
      SPECIAL_SHORTCUT.GO_KEY,
      'm'
    );
    this.bindShortcut(
      Shortcut.GO_TO_ABANDONED_CHANGES,
      SPECIAL_SHORTCUT.GO_KEY,
      'a'
    );
    this.bindShortcut(
      Shortcut.GO_TO_WATCHED_CHANGES,
      SPECIAL_SHORTCUT.GO_KEY,
      'w'
    );

    this.bindShortcut(Shortcut.CURSOR_NEXT_CHANGE, 'j');
    this.bindShortcut(Shortcut.CURSOR_PREV_CHANGE, 'k');
    this.bindShortcut(Shortcut.OPEN_CHANGE, 'o');
    this.bindShortcut(Shortcut.NEXT_PAGE, 'n', ']');
    this.bindShortcut(Shortcut.PREV_PAGE, 'p', '[');
    this.bindShortcut(Shortcut.TOGGLE_CHANGE_REVIEWED, 'r:keyup');
    this.bindShortcut(Shortcut.TOGGLE_CHANGE_STAR, 's:keydown');
    this.bindShortcut(Shortcut.REFRESH_CHANGE_LIST, 'shift+r:keyup');
    this.bindShortcut(Shortcut.EDIT_TOPIC, 't');
    this.bindShortcut(Shortcut.OPEN_SUBMIT_DIALOG, 'shift+s');
    this.bindShortcut(Shortcut.TOGGLE_ATTENTION_SET, 'shift+t');

    this.bindShortcut(Shortcut.OPEN_REPLY_DIALOG, 'a:keyup');
    this.bindShortcut(Shortcut.OPEN_DOWNLOAD_DIALOG, 'd:keyup');
    this.bindShortcut(Shortcut.EXPAND_ALL_MESSAGES, 'x');
    this.bindShortcut(Shortcut.COLLAPSE_ALL_MESSAGES, 'z');
    this.bindShortcut(Shortcut.REFRESH_CHANGE, 'shift+r:keyup');
    this.bindShortcut(Shortcut.UP_TO_DASHBOARD, 'u');
    this.bindShortcut(Shortcut.UP_TO_CHANGE, 'u');
    this.bindShortcut(Shortcut.TOGGLE_DIFF_MODE, 'm:keyup');
    this.bindShortcut(
      Shortcut.DIFF_AGAINST_BASE,
      SPECIAL_SHORTCUT.V_KEY,
      'down',
      's'
    );
    // this keyboard shortcut is used in toast _displayDiffAgainstLatestToast
    // in gr-diff-view. Any updates here should be reflected there
    this.bindShortcut(
      Shortcut.DIFF_AGAINST_LATEST,
      SPECIAL_SHORTCUT.V_KEY,
      'up',
      'w'
    );
    // this keyboard shortcut is used in toast _displayDiffBaseAgainstLeftToast
    // in gr-diff-view. Any updates here should be reflected there
    this.bindShortcut(
      Shortcut.DIFF_BASE_AGAINST_LEFT,
      SPECIAL_SHORTCUT.V_KEY,
      'left',
      'a'
    );
    this.bindShortcut(
      Shortcut.DIFF_RIGHT_AGAINST_LATEST,
      SPECIAL_SHORTCUT.V_KEY,
      'right',
      'd'
    );
    this.bindShortcut(
      Shortcut.DIFF_BASE_AGAINST_LATEST,
      SPECIAL_SHORTCUT.V_KEY,
      'b'
    );

    this.bindShortcut(Shortcut.NEXT_LINE, 'j', 'down');
    this.bindShortcut(Shortcut.PREV_LINE, 'k', 'up');
    if (this._isCursorManagerSupportMoveToVisibleLine()) {
      this.bindShortcut(Shortcut.VISIBLE_LINE, '.');
    }
    this.bindShortcut(Shortcut.NEXT_CHUNK, 'n');
    this.bindShortcut(Shortcut.PREV_CHUNK, 'p');
    this.bindShortcut(Shortcut.TOGGLE_ALL_DIFF_CONTEXT, 'shift+x');
    this.bindShortcut(Shortcut.NEXT_COMMENT_THREAD, 'shift+n');
    this.bindShortcut(Shortcut.PREV_COMMENT_THREAD, 'shift+p');
    this.bindShortcut(
      Shortcut.EXPAND_ALL_COMMENT_THREADS,
      SPECIAL_SHORTCUT.DOC_ONLY,
      'e'
    );
    this.bindShortcut(
      Shortcut.COLLAPSE_ALL_COMMENT_THREADS,
      SPECIAL_SHORTCUT.DOC_ONLY,
      'shift+e'
    );
    this.bindShortcut(Shortcut.LEFT_PANE, 'shift+left');
    this.bindShortcut(Shortcut.RIGHT_PANE, 'shift+right');
    this.bindShortcut(Shortcut.TOGGLE_LEFT_PANE, 'shift+a');
    this.bindShortcut(Shortcut.NEW_COMMENT, 'c');
    this.bindShortcut(
      Shortcut.SAVE_COMMENT,
      'ctrl+enter',
      'meta+enter',
      'ctrl+s',
      'meta+s'
    );
    this.bindShortcut(Shortcut.OPEN_DIFF_PREFS, ',');
    this.bindShortcut(Shortcut.TOGGLE_DIFF_REVIEWED, 'r:keyup');

    this.bindShortcut(Shortcut.NEXT_FILE, ']');
    this.bindShortcut(Shortcut.PREV_FILE, '[');
    this.bindShortcut(Shortcut.NEXT_FILE_WITH_COMMENTS, 'shift+j');
    this.bindShortcut(Shortcut.PREV_FILE_WITH_COMMENTS, 'shift+k');
    this.bindShortcut(Shortcut.CURSOR_NEXT_FILE, 'j', 'down');
    this.bindShortcut(Shortcut.CURSOR_PREV_FILE, 'k', 'up');
    this.bindShortcut(Shortcut.OPEN_FILE, 'o', 'enter');
    this.bindShortcut(Shortcut.TOGGLE_FILE_REVIEWED, 'r:keyup');
    this.bindShortcut(Shortcut.NEXT_UNREVIEWED_FILE, 'shift+m');
    this.bindShortcut(Shortcut.TOGGLE_ALL_INLINE_DIFFS, 'shift+i');
    this.bindShortcut(Shortcut.TOGGLE_INLINE_DIFF, 'i');
    this.bindShortcut(Shortcut.TOGGLE_BLAME, 'b:keyup');
    this.bindShortcut(Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, 'h');
    this.bindShortcut(Shortcut.OPEN_FILE_LIST, 'f');

    this.bindShortcut(Shortcut.OPEN_FIRST_FILE, ']');
    this.bindShortcut(Shortcut.OPEN_LAST_FILE, '[');

    this.bindShortcut(Shortcut.SEARCH, '/');
  }

  _isCursorManagerSupportMoveToVisibleLine() {
    // This method is a copy-paste from the
    // method _isIntersectionObserverSupported of gr-cursor-manager.js
    // It is better share this method with gr-cursor-manager,
    // but doing it require a lot if changes instead of 1-line copied code
    return 'IntersectionObserver' in window;
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

  _showKeyboardShortcuts(e: CustomKeyboardEvent) {
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
    if (this.shouldSuppressKeyboardShortcut(e)) {
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
