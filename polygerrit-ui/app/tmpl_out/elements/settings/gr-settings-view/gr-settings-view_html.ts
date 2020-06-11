import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrSettingsView} from '../../../../elements/settings/gr-settings-view/gr-settings-view';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrSettingsViewCheck extends GrSettingsView
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `loading`);
      el.setAttribute('hidden', `${!this._loading}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this._loading}`);
    }
    {
      const el: HTMLElementTagNameMap['gr-page-nav'] = null!;
      useVars(el);
      el.setAttribute('class', `navStyles`);
    }
    {
      const el: HTMLElementTagNameMap['ul'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._showHttpAuth(this._serverConfig))
    {
      {
        const el: HTMLElementTagNameMap['li'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['a'] = null!;
        useVars(el);
      }
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this._serverConfig)!.sshd}`);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(__f(this._serverConfig)!.receive)!.enable_signed_push}`);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (__f(__f(this._serverConfig)!.auth)!.use_contributor_agreements)
    {
      {
        const el: HTMLElementTagNameMap['li'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['a'] = null!;
        useVars(el);
      }
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
      useVars(el);
      el.name = `settings-menu-item`;
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['h1'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-1`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('class', `darkToggle`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `toggle`);
    }
    {
      const el: HTMLElementTagNameMap['paper-toggle-button'] = null!;
      useVars(el);
      el.checked = this._isDark;
      el.addEventListener('change', this._handleToggleDark.bind(this));
      el.addEventListener('click', this._onTapDarkToggle.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `darkThemeToggleLabel`);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `Profile`);
      el.setAttribute('class', `${this._computeHeaderClass(this._accountInfoChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `profile`);
    }
    {
      const el: HTMLElementTagNameMap['gr-account-info'] = null!;
      useVars(el);
      el.setAttribute('id', `accountInfo`);
      el.hasUnsavedChanges = this._accountInfoChanged;
      this._accountInfoChanged = el.hasUnsavedChanges;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.addEventListener('click', this._handleSaveAccountInfo.bind(this));
      el.disabled = !this._accountInfoChanged;
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `Preferences`);
      el.setAttribute('class', `${this._computeHeaderClass(this._prefsChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `preferences`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this._localPrefs)!.changes_per_page);
      el.addEventListener('change', this._handleChangesPerPage.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `changesPerPageSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this._localPrefs)!.date_format);
      el.addEventListener('change', this._handleDateFormat.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `dateTimeFormatSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this._localPrefs)!.time_format);
      el.addEventListener('change', this._handleTimeFormat.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `timeFormatSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this._localPrefs)!.email_strategy);
      el.addEventListener('change', this._handleEmailStrategy.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `emailNotificationsSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!this._convertToString(__f(this._localPrefs)!.email_format)}`);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this._localPrefs)!.email_format);
      el.addEventListener('change', this._handleEmailFormat.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `emailFormatSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this._localPrefs)!.default_base_for_merges}`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this._localPrefs)!.default_base_for_merges);
      el.addEventListener('change', this._handleDefaultBaseForMerges.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `defaultBaseForMergesSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `relativeDateInChangeTable`);
      el.setAttribute('checked', `${__f(this._localPrefs)!.relative_date_in_change_table}`);
      el.addEventListener('change', this._handleRelativeDateInChangeTable.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this._localPrefs)!.diff_view);
      el.addEventListener('change', this._handleDiffView.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `diffViewSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `showSizeBarsInFileList`);
      el.setAttribute('checked', `${__f(this._localPrefs)!.size_bar_in_change_table}`);
      el.addEventListener('change', this._handleShowSizeBarsInFileListChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `publishCommentsOnPush`);
      el.setAttribute('checked', `${__f(this._localPrefs)!.publish_comments_on_push}`);
      el.addEventListener('change', this._handlePublishCommentsOnPushChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `workInProgressByDefault`);
      el.setAttribute('checked', `${__f(this._localPrefs)!.work_in_progress_by_default}`);
      el.addEventListener('change', this._handleWorkInProgressByDefault.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `disableKeyboardShortcuts`);
      el.setAttribute('checked', `${__f(this._localPrefs)!.disable_keyboard_shortcuts}`);
      el.addEventListener('change', this._handleDisableKeyboardShortcutsChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `insertSignedOff`);
      el.setAttribute('checked', `${__f(this._localPrefs)!.signed_off_by}`);
      el.addEventListener('change', this._handleInsertSignedOff.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `savePrefs`);
      el.addEventListener('click', this._handleSavePreferences.bind(this));
      el.disabled = !this._prefsChanged;
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `DiffPreferences`);
      el.setAttribute('class', `${this._computeHeaderClass(this._diffPrefsChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `diffPreferences`);
    }
    {
      const el: HTMLElementTagNameMap['gr-diff-preferences'] = null!;
      useVars(el);
      el.setAttribute('id', `diffPrefs`);
      el.hasUnsavedChanges = this._diffPrefsChanged;
      this._diffPrefsChanged = el.hasUnsavedChanges;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `saveDiffPrefs`);
      el.addEventListener('click', this._handleSaveDiffPreferences.bind(this));
      el.setAttribute('disabled', `${!this._diffPrefsChanged}`);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `EditPreferences`);
      el.setAttribute('class', `${this._computeHeaderClass(this._editPrefsChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `editPreferences`);
    }
    {
      const el: HTMLElementTagNameMap['gr-edit-preferences'] = null!;
      useVars(el);
      el.setAttribute('id', `editPrefs`);
      el.hasUnsavedChanges = this._editPrefsChanged;
      this._editPrefsChanged = el.hasUnsavedChanges;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `saveEditPrefs`);
      el.addEventListener('click', this._handleSaveEditPreferences.bind(this));
      el.setAttribute('disabled', `${!this._editPrefsChanged}`);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `Menu`);
      el.setAttribute('class', `${this._computeHeaderClass(this._menuChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `menu`);
    }
    {
      const el: HTMLElementTagNameMap['gr-menu-editor'] = null!;
      useVars(el);
      el.menuItems = this._localMenu;
      this._localMenu = el.menuItems;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `saveMenu`);
      el.addEventListener('click', this._handleSaveMenu.bind(this));
      el.disabled = !this._menuChanged;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `resetMenu`);
      el.link = true;
      el.addEventListener('click', this._handleResetMenuButton.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `ChangeTableColumns`);
      el.setAttribute('class', `${this._computeHeaderClass(this._changeTableChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `changeTableColumns`);
    }
    {
      const el: HTMLElementTagNameMap['gr-change-table-editor'] = null!;
      useVars(el);
      el.showNumber = this._showNumber;
      this._showNumber = el.showNumber;
      el.serverConfig = this._serverConfig;
      el.displayedColumns = this._localChangeTableColumns;
      this._localChangeTableColumns = el.displayedColumns;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `saveChangeTable`);
      el.addEventListener('click', this._handleSaveChangeTable.bind(this));
      el.disabled = !this._changeTableChanged;
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `Notifications`);
      el.setAttribute('class', `${this._computeHeaderClass(this._watchedProjectsChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `watchedProjects`);
    }
    {
      const el: HTMLElementTagNameMap['gr-watched-projects-editor'] = null!;
      useVars(el);
      el.hasUnsavedChanges = this._watchedProjectsChanged;
      this._watchedProjectsChanged = el.hasUnsavedChanges;
      el.setAttribute('id', `watchedProjectsEditor`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.addEventListener('click', this._handleSaveWatchedProjects.bind(this));
      el.setAttribute('disabled', `${!this._watchedProjectsChanged}`);
      el.setAttribute('id', `_handleSaveWatchedProjects`);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `EmailAddresses`);
      el.setAttribute('class', `${this._computeHeaderClass(this._emailsChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `email`);
    }
    {
      const el: HTMLElementTagNameMap['gr-email-editor'] = null!;
      useVars(el);
      el.setAttribute('id', `emailEditor`);
      el.hasUnsavedChanges = this._emailsChanged;
      this._emailsChanged = el.hasUnsavedChanges;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.addEventListener('click', this._handleSaveEmails.bind(this));
      el.setAttribute('disabled', `${!this._emailsChanged}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `newEmail`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.setAttribute('class', `newEmailInput`);
      el.bindValue = this._newEmail;
      this._newEmail = convert(el.bindValue);
      el.addEventListener('keydown', this._handleNewEmailKeydown.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('class', `newEmailInput`);
      el.disabled = this._addingEmail;
      el.addEventListener('keydown', this._handleNewEmailKeydown.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('id', `verificationSentMessage`);
      el.setAttribute('hidden', `${!this._lastSentVerificationEmail}`);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    setTextContent(`${this._lastSentVerificationEmail}`);

    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.disabled = !this._computeAddEmailButtonEnabled(this._newEmail, this._addingEmail);
      el.addEventListener('click', this._handleAddEmailButton.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._showHttpAuth(this._serverConfig))
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['h2'] = null!;
        useVars(el);
        el.setAttribute('id', `HTTPCredentials`);
      }
      {
        const el: HTMLElementTagNameMap['fieldset'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['gr-http-password'] = null!;
        useVars(el);
        el.setAttribute('id', `httpPass`);
      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this._serverConfig)!.sshd}`);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `SSHKeys`);
      el.setAttribute('class', `${this._computeHeaderClass(this._keysChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['gr-ssh-editor'] = null!;
      useVars(el);
      el.setAttribute('id', `sshEditor`);
      el.hasUnsavedChanges = this._keysChanged;
      this._keysChanged = el.hasUnsavedChanges;
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(__f(this._serverConfig)!.receive)!.enable_signed_push}`);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `GPGKeys`);
      el.setAttribute('class', `${this._computeHeaderClass(this._gpgKeysChanged)}`);
    }
    {
      const el: HTMLElementTagNameMap['gr-gpg-editor'] = null!;
      useVars(el);
      el.setAttribute('id', `gpgEditor`);
      el.hasUnsavedChanges = this._gpgKeysChanged;
      this._gpgKeysChanged = el.hasUnsavedChanges;
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `Groups`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-group-list'] = null!;
      useVars(el);
      el.setAttribute('id', `groupList`);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `Identities`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-identities'] = null!;
      useVars(el);
      el.setAttribute('id', `identities`);
      el.serverConfig = this._serverConfig;
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (__f(__f(this._serverConfig)!.auth)!.use_contributor_agreements)
    {
      {
        const el: HTMLElementTagNameMap['h2'] = null!;
        useVars(el);
        el.setAttribute('id', `Agreements`);
      }
      {
        const el: HTMLElementTagNameMap['fieldset'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['gr-agreements-list'] = null!;
        useVars(el);
        el.setAttribute('id', `agreementsList`);
      }
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('id', `MailFilters`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('class', `filters`);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._getFilterDocsLink(this._docsBaseUrl)}`);
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['code'] = null!;
      useVars(el);
      el.setAttribute('class', `queryExample`);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['code'] = null!;
      useVars(el);
      el.setAttribute('class', `queryExample`);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['code'] = null!;
      useVars(el);
      el.setAttribute('class', `queryExample`);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['code'] = null!;
      useVars(el);
      el.setAttribute('class', `queryExample`);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['code'] = null!;
      useVars(el);
      el.setAttribute('class', `queryExample`);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['code'] = null!;
      useVars(el);
      el.setAttribute('class', `queryExample`);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['code'] = null!;
      useVars(el);
      el.setAttribute('class', `queryExample`);
    }
    {
      const el: HTMLElementTagNameMap['em'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
      useVars(el);
      el.name = `settings-screen`;
    }
  }
}

