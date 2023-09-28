/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject, Observable, Subscription} from 'rxjs';
import {Finalizable} from '../../services/registry';
import {deepEqual} from '../../utils/deep-util';

/**
 * A Model stores a value <T> and controls changes to that value via `subject$`
 * while allowing others to subscribe to value updates via the `state$`
 * Observable.
 *
 * Typically a given Model subclass will provide:
 *   1. An initial value. If there is no good default to start with, then
 *      include `undefined` in the type `T`.
 *   2. "reducers": functions for users to request changes to the value
 *   3. "selectors": convenient sub-Observables that only contain updates for a
 *      nested property from the value
 *
 *  Any new subscriber will immediately receive the current value.
 */
export abstract class Model<T> implements Finalizable {
  /**
   * rxjs does not like `next()` being called on a subject during processing of
   * another `next()` call. So make sure that state updates complete before
   * starting another one.
   */
  private stateUpdateInProgress = false;

  private subject$: BehaviorSubject<T>;

  public state$: Observable<T>;

  protected subscriptions: Subscription[] = [];

  constructor(initialState: T) {
    this.subject$ = new BehaviorSubject(initialState);
    this.state$ = this.subject$.asObservable();
  }

  getState() {
    return this.subject$.getValue();
  }

  setState(state: T) {
    if (this.stateUpdateInProgress) {
      setTimeout(() => this.setState(state));
      return;
    }
    if (deepEqual(state, this.getState())) return;
    try {
      this.stateUpdateInProgress = true;
      this.subject$.next(state);
    } finally {
      this.stateUpdateInProgress = false;
    }
  }

  updateState(state: Partial<T>) {
    if (this.stateUpdateInProgress) {
      setTimeout(() => this.updateState(state));
      return;
    }
    this.setState({...this.getState(), ...state});
  }

  finalize() {
    this.subject$.complete();
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }
}
