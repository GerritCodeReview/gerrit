/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {from, Observable, of} from 'rxjs';
import {filter, switchMap, tap} from 'rxjs/operators';
import {
  DiffPreferencesInfo as DiffPreferencesInfoAPI,
  DiffViewMode,
} from '../../api/diff';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  EditPreferencesInfo,
  EmailInfo,
  PreferencesInfo,
  TopMenuItemInfo,
} from '../../types/common';
import {
  AppTheme,
  ColumnNames,
  createDefaultDiffPrefs,
  createDefaultEditPrefs,
  createDefaultPreferences,
} from '../../constants/constants';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {DiffPreferencesInfo} from '../../types/diff';
import {select} from '../../utils/observable-util';
import {define} from '../dependency';
import {Model} from '../base/model';
import {isDefined} from '../../types/types';
import {readJSONResponsePayload} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

export function changeTablePrefs(prefs: Partial<PreferencesInfo>) {
  const cols = prefs.change_table ?? [];
  if (cols.length === 0) return Object.values(ColumnNames);
  return cols
    .map(column => (column === 'Project' ? ColumnNames.REPO : column))
    .map(column => (column === ' Status ' ? ColumnNames.STATUS : column));
}

export interface UserState {
  /**
   * Keeps being defined even when credentials have expired.
   *
   * `undefined` can mean that the app is still starting up and we have not
   * tried loading an account object yet. If you want to wait until the
   * `account` is known, then use `accountLoaded` below.
   */
  account?: AccountDetailInfo;
  emails?: EmailInfo[];
  /**
   * Starts as `false` and switches to `true` after the first `getAccount` call.
   * A common use case for this is to wait with loading or doing something until
   * we know whether the user is logged in or not, see `loadedAccount$` below.
   *
   * This value cannot change back to `false` once it has become `true`.
   *
   * This value does *not* indicate whether the user is logged in or whether an
   * `account` object is available. If the first `getAccount()` call returns
   * `undefined`, then `accountLoaded` still becomes true, even if `account`
   * stays `undefined`.
   */
  accountLoaded: boolean;
  preferences?: PreferencesInfo;
  diffPreferences?: DiffPreferencesInfo;
  editPreferences?: EditPreferencesInfo;
  capabilities?: AccountCapabilityInfo;
}

export const userModelToken = define<UserModel>('user-model');

export class UserModel extends Model<UserState> {
  /**
   * Note that the initially emitted `undefined` value can mean "not loaded
   * the account into object yet" or "user is not logged in". Consider using
   * `loadedAccount$` below.
   *
   * TODO: Maybe consider changing all usages to `loadedAccount$`.
   */
  readonly account$: Observable<AccountDetailInfo | undefined> = select(
    this.state$,
    userState => userState.account
  );

  readonly emails$: Observable<EmailInfo[] | undefined> = select(
    this.state$,
    userState => userState.emails
  ).pipe(
    tap(emails => {
      if (emails === undefined) this.loadEmails();
    })
  );

  /**
   * Only emits once we have tried to actually load the account. Note that
   * this does not initially emit a value.
   *
   * So if this emits `undefined`, then you actually know that the user is not
   * logged in. And for logged in users you will never get an initial
   * `undefined` emission.
   */
  readonly loadedAccount$: Observable<AccountDetailInfo | undefined> = select(
    this.state$.pipe(filter(s => s.accountLoaded)),
    userState => userState.account
  );

  /** Note that this may still be true, even if credentials have expired. */
  readonly loggedIn$: Observable<boolean> = select(
    this.account$,
    account => !!account
  );

  readonly capabilities$: Observable<AccountCapabilityInfo | undefined> =
    select(this.state$, userState => userState.capabilities);

  readonly isAdmin$: Observable<boolean> = select(
    this.capabilities$,
    capabilities => capabilities?.administrateServer ?? false
  );

  readonly preferences$: Observable<PreferencesInfo> = select(
    this.state$,
    userState => userState.preferences
  ).pipe(filter(isDefined));

  readonly diffPreferences$: Observable<DiffPreferencesInfo> = select(
    this.state$,
    userState => userState.diffPreferences
  ).pipe(filter(isDefined));

  readonly editPreferences$: Observable<EditPreferencesInfo> = select(
    this.state$,
    userState => userState.editPreferences
  ).pipe(filter(isDefined));

