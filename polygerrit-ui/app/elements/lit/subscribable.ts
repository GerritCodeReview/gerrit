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
import {LitElement, ReactiveController, ReactiveControllerHost} from 'lit';
import {Observable, Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

const unsubscribe = Symbol('unsubscrive');
const subscriptions = Symbol('subscriptions');

export type Constructor<T> = {
  // tslint:disable-next-line:no-any
  new (...args: any[]): T
};

export class Subscription<O extends ReactiveControllerHost, K extends keyof O> implements ReactiveController {
  private [unsubscribe] = new Subject();

  constructor(
    private readonly host: O,
    private readonly name: K,
    private readonly obs$: Observable<O[K]>
  ) {}

  hostConnected() {
    this.obs$.pipe(takeUntil(this[unsubscribe])).subscribe(value => {
      this.host[this.name] = value
      this.host.requestUpdate();
    });
  }

  hostDisconnected() {
    this[unsubscribe].next();
  }
}

// TODO: Ensure typing of T matches this[name]
// TODO: Ensure that you can only @subscribe in @subscribable classes.
export const subscribe = <T>(obj$: Observable<T>) => {
  // tslint:disable-next-line:no-any decorator
  return (proto: Object, name: PropertyKey): void => {
    (proto as any)[subscriptions] = (proto as any)[subscriptions] || new Map();
    (proto as any)[subscriptions].set(name, obj$)
  }
}

export const subscribable = <T extends Constructor<LitElement>>(Base: T) => {
  return class extends Base {
    constructor(...args: any[]) {
      super(...args);
      const pendingSubscriptions = Base.prototype[subscriptions];
      if (pendingSubscriptions) {
        for (const [key, obs$] of pendingSubscriptions) {
          this.subscribe(key, obs$)
        }
        this.requestUpdate();
      }
    }

    subscribe<Key extends keyof this>(prop: Key, obs$: Observable<this[Key]>) {
      const subscription = new Subscription(this, prop, obs$);
      this.addController(subscription);
    }
  }
}
