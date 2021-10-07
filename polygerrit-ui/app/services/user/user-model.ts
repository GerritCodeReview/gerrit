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
import {AccountDetailInfo, PreferencesInfo} from '../../types/common';
import {BehaviorSubject, Observable} from 'rxjs';
import {map, distinctUntilChanged} from 'rxjs/operators';
import {createDefaultPreferences} from '../../constants/constants';

interface UserState {
  /**
   * Keeps being defined even when credentials have expired.
   */
  account?: AccountDetailInfo;
  preferences: PreferencesInfo;
}

const initialState: UserState = {
  preferences: createDefaultPreferences(),
};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const userState$: Observable<UserState> = privateState$;

export function updateAccount(account?: AccountDetailInfo) {
  const current = privateState$.getValue();
  privateState$.next({...current, account});
}

export function updatePreferences(preferences: PreferencesInfo) {
  const current = privateState$.getValue();
  privateState$.next({...current, preferences});
}

export const account$ = userState$.pipe(
  map(userState => userState.account),
  distinctUntilChanged()
);

export const preferences$ = userState$.pipe(
  map(userState => userState.preferences),
  distinctUntilChanged()
);

export const myTopMenuItems$ = preferences$.pipe(
  map(preferences => preferences?.my ?? []),
  distinctUntilChanged()
);

export const disableShortcuts$ = preferences$.pipe(
  map(preferences => preferences?.disable_keyboard_shortcuts ?? false),
  distinctUntilChanged()
);
