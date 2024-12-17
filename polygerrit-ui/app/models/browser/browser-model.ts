/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest} from 'rxjs';
import {define} from '../dependency';
import {DiffViewMode} from '../../api/diff';
import {UserModel} from '../user/user-model';
import {Model} from '../base/model';
import {select} from '../../utils/observable-util';

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

export class BrowserModel extends Model<BrowserState> {
  private readonly isScreenTooSmall$ = select(
    this.state$,
    state =>
      !!state.screenWidth &&
      state.screenWidth < MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX
  );

  readonly diffViewMode$: Observable<DiffViewMode>;

  constructor(readonly userModel: UserModel) {
    super(initialState);

    this.diffViewMode$ = select(
      combineLatest([
        this.isScreenTooSmall$,
        this.userModel.preferenceDiffViewMode$,
      ]),
      ([isScreenTooSmall, preferenceDiffViewMode]) =>
        isScreenTooSmall ? DiffViewMode.UNIFIED : preferenceDiffViewMode
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
    this.updateState({screenWidth});
  }
}
