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
const subscriptionSymbol = Symbol('subscriptions');

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
  ) {

  }

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


// From the TC39 Decorators proposal
interface ClassElement {
  kind: 'field'|'method';
  key: PropertyKey;
  placement: 'static'|'prototype'|'own';
  initializer?: Function;
  extras?: ClassElement[];
  finisher?: <T>(clazz: Constructor<T>) => undefined | Constructor<T>;
  descriptor?: PropertyDescriptor;
}

const legacySubscribe = <T>(obj$: Observable<T>, proto: Object, name: PropertyKey): void => {
  (proto as any)[subscriptionSymbol] = (proto as any)[subscriptionSymbol] || new Map();
  (proto as any)[subscriptionSymbol].set(name, obj$)
}

export const subscribe = <T>(obj$: Observable<T>) => {
  // tslint:disable-next-line:no-any decorator
  return (protoOrDescriptor: Object|ClassElement, name?: PropertyKey | undefined): any => {
    if (name !== undefined) {
      legacySubscribe<T>(obj$, protoOrDescriptor as Object, name);
    } else {
      throw new Error('TC-39 implementation has not been implemented yet');
    }
  }
}

export const subscribable = <T extends Constructor<LitElement>>(Base: T) => {
  return class extends Base {
    constructor(...args: any[]) {
      super(...args);
      const pendingSubscriptions = Base.prototype[subscriptionSymbol];
      if (pendingSubscriptions) {
        for (const [key, obs$] of pendingSubscriptions) {
          const subscription = new Subscription(this, key, obs$);
          this.addController(subscription);
        }
        this.requestUpdate();
      }
    }
  }
}

/**
 * Base class for Gerrit's lit-elements.
 *
 * Adds basic functionality that we want to have available in all Gerrit's
 * components.
 */
export class GrLitElement extends LitElement {
  [unsubscribe] = new Subject();

  /**
   * Hooks up an element property with an observable. Apart from subscribing it
   * makes sure that you are unsubscribed when the component is disconnected.
   * And it requests a template check when a new value comes in.
   *
   * Should be called from connectedCallback() such that you will be
   * re-subscribed when the component is re-connected.
   *
   * TODO: Maybe distinctUntilChanged should be applied to obs$?
   */
  subscribe<Key extends keyof this>(prop: Key, obs$: Observable<this[Key]>) {
    obs$.pipe(takeUntil(this[unsubscribe])).subscribe(value => {
      this[prop] = value;
    });
  }

  override disconnectedCallback() {
    this[unsubscribe].next();
    super.disconnectedCallback();
  }
};
