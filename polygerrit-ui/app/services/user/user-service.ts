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
import {from, of, Subscription} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  PreferencesInfo,
} from '../../types/common';
import {
  account$,
  updateAccount,
  updatePreferences,
  updateDiffPreferences,
  updateCapabilities,
} from './user-model';
import {
  createDefaultPreferences,
  createDefaultDiffPrefs,
} from '../../constants/constants';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {DiffPreferencesInfo} from '../../types/diff';
import {Finalizable} from '../registry';

export class UserService implements Finalizable {
  private readonly subscriptions: Subscription[] = [];

  constructor(readonly restApiService: RestApiService) {
    this.subscriptions.push(
      from(this.restApiService.getAccount()).subscribe(
        (account?: AccountDetailInfo) => {
          updateAccount(account);
        }
      )
    );
    this.subscriptions.push(
      account$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultPreferences());
            return from(this.restApiService.getPreferences());
          })
        )
        .subscribe((preferences?: PreferencesInfo) => {
          updatePreferences(preferences ?? createDefaultPreferences());
        })
    );
    this.subscriptions.push(
      account$
        .pipe(
          switchMap(account => {
            if (!account) return of(undefined);
            return from(this.restApiService.getAccountCapabilities());
          })
        )
        .subscribe((capabilities?: AccountCapabilityInfo) => {
          updateCapabilities(capabilities);
        })
    );
    this.subscriptions.push(
      account$
        .pipe(
          switchMap(account => {
            if (!account) return of(createDefaultDiffPrefs());
            return from(this.restApiService.getDiffPreferences());
          })
        )
        .subscribe((diffPrefs?: DiffPreferencesInfo) => {
          updateDiffPreferences(diffPrefs ?? createDefaultDiffPrefs());
        })
    );
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
        updatePreferences(newPrefs);
      });
  }

  updateDiffPreference(diffPrefs: DiffPreferencesInfo) {
    return this.restApiService
      .saveDiffPreferences(diffPrefs)
      .then((response: Response) => {
        this.restApiService.getResponseObject(response).then(obj => {
          const newPrefs = obj as unknown as DiffPreferencesInfo;
          if (!newPrefs) return;
          updateDiffPreferences(newPrefs);
        });
      });
  }

  getDiffPreferences() {
    return this.restApiService.getDiffPreferences().then(prefs => {
      if (!prefs) return;
      updateDiffPreferences(prefs);
    });
  }
}
