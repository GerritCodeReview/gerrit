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
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  PreferencesInfo,
} from '../../types/common';
import {BehaviorSubject, Observable} from 'rxjs';
import {map, distinctUntilChanged} from 'rxjs/operators';
import {
  createDefaultPreferences,
  createDefaultDiffPrefs,
} from '../../constants/constants';
import {DiffPreferencesInfo, DiffViewMode} from '../../api/diff';

interface UserState {
  /**
   * Keeps being defined even when credentials have expired.
   */
  account?: AccountDetailInfo;
  preferences: PreferencesInfo;
  diffPreferences: DiffPreferencesInfo;
  capabilities?: AccountCapabilityInfo;
}

const initialState: UserState = {
  preferences: createDefaultPreferences(),
  diffPreferences: createDefaultDiffPrefs(),
};

const privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next({...initialState});
}

export function _testOnly_setState(state: UserState) {
  privateState$.next(state);
}

export function _testOnly_getState() {
  return privateState$.getValue();
}

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

export function updateDiffPreferences(diffPreferences: DiffPreferencesInfo) {
  const current = privateState$.getValue();
  privateState$.next({...current, diffPreferences});
}

export function updateCapabilities(capabilities?: AccountCapabilityInfo) {
  const current = privateState$.getValue();
  privateState$.next({...current, capabilities});
}

export const account$ = userState$.pipe(
  map(userState => userState.account),
  distinctUntilChanged()
);

export const preferences$ = userState$.pipe(
  map(userState => userState.preferences),
  distinctUntilChanged()
);

export const diffPreferences$ = userState$.pipe(
  map(userState => userState.diffPreferences),
  distinctUntilChanged()
);

export const preferenceDiffViewMode$ = preferences$.pipe(
  map(preference => preference.diff_view ?? DiffViewMode.SIDE_BY_SIDE),
  distinctUntilChanged()
);

export const myTopMenuItems$ = preferences$.pipe(
  map(preferences => preferences?.my ?? []),
  distinctUntilChanged()
);

export const sizeBarInChangeTable$ = preferences$.pipe(
  map(prefs => !!prefs?.size_bar_in_change_table),
  distinctUntilChanged()
);

export const disableShortcuts$ = preferences$.pipe(
  map(preferences => preferences?.disable_keyboard_shortcuts ?? false),
  distinctUntilChanged()
);

export const capabilities$ = userState$.pipe(
  map(userState => userState.capabilities),
  distinctUntilChanged()
);

export const isAdmin$ = capabilities$.pipe(
  map(capabilities => capabilities?.administrateServer ?? false),
  distinctUntilChanged()
);
