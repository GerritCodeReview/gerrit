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
import {appContext} from '../app-context';
import {AccountDetailInfo, PreferencesInfo} from '../../types/common';
import {from, combineLatest, of} from 'rxjs';
import {account$, updateAccount, updatePreferences} from './user-model';
import {switchMap} from 'rxjs/operators';
import {createDefaultPreferences} from '../../constants/constants';

export class UserService {
  private readonly restApiService = appContext.restApiService;

  constructor() {
    from(this.restApiService.getAccount()).subscribe(
      (account?: AccountDetailInfo) => {
        updateAccount(account);
      }
    );
    combineLatest([of(1), account$])
      .pipe(
        switchMap(([_, account]) => {
          if (!account) return of(createDefaultPreferences());
          return from(this.restApiService.getPreferences());
        })
      )
      .subscribe((preferences?: PreferencesInfo) => {
        updatePreferences(preferences ?? createDefaultPreferences());
      });
  }
}
