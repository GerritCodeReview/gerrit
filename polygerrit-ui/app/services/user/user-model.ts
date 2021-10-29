/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {from, of, BehaviorSubject, Observable, Subscription} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {
  DiffPreferencesInfo as DiffPreferencesInfoAPI,
  DiffViewMode,
} from '../../api/diff';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  PreferencesInfo,
} from '../../types/common';
import {
  createDefaultPreferences,
  createDefaultDiffPrefs,
} from '../../constants/constants';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {DiffPreferencesInfo} from '../../types/diff';
import {Finalizable} from '../registry';
import {select} from '../../utils/observable-util';

export interface UserState {
  /**
   * Keeps being defined even when credentials have expired.
   */
  account?: AccountDetailInfo;
  preferences: PreferencesInfo;
  diffPreferences: DiffPreferencesInfo;
  capabilities?: AccountCapabilityInfo;
}

export class UserModel implements Finalizable {
  private readonly privateState$: BehaviorSubject<UserState> =
    new BehaviorSubject({
      preferences: createDefaultPreferences(),
      diffPreferences: createDefaultDiffPrefs(),
    });

  readonly account$: Observable<AccountDetailInfo | undefined> = select(
    this.privateState$,
    userState => userState.account
  );

  /** Note that this may still be true, even if credentials have expired. */
  readonly loggedIn$: Observable<boolean> = select(
    this.account$,
    account => !!account
  );

  readonly capabilities$: Observable<AccountCapabilityInfo | undefined> =
    select(this.userState$, userState => userState.capabilities);

  readonly isAdmin$: Observable<boolean> = select(
    this.capabilities$,
    capabilities => capabilities?.administrateServer ?? false
  );

  readonly preferences$: Observable<PreferencesInfo> = select(
    this.privateState$,
    userState => userState.preferences
  );

  readonly diffPreferences$: Observable<DiffPreferencesInfo> = select(
    this.privateState$,
    userState => userState.diffPreferences
  );

  readonly preferenceDiffViewMode$: Observable<DiffViewMode> = select(
    this.preferences$,
    preference => preference.diff_view ?? DiffViewMode.SIDE_BY_SIDE
  );

  private readonly subscriptions: Subscription[] = [];

  get userState$(): Observable<UserState> {
    return this.privateState$;
  }

  constructor(readonly restApiService: RestApiService) {
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
    this.subscriptions.splice(0, this.subscriptions.length);
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
      .then((response: Response) => {
        this.restApiService.getResponseObject(response).then(obj => {
          const newPrefs = obj as unknown as DiffPreferencesInfo;
          if (!newPrefs) return;
          this.setDiffPreferences(newPrefs);
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
    const current = this.privateState$.getValue();
    this.privateState$.next({...current, preferences});
  }

  setDiffPreferences(diffPreferences: DiffPreferencesInfo) {
    const current = this.privateState$.getValue();
    this.privateState$.next({...current, diffPreferences});
  }

  setCapabilities(capabilities?: AccountCapabilityInfo) {
    const current = this.privateState$.getValue();
    this.privateState$.next({...current, capabilities});
  }

  private setAccount(account?: AccountDetailInfo) {
    const current = this.privateState$.getValue();
    this.privateState$.next({...current, account});
  }
}
