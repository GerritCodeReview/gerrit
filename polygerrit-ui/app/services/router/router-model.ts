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

import {BehaviorSubject, Observable} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {Finalizable} from '../registry';
import {NumericChangeId, PatchSetNum} from '../../types/common';

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

export class RouterModel implements Finalizable {
  private readonly privateState$ = new BehaviorSubject<RouterState>({});

  readonly routerView$: Observable<GerritView | undefined>;

  readonly routerChangeNum$: Observable<NumericChangeId | undefined>;

  readonly routerPatchNum$: Observable<PatchSetNum | undefined>;

  constructor() {
    this.routerView$ = this.privateState$.pipe(
      map(state => state.view),
      distinctUntilChanged()
    );
    this.routerChangeNum$ = this.privateState$.pipe(
      map(state => state.changeNum),
      distinctUntilChanged()
    );
    this.routerPatchNum$ = this.privateState$.pipe(
      map(state => state.patchNum),
      distinctUntilChanged()
    );
  }

  finalize() {}

  setState(state: RouterState) {
    this.privateState$.next(state);
  }

  updateState(partial: Partial<RouterState>) {
    this.privateState$.next({
      ...this.privateState$.getValue(),
      ...partial,
    });
  }

  get routerState$(): Observable<RouterState> {
    return this.privateState$;
  }
}
