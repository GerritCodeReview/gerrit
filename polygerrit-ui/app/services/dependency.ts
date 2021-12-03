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
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {PolymerElement} from '@polymer/polymer';

/**
 * This module provides the ability to do dependency injection in components.
 * It provides 3 functions that are for the purpose of dependency injection.
 *
 * Definitions
 * ---
 * A component's "connected lifetime" consists of the span between
 * `super.connectedCallback` and `super.disconnectedCallback`.
 *
 * Dependency Definition
 * ---
 *
 * A token for a dependency of type FooService is defined as follows:
 *
 *   const fooToken = define<FooService>('some name');
 *
 * Dependency Resolution
 * ---
 *
 * To get the value of a dependency, a component requests a resolved dependency
 *
 * ```
 *   private readonly serviceRef = resolve(this, fooToken);
 * ```
 *
 * A resolved dependency is guaranteed to be resolved during a components
 * connected lifetime. Therefore, the following is safe-by-construction as long
 * as it happens within a components connected lifetime:
 *
 * ```
 *    serviceRef.get().fooServiceMethod()
 * ```
 *
 * If desired, it's also possible to set up work to be done for as soon as the
 * dependency resolves:
 *
 * ```
 *    serviceRef.wait().then(service => ...);
 * ```
 *
 * Dependency Injection
 * ---
 *
 * Parent components will inject the dependencies that a child component
 * requires by providing factories for those values.
 *
 *
 * To provide a dependency, a component needs to specify the following prior
 * to finishing its connectedCallback:
 *
 * ```
 *   provide(this, fooToken, () => new FooImpl())
 * ```
 * Dependencies are injected as factories in case the construction of them
 * depends on other dependencies further up the component chain.  For instance,
 * if the construction of FooImpl needed a BarService, then it could look
 * something like this:
 *
 * ```
 *   const barRef = resolve(this, barToken);
 *   provide(this, fooToken, () => new FooImpl(barRef.get()));
 * ```
 *
 * Lifetime guarantees
 * ---
 * A resolved dependency is valid for the duration of its component's connected
 * lifetime.
 *
 * Internally, this is guaranteed by the following:
 *
 *   - Dependency injection relies on using dom-events which work synchronously.
 *   - Dependency injection leverages ReactiveControllers whose own
 *     lifetime mirror that of the component
 *   - Parent components' connected lifetime is guaranteed to include the
 *     connected lifetime of child components.
 *   - Dependency provider factories are only called during the lifetime of the
 *     component that provides the value.
 *
 * Type Safety
 * ---
 *
 * Dependency injection is guaranteed type-safe by construction due to the
 * typing of the token used to tie together dependency providers and dependency
 * consumers.
 *
 * Two tokens can never be equal because of how they are created. And both the
 * consumer and provider logic of dependencies relies on the type of dependency
 * token.
 */

/**
 * A dependency-token is a unique key. It's typed by the type of the value the
 * dependency needs.
 */
export type DependencyToken<ValueType> = Symbol & {__value__: ValueType};

export type DependencyValue<Dep extends DependencyToken<unknown>> =
  Dep extends DependencyToken<infer ValueType> ? ValueType : never;

/**
 * Defines a unique dependency token for a given type.
 *
 * Example usage:
 *   const token = define<FooService>('foo-service');
 */
export function define<ValueType>(name: string) {
  return Symbol(name) as unknown as DependencyToken<ValueType>;
}

/**
 * A dependency callback returns a cleanup function in case the dependency
 * changes.
 */
type DependencyCallback<Dep extends DependencyToken<unknown>> =
  (value: DependencyValue<Dep>) => void;

/**
* A Dependency Request gets sent by an element to ask for a dependency.
*/
export interface DependencyRequest<Dep extends DependencyToken<unknown>> {
  readonly dependency: Dep
  readonly callback: DependencyCallback<Dep>
}

declare global {
  interface HTMLElementEventMap {
    /**
     * An 'request-dependency' can be emitted by any element which desires a
     * dependency to be injected by an external provider.
     */
    'request-dependency': DependencyRequestEvent<DependencyToken<unknown>>,
  }
}

/**
 * Dependency Consumers fire DependencyRequests in the form of
 * DependencyRequestEvent
 */
export class DependencyRequestEvent<Dep extends DependencyToken<unknown>>
  extends Event
  implements DependencyRequest<Dep>
{

  public constructor(
    public readonly dependency: Dep,
    public readonly callback: DependencyCallback<Dep>,
  ) {
    super('request-dependency', {bubbles: true, composed: true});
  }
}

