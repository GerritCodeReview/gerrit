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

import {BehaviorSubject, Observable} from 'rxjs';

/**
 * A Model stores a value <T> and controls changes to that value via `subject$`
 * while allowing others to subscribe to value updates via the `state$`
 * Observable.
 *
 * Typically a given Model subclass will provide:
 *   1. an initial value
 *   2. "reducers": functions for users to request changes to the value
 *   3. "selectors": convenient sub-Observables that only contain updates for a
 *          nested property from the value
 *
 *  Any new subscriber will immediately receive the current value.
 */
export abstract class Model<T> {
  protected subject$: BehaviorSubject<T>;

  public state$: Observable<T>;

  constructor(initialState: T) {
    this.subject$ = new BehaviorSubject(initialState);
    this.state$ = this.subject$.asObservable();
  }
}
