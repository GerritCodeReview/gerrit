/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {Observable, Subscription} from 'rxjs';

/**
 * Enables components to simply hook up a property with an Observable like so:
 *
 * subscribe(this, obs$, x => (this.prop = x));
 */
export function subscribe<T>(
  host: ReactiveControllerHost,
  obs$: Observable<T>,
  setProp: (t: T) => void
) {
  host.addController(new SubscriptionController(obs$, setProp));
}

export class SubscriptionController<T> implements ReactiveController {
  private sub?: Subscription;

  constructor(
    private readonly obs$: Observable<T>,
    private readonly setProp: (t: T) => void
  ) {}

  hostConnected() {
    this.sub = this.obs$.subscribe(this.setProp);
  }

  hostDisconnected() {
    this.sub?.unsubscribe();
  }
}
