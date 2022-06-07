/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {from, of, Observable, Subscription} from 'rxjs';
import {switchMap} from 'rxjs/operators';
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
} from '../../constants/constants';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {DiffPreferencesInfo} from '../../types/diff';
import {Finalizable} from '../../services/registry';
import {select} from '../../utils/observable-util';
import {Model} from '../model';

export interface UserState {
  /**
   * Keeps being defined even when credentials have expired.
   */
  account?: AccountDetailInfo;
  preferences: PreferencesInfo;
  diffPreferences: DiffPreferencesInfo;
  editPreferences: EditPreferencesInfo;
  capabilities?: AccountCapabilityInfo;
}

export class UserModel extends Model<UserState> implements Finalizable {
  readonly account$: Observable<AccountDetailInfo | undefined> = select(
    this.state$,
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
  );

  readonly diffPreferences$: Observable<DiffPreferencesInfo> = select(
    this.state$,
    userState => userState.diffPreferences
  );

  readonly editPreferences$: Observable<EditPreferencesInfo> = select(
    this.state$,
    userState => userState.editPreferences
  );

  readonly preferenceDiffViewMode$: Observable<DiffViewMode> = select(
    this.preferences$,
    preference => preference.diff_view ?? DiffViewMode.SIDE_BY_SIDE
  );

  private subscriptions: Subscription[] = [];

  constructor(readonly restApiService: RestApiService) {
    super({
      preferences: createDefaultPreferences(),
      diffPreferences: createDefaultDiffPrefs(),
      editPreferences: createDefaultEditPrefs(),
    });
    this.subscriptions = [
      from(this.restApiService.getAccount()).subscribe(
        (account?: AccountDetailInfo) => {
          this.setAccount(account);
        }
      ),
      this.account$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultPreferences());
            return from(this.restApiService.getPreferences());
          })
        )
        .subscribe((preferences?: PreferencesInfo) => {
          this.setPreferences(preferences ?? createDefaultPreferences());
        }),
      this.account$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultDiffPrefs());
            return from(this.restApiService.getDiffPreferences());
          })
        )
        .subscribe((diffPrefs?: DiffPreferencesInfoAPI) => {
          this.setDiffPreferences(diffPrefs ?? createDefaultDiffPrefs());
        }),
      this.account$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultEditPrefs());
            return from(this.restApiService.getEditPreferences());
          })
        )
        .subscribe((editPrefs?: EditPreferencesInfo) => {
          this.setEditPreferences(editPrefs ?? createDefaultEditPrefs());
        }),
      this.account$
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

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }

  updatePreferences(prefs: Partial<PreferencesInfo>) {
    this.restApiService
      .savePreferences(prefs)
      .then((newPrefs: PreferencesInfo | undefined) => {
        if (!newPrefs) return;
        this.setPreferences(newPrefs);
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
    const current = this.subject$.getValue();
    this.subject$.next({...current, preferences});
  }

  setDiffPreferences(diffPreferences: DiffPreferencesInfo) {
    const current = this.subject$.getValue();
    this.subject$.next({...current, diffPreferences});
  }

  setEditPreferences(editPreferences: EditPreferencesInfo) {
    const current = this.subject$.getValue();
    this.subject$.next({...current, editPreferences});
  }

  setCapabilities(capabilities?: AccountCapabilityInfo) {
    const current = this.subject$.getValue();
    this.subject$.next({...current, capabilities});
  }

  private setAccount(account?: AccountDetailInfo) {
    const current = this.subject$.getValue();
    this.subject$.next({...current, account});
  }
}
