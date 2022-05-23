/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {Subscription} from 'rxjs';
import {Finalizable} from '../../services/registry';
import {select} from '../../utils/observable-util';
import {define} from '../dependency';
import {Model} from '../model';

export interface ViewState {
  selectedIndexForDashboard: Map<string, number>;
}

const initialState: ViewState = {
  selectedIndexForDashboard: new Map(),
};

export const viewModelToken = define<ViewModel>('view-model');

export class ViewModel extends Model<ViewState> implements Finalizable {
  private subscriptions: Subscription[] = [];

  constructor() {
    super(initialState);
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }

  /** Required for testing */
  getState() {
    return this.subject$.getValue();
  }

  public readonly selectedIndexForDashboard$ = select(
    this.state$,
    state => state.selectedIndexForDashboard
  );

  setSelectedIndexForDashboard(user: string, index: number) {
    const current = this.subject$.getValue();
    current.selectedIndexForDashboard = new Map(
      current.selectedIndexForDashboard
    );
    current.selectedIndexForDashboard.set(user, index);
    this.subject$.next(current);
  }
}