/**
 * A resolved dependency is valid within the connectd lifetime of a component,
 * namely between connectedCallback and disconnectedCallback.
 */
export interface ResolvedDependency<Dep extends DependencyToken<unknown>> {
  get(): DependencyValue<Dep>
  wait(): Promise<DependencyValue<Dep>>
}

export class DependencyError<D extends DependencyToken<unknown>> extends Error {
  constructor(public readonly dependency: D, message: string) {
    super(message);
  }
}

/**
 * A consumer of a service will resolve.
 */
export function resolve<Dep extends DependencyToken<unknown>>(
  host: ReactiveControllerHost & HTMLElement,
  dependency: Dep): ResolvedDependency<Dep> {
  const controller = new DependencySubscriber(host, dependency);
  host.addController(controller);
  return controller;
}

class DependencySubscriber<D extends DependencyToken<unknown>>
  implements ReactiveController, ResolvedDependency<D> {

  private value?: DependencyValue<D>;
  private waiters: DependencyCallback<D>[] = [];

  constructor(
    private readonly host: ReactiveControllerHost & HTMLElement,
    private readonly dependency: D,
  ) { }

  get() {
    if (this.value === undefined) {
      throw new DependencyError(this.dependency, 'Could not resolve dependency');
    }
    return this.value;
  }

  wait() {
    if (this.value !== undefined) return Promise.resolve(this.value);
    return new Promise<DependencyValue<D>>(resolve => {
      this.waiters.push(resolve);
    });
  }

  hostConnected() {
    this.host.dispatchEvent(new DependencyRequestEvent(
      this.dependency,
      (value: DependencyValue<D>) => { this.resolve(value) }
    ));
  }

  hostDisconnected() {
    this.value = undefined;
  }

  private resolve(value: DependencyValue<D>) {
    this.value = value;
    const waiters = this.waiters;
    this.waiters = [];
    for (const waiter of waiters) {
      waiter(value);
    }
  }
}

class DependencyProvider<D extends DependencyToken<unknown>>
  implements ReactiveController {
    private value?: DependencyValue<D>;

    constructor(
      private readonly host: ReactiveControllerHost & HTMLElement,
      private readonly dependency: D,
      private readonly provider: () => DependencyValue<D>) {}

    hostConnected() {
      // Delay construction in case the provider has its own dependencies.
      this.value = this.provider();
      this.host.addEventListener('request-dependency', this.fullfill);
    }

    hostDisconnected() {
      this.host.removeEventListener('request-dependency', this.fullfill);
      this.value = undefined;
    }

    private readonly fullfill =
      (ev: DependencyRequestEvent<DependencyToken<unknown>>) => {
      if (ev.dependency !== this.dependency) return;
      ev.stopPropagation();
      ev.preventDefault();
      ev.callback(this.value!);
    }
}

/**
 * A producer of a dependency expresses this as a need that results in a promise
 * for the given dependency.
 * @param host
 * @param dependency
 * @returns
 */
export function provide<D extends DependencyToken<unknown>>(
  host: ReactiveControllerHost & EventTarget,
  dependency: D,
  provider: () => DependencyValue<D>) {
  host.addController(new DependencyProvider<D>(host, dependency, provider));
}


/**
 * Because Polymer doesn't (yet) depend on ReactiveElement, this adds a
 * work-around base-class to make this work for Polymer.
 */
export class DIPolymerElement extends PolymerElement
  implements ReactiveControllerHost {
  private __controllers: ReactiveController[] = [];
  private __connected = false;

  override connectedCallback() {
    super.connectedCallback();
    this.__connected = true;
    for (const c of this.__controllers) {
      c.hostConnected?.();
    }
  }

  override disconnectedCallback() {
    for (const c of this.__controllers) {
      c.hostDisconnected?.();
    }
    this.__connected = false;
    super.disconnectedCallback();
  }

  addController(controller: ReactiveController) {
    (this.__controllers ??= []).push(controller);

    if (this.__connected) controller.hostConnected?.();
  }

  removeController(controller: ReactiveController) {
    this.__controllers?.splice(this.__controllers.indexOf(controller) >>> 0, 1);
  }

  requestUpdate() {}
  
  get updateComplete(): Promise<boolean> {
    return Promise.resolve(true);
  }
}