  readonly preferenceDiffViewMode$: Observable<DiffViewMode> = select(
    this.preferences$,
    preference => preference.diff_view ?? DiffViewMode.SIDE_BY_SIDE
  );

  readonly preferenceTheme$: Observable<AppTheme> = select(
    this.preferences$,
    preference => preference.theme
  );

  readonly myMenuItems$: Observable<TopMenuItemInfo[]> = select(
    this.preferences$,
    preference => preference?.my ?? []
  );

  readonly preferenceChangesPerPage$: Observable<number> = select(
    this.preferences$,
    preference => preference.changes_per_page
  );

  constructor(readonly restApiService: RestApiService) {
    super({
      accountLoaded: false,
    });
    this.loadAccount();
    this.subscriptions = [
      this.loadedAccount$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultPreferences());
            return from(this.restApiService.getPreferences());
          })
        )
        .subscribe((preferences?: PreferencesInfo) => {
          this.setPreferences(preferences ?? createDefaultPreferences());
        }),
      this.loadedAccount$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultDiffPrefs());
            return from(this.restApiService.getDiffPreferences());
          })
        )
        .subscribe((diffPrefs?: DiffPreferencesInfoAPI) => {
          this.setDiffPreferences(diffPrefs ?? createDefaultDiffPrefs());
        }),
      this.loadedAccount$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultEditPrefs());
            return from(this.restApiService.getEditPreferences());
          })
        )
        .subscribe((editPrefs?: EditPreferencesInfo) => {
          this.setEditPreferences(editPrefs ?? createDefaultEditPrefs());
        }),
      this.loadedAccount$
        .pipe(
          switchMap(account => {
            if (!account) return of(undefined);
            return from(this.restApiService.getAccountCapabilities());
          })
        )
        .subscribe((capabilities?: AccountCapabilityInfo) => {
          this.setCapabilities(capabilities);
        }),
    ];
  }

  updatePreferences(prefs: Partial<PreferencesInfo>) {
    this.setPreferences({
      ...(this.getState().preferences ?? createDefaultPreferences()),
      ...prefs,
    });
    return this.restApiService
      .savePreferences(prefs)
      .then((newPrefs: PreferencesInfo | undefined) => {
        if (!newPrefs) return;
        this.setPreferences(newPrefs);
        return newPrefs;
      });
  }

  updateDiffPreference(diffPrefs: DiffPreferencesInfo) {
    return this.restApiService
      .saveDiffPreferences(diffPrefs)
      .then((response: Response) =>
        readJSONResponsePayload(response).then(obj => {
          const newPrefs = obj.parsed as unknown as DiffPreferencesInfo;
          if (!newPrefs) return;
          this.setDiffPreferences(newPrefs);
        })
      );
  }

  updateEditPreference(editPrefs: EditPreferencesInfo) {
    return this.restApiService
      .saveEditPreferences(editPrefs)
      .then((response: Response) =>
        readJSONResponsePayload(response).then(obj => {
          const newPrefs = obj.parsed as unknown as EditPreferencesInfo;
          if (!newPrefs) return;
          this.setEditPreferences(newPrefs);
        })
      );
  }

  getDiffPreferences() {
    return this.restApiService.getDiffPreferences().then(prefs => {
      if (!prefs) return;
      this.setDiffPreferences(prefs);
    });
  }

  setPreferences(preferences: PreferencesInfo) {
    this.updateState({preferences});
  }

  setDiffPreferences(diffPreferences: DiffPreferencesInfo) {
    this.updateState({diffPreferences});
  }

  setEditPreferences(editPreferences: EditPreferencesInfo) {
    this.updateState({editPreferences});
  }

  setCapabilities(capabilities?: AccountCapabilityInfo) {
    this.updateState({capabilities});
  }

  setAccount(account?: AccountDetailInfo) {
    this.updateState({account, accountLoaded: true});
  }

  private setAccountEmails(emails?: EmailInfo[]) {
    this.updateState({emails});
  }

  loadAccount(noCache?: boolean) {
    if (noCache) this.restApiService.invalidateAccountsDetailCache();
    return this.restApiService.getAccount().then(account => {
      this.setAccount(account);
    });
  }

  loadEmails(noCache?: boolean) {
    if (noCache) this.restApiService.invalidateAccountsEmailCache();
    return this.restApiService.getAccountEmails().then(emails => {
      this.setAccountEmails(emails);
    });
  }
}
