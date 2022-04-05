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
import {PolymerElement} from '@polymer/polymer/polymer-element';

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
 * A resolved dependency is a function that when called will return the actual
 * value for that dependency.
 *
 * A resolved dependency is guaranteed to be resolved during a components
 * connected lifetime. If no ancestor provided a value for the dependency, then
 * the resolved dependency will throw an error if the value is accessed.
 * Therefore, the following is safe-by-construction as long as it happens
 * within a components connected lifetime:
 *
 * ```
 *    serviceRef().fooServiceMethod()
 * ```
 *
 * Dependency Injection
 * ---
 *
 * Ancestor components will inject the dependencies that a child component
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
 *   provide(this, fooToken, () => new FooImpl(barRef()));
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
 *   - Dependency injection leverages ReactiveControllers whose lifetime
 *     mirror that of the component
 *   - Parent components' connected lifetime is guaranteed to include the
 *     connected lifetime of child components.
 *   - Dependency provider factories are only called during the lifetime of the
 *     component that provides the value.
 *
 * Best practices
 * ===
 *  - Provide dependencies in or before connectedCallback
 *  - Verify that isConnected is true when accessing a dependency after an
 *    await.
 *
 * Type Safety
 * ---
 *
 * Dependency injection is guaranteed npmtype-safe by construction due to the
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
export type DependencyToken<ValueType> = symbol & {__type__: ValueType};

/**
 * Defines a unique dependency token for a given type.  The string provided
 * is purely for debugging and does not need to be unique.
 *
 * Example usage:
 *   const token = define<FooService>('foo-service');
 */
export function define<ValueType>(name: string) {
  return Symbol(name) as unknown as DependencyToken<ValueType>;
}

/**
 * A provider for a value.
 */
export type Provider<T> = () => T;

// Symbols to cache the providers and resolvers to avoid duplicate registration.
const PROVIDERS_SYMBOL = Symbol('providers');
const RESOLVERS_SYMBOL = Symbol('resolvers');

interface Registrations {
  [PROVIDERS_SYMBOL]?: Map<
    DependencyToken<unknown>,
    DependencyProvider<unknown>
  >;
  [RESOLVERS_SYMBOL]?: Map<DependencyToken<unknown>, Provider<unknown>>;
}
/**
 * A producer of a dependency expresses this as a need that results in a promise
 * for the given dependency.
 */
export function provide<T>(
  host: ReactiveControllerHost & HTMLElement & Registrations,
  dependency: DependencyToken<T>,
  provider: Provider<T>
) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const hostProviders = (host[PROVIDERS_SYMBOL] ||= new Map());
  if (hostProviders.has(dependency)) {
    host.removeController(hostProviders.get(dependency));
  }
  const controller = new DependencyProvider<T>(host, dependency, provider);
  hostProviders.set(dependency, provider);
  host.addController(controller);
}

/**
 * A consumer of a service will resolve a given dependency token. The resolved
 * dependency is returned as a simple function that can be called to access
 * the injected value.
 */
export function resolve<T>(
  host: ReactiveControllerHost & HTMLElement & Registrations,
  dependency: DependencyToken<T>
): Provider<T> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const hostResolvers = (host[RESOLVERS_SYMBOL] ||= new Map());
  let resolver = hostResolvers.get(dependency);
  if (!resolver) {
    const controller = new DependencySubscriber(host, dependency);
    host.addController(controller);
    resolver = () => controller.get();
    hostResolvers.set(dependency, resolver);
  }
  return resolver;
}

/**
 * Because Polymer doesn't (yet) depend on ReactiveControllerHost, this adds a
 * work-around base-class to make this work for Polymer.
 */
export class DIPolymerElement
  extends PolymerElement
  implements ReactiveControllerHost
{
  private readonly ___controllers: ReactiveController[] = [];

  override connectedCallback() {
    for (const c of this.___controllers) {
      c.hostConnected?.();
    }
    super.connectedCallback();
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    for (const c of this.___controllers) {
      c.hostDisconnected?.();
    }
  }

  addController(controller: ReactiveController) {
    this.___controllers.push(controller);

    if (this.isConnected) controller.hostConnected?.();
  }

  removeController(controller: ReactiveController) {
    const idx = this.___controllers.indexOf(controller);
    if (idx < 0) return;
    this.___controllers?.splice(idx, 1);
  }

  requestUpdate() {}

  get updateComplete(): Promise<boolean> {
    return Promise.resolve(true);
  }
}

/**
 * A callback for a value.
 */
type Callback<T> = (value: T) => void;

/**
 * A Dependency Request gets sent by an element to ask for a dependency.
 */
export interface DependencyRequest<T> {
  readonly dependency: DependencyToken<T>;
  readonly callback: Callback<T>;
}

declare global {
  interface HTMLElementEventMap {
    /**
     * An 'request-dependency' can be emitted by any element which desires a
     * dependency to be injected by an external provider.
     */
    'request-dependency': DependencyRequestEvent<unknown>;
  }
  interface DocumentEventMap {
    /**
     * An 'request-dependency' can be emitted by any element which desires a
     * dependency to be injected by an external provider.
     */
    'request-dependency': DependencyRequestEvent<unknown>;
  }
}

/**
 * Dependency Consumers fire DependencyRequests in the form of
 * DependencyRequestEvent
 */
export class DependencyRequestEvent<T>
  extends Event
  implements DependencyRequest<T>
{
  public constructor(
    public readonly dependency: DependencyToken<T>,
    public readonly callback: Callback<T>
  ) {
    super('request-dependency', {bubbles: true, composed: true});
  }
}

/**
 * A resolved dependency is valid within the econnectd lifetime of a component,
 * namely between connectedCallback and disconnectedCallback.
 */
interface ResolvedDependency<T> {
  get(): T;
}

export class DependencyError<T> extends Error {
  constructor(public readonly dependency: DependencyToken<T>, message: string) {
    super(message);
  }
}

class DependencySubscriber<T>
  implements ReactiveController, ResolvedDependency<T>
{
  private value?: T;

  private resolved = false;

  constructor(
    private readonly host: ReactiveControllerHost & HTMLElement,
    private readonly dependency: DependencyToken<T>
  ) {}

  get() {
    this.checkResolved();
    return this.value!;
  }

  hostConnected() {
    this.host.dispatchEvent(
      new DependencyRequestEvent(this.dependency, (value: T) => {
        this.resolved = true;
        this.value = value;
      })
    );
    this.checkResolved();
  }

  checkResolved() {
    if (this.resolved) return;
    const dep = this.dependency.description;
    const tag = this.host.tagName;
    const msg = `Could not resolve dependency '${dep}' in '${tag}'`;
    throw new DependencyError(this.dependency, msg);
  }

  hostDisconnected() {
    this.value = undefined;
    this.resolved = false;
  }
}

class DependencyProvider<T> implements ReactiveController {
  private value?: T;

  constructor(
    private readonly host: ReactiveControllerHost & HTMLElement,
    private readonly dependency: DependencyToken<T>,
    private readonly provider: Provider<T>
  ) {}

  hostConnected() {
    // Delay construction in case the provider has its own dependencies.
    this.value = this.provider();
    this.host.addEventListener('request-dependency', this.fullfill);
  }

  hostDisconnected() {
    this.host.removeEventListener('request-dependency', this.fullfill);
    this.value = undefined;
  }

  private readonly fullfill = (ev: DependencyRequestEvent<unknown>) => {
    if (ev.dependency !== this.dependency) return;
    ev.stopPropagation();
    ev.preventDefault();
    ev.callback(this.value!);
  };
}
