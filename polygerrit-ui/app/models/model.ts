/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject, Observable} from 'rxjs';
import {Finalizable} from '../services/registry';

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
  protected subject$: BehaviorSubject<T>;

  public state$: Observable<T>;

  constructor(initialState: T) {
    this.subject$ = new BehaviorSubject(initialState);
    this.state$ = this.subject$.asObservable();
  }

  getState() {
    return this.subject$.getValue();
  }

  setState(state: T) {
    this.subject$.next(state);
  }

  updateState(state: Partial<T>) {
    this.setState({...this.getState(), ...state});
  }

  finalize() {}
}
