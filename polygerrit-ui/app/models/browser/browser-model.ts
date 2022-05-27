/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {Finalizable} from '../../services/registry';
import {define} from '../dependency';
import {DiffViewMode} from '../../api/diff';
import {UserModel} from '../user/user-model';
import {Model} from '../model';

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

export class BrowserModel extends Model<BrowserState> implements Finalizable {
  readonly diffViewMode$: Observable<DiffViewMode>;

  constructor(readonly userModel: UserModel) {
    super(initialState);
    const screenWidth$ = this.state$.pipe(
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
    this.subject$.next({...this.subject$.getValue(), screenWidth});
  }

  finalize() {}
}
