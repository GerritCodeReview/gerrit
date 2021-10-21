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
import '@polymer/iron-input/iron-input';
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../../styles/gr-font-styles';
import '../../../styles/gr-form-styles';
import '../../../styles/gr-menu-page-styles';
import '../../../styles/gr-page-nav-styles';
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../gr-change-table-editor/gr-change-table-editor';
import '../../shared/gr-button/gr-button';
import {GrButton} from '../../shared/gr-button/gr-button';
import '../../shared/gr-diff-preferences/gr-diff-preferences';
import '../../shared/gr-page-nav/gr-page-nav';
import '../../shared/gr-select/gr-select';
import '../gr-account-info/gr-account-info';
import '../gr-agreements-list/gr-agreements-list';
import '../gr-edit-preferences/gr-edit-preferences';
import '../gr-email-editor/gr-email-editor';
import '../gr-gpg-editor/gr-gpg-editor';
import '../gr-group-list/gr-group-list';
import '../gr-http-password/gr-http-password';
import '../gr-identities/gr-identities';
import '../gr-menu-editor/gr-menu-editor';
import '../gr-ssh-editor/gr-ssh-editor';
import '../gr-watched-projects-editor/gr-watched-projects-editor';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-settings-view_html';
import {getDocsBaseUrl} from '../../../utils/url-util';
import {customElement, property, observe} from '@polymer/decorators';
import {AppElementParams} from '../../gr-app-types';
import {GrAccountInfo} from '../gr-account-info/gr-account-info';
import {GrWatchedProjectsEditor} from '../gr-watched-projects-editor/gr-watched-projects-editor';
import {GrGroupList} from '../gr-group-list/gr-group-list';
import {GrIdentities} from '../gr-identities/gr-identities';
import {GrEditPreferences} from '../gr-edit-preferences/gr-edit-preferences';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {
  PreferencesInput,
  ServerInfo,
  TopMenuItemInfo,
} from '../../../types/common';
import {GrSshEditor} from '../gr-ssh-editor/gr-ssh-editor';
import {GrGpgEditor} from '../gr-gpg-editor/gr-gpg-editor';
import {GrEmailEditor} from '../gr-email-editor/gr-email-editor';
import {fireAlert, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {GerritView} from '../../../services/router/router-model';
import {
  DateFormat,
  DefaultBase,
  DiffViewMode,
  EmailFormat,
  EmailStrategy,
  TimeFormat,
} from '../../../constants/constants';
import {columnNames} from '../../change-list/gr-change-list/gr-change-list';
import {windowLocationReload} from '../../../utils/dom-util';

const PREFS_SECTION_FIELDS: Array<keyof PreferencesInput> = [
  'changes_per_page',
  'date_format',
  'time_format',
  'email_strategy',
  'diff_view',
  'publish_comments_on_push',
  'disable_keyboard_shortcuts',
  'disable_token_highlighting',
  'work_in_progress_by_default',
  'default_base_for_merges',
  'signed_off_by',
  'email_format',
  'size_bar_in_change_table',
  'relative_date_in_change_table',
];

const GERRIT_DOCS_BASE_URL =
  'https://gerrit-review.googlesource.com/' + 'Documentation';
const GERRIT_DOCS_FILTER_PATH = '/user-notify.html';
const ABSOLUTE_URL_PATTERN = /^https?:/;
const TRAILING_SLASH_PATTERN = /\/$/;

const HTTP_AUTH = ['HTTP', 'HTTP_LDAP'];

enum CopyPrefsDirection {
  PrefsToLocalPrefs,
  LocalPrefsToPrefs,
}

type LocalMenuItemInfo = Omit<TopMenuItemInfo, 'id'>;

export interface GrSettingsView {
  $: {
    accountInfo: GrAccountInfo;
    watchedProjectsEditor: GrWatchedProjectsEditor;
    groupList: GrGroupList;
    identities: GrIdentities;
    editPrefs: GrEditPreferences;
    diffPrefs: GrDiffPreferences;
    sshEditor: GrSshEditor;
    gpgEditor: GrGpgEditor;
    emailEditor: GrEmailEditor;
    insertSignedOff: HTMLInputElement;
    workInProgressByDefault: HTMLInputElement;
    showSizeBarsInFileList: HTMLInputElement;
    publishCommentsOnPush: HTMLInputElement;
    disableKeyboardShortcuts: HTMLInputElement;
    disableTokenHighlighting: HTMLInputElement;
    relativeDateInChangeTable: HTMLInputElement;
    changesPerPageSelect: HTMLInputElement;
    dateTimeFormatSelect: HTMLInputElement;
    timeFormatSelect: HTMLInputElement;
    emailNotificationsSelect: HTMLInputElement;
    emailFormatSelect: HTMLInputElement;
    defaultBaseForMergesSelect: HTMLInputElement;
    diffViewSelect: HTMLInputElement;
    menu: HTMLFieldSetElement;
    resetButton: GrButton;
  };
}

@customElement('gr-settings-view')
export class GrSettingsView extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  /**
   * Fired with email confirmation text, or when the page reloads.
   *
   * @event show-alert
   */

  @property({type: Object})
  prefs: PreferencesInput = {};

  @property({type: Object})
  params?: AppElementParams;

  @property({type: Boolean})
  _accountInfoChanged?: boolean;

  @property({type: Object})
  _localPrefs: PreferencesInput = {};

  @property({type: Array})
  _localChangeTableColumns: string[] = [];

  @property({type: Array})
  _localMenu: LocalMenuItemInfo[] = [];

  @property({type: Boolean})
  _loading = true;

  @property({type: Boolean})
  _changeTableChanged = false;

  @property({type: Boolean})
  _prefsChanged = false;

  @property({type: Boolean})
  _diffPrefsChanged = false;

  @property({type: Boolean})
  _editPrefsChanged = false;

  @property({type: Boolean})
  _menuChanged = false;

  @property({type: Boolean})
  _watchedProjectsChanged = false;

  @property({type: Boolean})
  _keysChanged = false;

  @property({type: Boolean})
  _gpgKeysChanged = false;

  @property({type: String})
  _newEmail?: string;

  @property({type: Boolean})
  _addingEmail = false;

  @property({type: String})
  _lastSentVerificationEmail?: string | null = null;

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({type: String})
  _docsBaseUrl?: string | null;

  @property({type: Boolean})
  _emailsChanged = false;

  @property({type: Boolean})
  _showNumber?: boolean;

  @property({type: Boolean})
  _isDark = false;

  public _testOnly_loadingPromise?: Promise<void>;

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    // Polymer 2: anchor tag won't work on shadow DOM
    // we need to manually calling scrollIntoView when hash changed
    window.addEventListener('location-change', this.handleLocationChange);
    fireTitleChange(this, 'Settings');

    this._isDark = !!window.localStorage.getItem('dark-theme');

    const promises: Array<Promise<unknown>> = [
      this.$.accountInfo.loadData(),
      this.$.watchedProjectsEditor.loadData(),
      this.$.groupList.loadData(),
      this.$.identities.loadData(),
      this.$.editPrefs.loadData(),
      this.$.diffPrefs.loadData(),
    ];

    // TODO(dhruvsri): move this to the service
    promises.push(
      this.restApiService.getPreferences().then(prefs => {
        if (!prefs) {
          throw new Error('getPreferences returned undefined');
        }
        this.prefs = prefs;
        this._showNumber = !!prefs.legacycid_in_change_table;
        this._copyPrefs(CopyPrefsDirection.PrefsToLocalPrefs);
        this._localMenu = this._cloneMenu(prefs.my);
        this._localChangeTableColumns =
          prefs.change_table.length === 0
            ? columnNames
            : prefs.change_table.map(column =>
                column === 'Project' ? 'Repo' : column
              );
      })
    );

    promises.push(
      this.restApiService.getConfig().then(config => {
        this._serverConfig = config;
        const configPromises: Array<Promise<void>> = [];

        if (this._serverConfig && this._serverConfig.sshd) {
          configPromises.push(this.$.sshEditor.loadData());
        }

        if (
          this._serverConfig &&
          this._serverConfig.receive &&
          this._serverConfig.receive.enable_signed_push
        ) {
          configPromises.push(this.$.gpgEditor.loadData());
        }

        configPromises.push(
          getDocsBaseUrl(config, this.restApiService).then(baseUrl => {
            this._docsBaseUrl = baseUrl;
          })
        );

        return Promise.all(configPromises);
      })
    );

    if (
      this.params &&
      this.params.view === GerritView.SETTINGS &&
      this.params.emailToken
    ) {
      promises.push(
        this.restApiService
          .confirmEmail(this.params.emailToken)
          .then(message => {
            if (message) {
              fireAlert(this, message);
            }
            this.$.emailEditor.loadData();
          })
      );
    } else {
      promises.push(this.$.emailEditor.loadData());
    }

    this._testOnly_loadingPromise = Promise.all(promises).then(() => {
      this._loading = false;

      // Handle anchor tag for initial load
      this.handleLocationChange();
    });
  }

  override disconnectedCallback() {
    window.removeEventListener('location-change', this.handleLocationChange);
    super.disconnectedCallback();
  }

  private readonly handleLocationChange = () => {
    // Handle anchor tag after dom attached
    const urlHash = window.location.hash;
    if (urlHash) {
      // Use shadowRoot for Polymer 2
      const elem = (this.shadowRoot || document).querySelector(urlHash);
      if (elem) {
        elem.scrollIntoView();
      }
    }
  };

  reloadAccountDetail() {
    Promise.all([this.$.accountInfo.loadData(), this.$.emailEditor.loadData()]);
  }

  _isLoading() {
    return this._loading || this._loading === undefined;
  }

  _copyPrefs(direction: CopyPrefsDirection) {
    let to;
    let from;
    if (direction === CopyPrefsDirection.LocalPrefsToPrefs) {
      from = this._localPrefs;
      to = 'prefs';
    } else {
      from = this.prefs;
      to = '_localPrefs';
    }
    for (let i = 0; i < PREFS_SECTION_FIELDS.length; i++) {
      this.set([to, PREFS_SECTION_FIELDS[i]], from[PREFS_SECTION_FIELDS[i]]);
    }
  }

  _cloneMenu(prefs: TopMenuItemInfo[]) {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    return prefs.map(({id, ...item}) => item);
  }

  @observe('_localChangeTableColumns', '_showNumber')
  _handleChangeTableChanged() {
    if (this._isLoading()) {
      return;
    }
    this._changeTableChanged = true;
  }

  @observe('_localPrefs.*')
  _handlePrefsChanged() {
    if (this._isLoading()) {
      return;
    }
    this._prefsChanged = true;
  }

  _handleRelativeDateInChangeTable() {
    this.set(
      '_localPrefs.relative_date_in_change_table',
      this.$.relativeDateInChangeTable.checked
    );
  }

  _handleShowSizeBarsInFileListChanged() {
    this.set(
      '_localPrefs.size_bar_in_change_table',
      this.$.showSizeBarsInFileList.checked
    );
  }

  _handlePublishCommentsOnPushChanged() {
    this.set(
      '_localPrefs.publish_comments_on_push',
      this.$.publishCommentsOnPush.checked
    );
  }

  _handleDisableKeyboardShortcutsChanged() {
    this.set(
      '_localPrefs.disable_keyboard_shortcuts',
      this.$.disableKeyboardShortcuts.checked
    );
  }

  _handleDisableTokenHighlightingChanged() {
    this.set(
      '_localPrefs.disable_token_highlighting',
      this.$.disableTokenHighlighting.checked
    );
  }

  _handleWorkInProgressByDefault() {
    this.set(
      '_localPrefs.work_in_progress_by_default',
      this.$.workInProgressByDefault.checked
    );
  }

  _handleInsertSignedOff() {
    this.set('_localPrefs.signed_off_by', this.$.insertSignedOff.checked);
  }

  @observe('_localMenu.splices')
  _handleMenuChanged() {
    if (this._isLoading()) {
      return;
    }
    this._menuChanged = true;
  }

  _handleSaveAccountInfo() {
    this.$.accountInfo.save();
  }

  _handleSavePreferences() {
    this._copyPrefs(CopyPrefsDirection.LocalPrefsToPrefs);

    return this.restApiService.savePreferences(this.prefs).then(() => {
      this._prefsChanged = false;
    });
  }

  _handleSaveChangeTable() {
    this.set('prefs.change_table', this._localChangeTableColumns);
    this.set('prefs.legacycid_in_change_table', this._showNumber);
    return this.restApiService.savePreferences(this.prefs).then(() => {
      this._changeTableChanged = false;
    });
  }

  _handleSaveDiffPreferences() {
    this.$.diffPrefs.save();
  }

  _handleSaveEditPreferences() {
    this.$.editPrefs.save();
  }

  _handleSaveMenu() {
    this.set('prefs.my', this._localMenu);
    return this.restApiService.savePreferences(this.prefs).then(() => {
      this._menuChanged = false;
    });
  }

  _handleResetMenuButton() {
    return this.restApiService.getDefaultPreferences().then(data => {
      if (data?.my) {
        this._localMenu = this._cloneMenu(data.my);
      }
    });
  }

  _handleSaveWatchedProjects() {
    this.$.watchedProjectsEditor.save();
  }

  _computeHeaderClass(changed?: boolean) {
    return changed ? 'edited' : '';
  }

  _handleSaveEmails() {
    this.$.emailEditor.save();
  }

  _handleNewEmailKeydown(e: KeyboardEvent) {
    if (e.keyCode === 13) {
      // Enter
      e.stopPropagation();
      this._handleAddEmailButton();
    }
  }

  _isNewEmailValid(newEmail?: string): newEmail is string {
    return !!newEmail && newEmail.includes('@');
  }

  _computeAddEmailButtonEnabled(newEmail?: string, addingEmail?: boolean) {
    return this._isNewEmailValid(newEmail) && !addingEmail;
  }

  _handleAddEmailButton() {
    if (!this._isNewEmailValid(this._newEmail)) return;

    this._addingEmail = true;
    this.restApiService.addAccountEmail(this._newEmail).then(response => {
      this._addingEmail = false;

      // If it was unsuccessful.
      if (response.status < 200 || response.status >= 300) {
        return;
      }

      this._lastSentVerificationEmail = this._newEmail;
      this._newEmail = '';
    });
  }

  _getFilterDocsLink(docsBaseUrl?: string | null) {
    let base = docsBaseUrl;
    if (!base || !ABSOLUTE_URL_PATTERN.test(base)) {
      base = GERRIT_DOCS_BASE_URL;
    }

    // Remove any trailing slash, since it is in the GERRIT_DOCS_FILTER_PATH.
    base = base.replace(TRAILING_SLASH_PATTERN, '');

    return base + GERRIT_DOCS_FILTER_PATH;
  }

  _handleToggleDark() {
    if (this._isDark) {
      window.localStorage.removeItem('dark-theme');
    } else {
      window.localStorage.setItem('dark-theme', 'true');
    }
    this.reloadPage();
  }

  reloadPage() {
    windowLocationReload();
  }

  _showHttpAuth(config?: ServerInfo) {
    if (config && config.auth && config.auth.git_basic_auth_policy) {
      return HTTP_AUTH.includes(
        config.auth.git_basic_auth_policy.toUpperCase()
      );
    }

    return false;
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  _onTapDarkToggle(e: Event) {
    e.preventDefault();
  }

  _handleChangesPerPage() {
    this.set(
      '_localPrefs.changes_per_page',
      Number(this.$.changesPerPageSelect.value)
    );
  }

  _handleDateFormat() {
    this.set('_localPrefs.date_format', this.$.dateTimeFormatSelect.value);
  }

  _handleTimeFormat() {
    this.set('_localPrefs.time_format', this.$.timeFormatSelect.value);
  }

  _handleEmailStrategy() {
    this.set(
      '_localPrefs.email_strategy',
      this.$.emailNotificationsSelect.value
    );
  }

  _handleEmailFormat() {
    this.set('_localPrefs.email_format', this.$.emailFormatSelect.value);
  }

  _handleDefaultBaseForMerges() {
    this.set(
      '_localPrefs.default_base_for_merges',
      this.$.defaultBaseForMergesSelect.value
    );
  }

  _handleDiffView() {
    this.set(
      '_localPrefs.diff_view',
      this.$.diffViewSelect.value as DiffViewMode
    );
  }

  /**
   * bind-value has type string so we have to convert anything inputed
   * to string.
   *
   * This is so typescript template checker doesn't fail.
   */
  _convertToString(
    key?:
      | DateFormat
      | DefaultBase
      | DiffViewMode
      | EmailFormat
      | EmailStrategy
      | TimeFormat
      | number
  ) {
    return key !== undefined ? String(key) : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-view': GrSettingsView;
  }
}
