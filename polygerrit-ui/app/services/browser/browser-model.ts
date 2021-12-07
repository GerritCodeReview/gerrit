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
import {BehaviorSubject, Observable, combineLatest} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {Finalizable} from '../registry';
import {define} from '../dependency';
import {DiffViewMode} from '../../api/diff';
import {UserModel} from '../user/user-model';

// This value is somewhat arbitrary and not based on research or calculations.
const MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX = 850;

export interface BrowserState {
  /**
   * We maintain the screen width in the state so that the app can react to
   * changes in the width such as automatically changing to unified diff view
   */
  screenWidth?: number;
}

const initialState: BrowserState = {};

export const browserModelToken = define<BrowserModel>('browser-model');

export class BrowserModel implements Finalizable {
  private readonly privateState$ = new BehaviorSubject(initialState);

  readonly diffViewMode$: Observable<DiffViewMode>;

  get viewState$(): Observable<BrowserState> {
    return this.privateState$;
  }

  constructor(readonly userModel: UserModel) {
    const screenWidth$ = this.privateState$.pipe(
      map(
        state =>
          !!state.screenWidth &&
          state.screenWidth < MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX
      ),
      distinctUntilChanged()
    );
    // TODO; Inject the UserModel once preferenceDiffViewMode$ has moved to
    // the user model.
    this.diffViewMode$ = combineLatest([
      screenWidth$,
      userModel.preferenceDiffViewMode$,
    ]).pipe(
      map(([isScreenTooSmall, preferenceDiffViewMode]) => {
        if (isScreenTooSmall) return DiffViewMode.UNIFIED;
        else return preferenceDiffViewMode;
      }),
      distinctUntilChanged()
    );
  }

  /* Observe the screen width so that the app can react to changes to it */
  observeWidth() {
    return new ResizeObserver(entries => {
      entries.forEach(entry => {
        this.setScreenWidth(entry.contentRect.width);
      });
    });
  }

  // Private but used in tests.
  setScreenWidth(screenWidth: number) {
    this.privateState$.next({...this.privateState$.getValue(), screenWidth});
  }

  finalize() {}
}
