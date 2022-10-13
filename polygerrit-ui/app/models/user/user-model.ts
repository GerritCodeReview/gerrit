/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {from, of, Observable} from 'rxjs';
import {filter, switchMap} from 'rxjs/operators';
import {
  DiffPreferencesInfo as DiffPreferencesInfoAPI,
  DiffViewMode,
} from '../../api/diff';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  EditPreferencesInfo,
  PreferencesInfo,
} from '../../types/common';
import {
  createDefaultPreferences,
  createDefaultDiffPrefs,
  createDefaultEditPrefs,
  AppTheme,
} from '../../constants/constants';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {DiffPreferencesInfo} from '../../types/diff';
import {Finalizable} from '../../services/registry';
import {select} from '../../utils/observable-util';
import {Model} from '../model';
import {notUndefined} from '../../types/types';

export interface UserState {
  /**
   * Keeps being defined even when credentials have expired.
   *
   * `undefined` can mean that the app is still starting up and we have not
   * tried loading an account object yet. If you want to wait until the
   * `account` is known, then use `accountLoaded` below.
   */
  account?: AccountDetailInfo;
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

export class UserModel extends Model<UserState> implements Finalizable {
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
  ).pipe(filter(notUndefined));

  readonly diffPreferences$: Observable<DiffPreferencesInfo> = select(
    this.state$,
    userState => userState.diffPreferences
  ).pipe(filter(notUndefined));

  readonly editPreferences$: Observable<EditPreferencesInfo> = select(
    this.state$,
    userState => userState.editPreferences
  ).pipe(filter(notUndefined));

  readonly preferenceDiffViewMode$: Observable<DiffViewMode> = select(
    this.preferences$,
    preference => preference.diff_view ?? DiffViewMode.SIDE_BY_SIDE
  );

  readonly preferenceTheme$: Observable<AppTheme> = select(
    this.preferences$,
    preference => preference.theme
  );

  readonly preferenceChangesPerPage$: Observable<number> = select(
    this.preferences$,
    preference => preference.changes_per_page
  );

  constructor(readonly restApiService: RestApiService) {
    super({
      accountLoaded: false,
    });
    this.subscriptions = [
      from(this.restApiService.getAccount()).subscribe(
        (account?: AccountDetailInfo) => {
          this.setAccount(account);
        }
      ),
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
        this.restApiService.getResponseObject(response).then(obj => {
          const newPrefs = obj as unknown as DiffPreferencesInfo;
          if (!newPrefs) return;
          this.setDiffPreferences(newPrefs);
        })
      );
  }

  updateEditPreference(editPrefs: EditPreferencesInfo) {
    return this.restApiService
      .saveEditPreferences(editPrefs)
      .then((response: Response) => {
        this.restApiService.getResponseObject(response).then(obj => {
          const newPrefs = obj as unknown as EditPreferencesInfo;
          if (!newPrefs) return;
          this.setEditPreferences(newPrefs);
        });
      });
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
}
