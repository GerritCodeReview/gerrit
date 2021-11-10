/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {NumericChangeId, PatchSetNum} from '../../types/common';
import {BehaviorSubject, Observable} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';

export enum GerritView {
  ADMIN = 'admin',
  AGREEMENTS = 'agreements',
  CHANGE = 'change',
  DASHBOARD = 'dashboard',
  DIFF = 'diff',
  DOCUMENTATION_SEARCH = 'documentation-search',
  EDIT = 'edit',
  GROUP = 'group',
  PLUGIN_SCREEN = 'plugin-screen',
  REPO = 'repo',
  ROOT = 'root',
  SEARCH = 'search',
  SETTINGS = 'settings',
  TOPIC = 'topic',
}

export interface RouterState {
  view?: GerritView;
  changeNum?: NumericChangeId;
  patchNum?: PatchSetNum;
}

// TODO: Figure out how to best enforce immutability of all states. Use Immer?
// Use DeepReadOnly?
const initialState: RouterState = {};

const privateState$ = new BehaviorSubject<RouterState>(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next({...initialState});
}

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const routerState$: Observable<RouterState> = privateState$;

// Must only be used by the router service or whatever is in control of this
// model.
// TODO: Consider keeping params of type AppElementParams entirely in the state
export function updateState(
  view?: GerritView,
  changeNum?: NumericChangeId,
  patchNum?: PatchSetNum
) {
  privateState$.next({
    ...privateState$.getValue(),
    view,
    changeNum,
    patchNum,
  });
}

export const routerView$ = routerState$.pipe(
  map(state => state.view),
  distinctUntilChanged()
);

export const routerChangeNum$ = routerState$.pipe(
  map(state => state.changeNum),
  distinctUntilChanged()
);

export const routerPatchNum$ = routerState$.pipe(
  map(state => state.patchNum),
  distinctUntilChanged()
);
