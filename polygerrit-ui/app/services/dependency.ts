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
import {LitElement, ReactiveController, ReactiveControllerHost} from 'lit';

/**
 * A dependency is defined by unique key of a given KeyType. It's typed
 * by the type of the value the dependency needs.
 */
export type DependencyToken<KeyType, ValueType> = KeyType & {__value__: ValueType};

export type DependencyValue<Dep extends DependencyToken<unknown, unknown>> =
  Dep extends DependencyToken<unknown, infer ValueType> ? ValueType : never;


export function define<ValueType>(dependency: unknown) {
  return dependency as DependencyToken<typeof dependency, ValueType>;
}

/**
 * A dependency callback returns a cleanup function in case the dependency
 * changes.
 */
type DependencyCallback<Dep extends DependencyToken<unknown, unknown>> =
  (value: DependencyValue<Dep>) => void;

/**
* A Dependency Request is what gets sent by an element to ask for a dependency.
*/
export interface DependencyRequest<Dep extends DependencyToken<unknown, unknown>> {
  readonly dependency: Dep
  readonly callback: DependencyCallback<Dep>
}

declare global {
  interface HTMLElementEventMap {
    /**
     * An 'inject-dependency' can be emitted by any element which desires a
     * dependency to be injected by an external provider.
     */
    'request-dependency': DependencyRequestEvent<DependencyToken<unknown, unknown>>,
  }
}

/**
 * Dependency Consumers fire DependencyRequests in the form of
 * DependencyRequestEvent
 */
export class DependencyRequestEvent<Dep extends DependencyToken<unknown, unknown>>
  extends Event
  implements DependencyRequest<Dep>
{
  /**
   *
   * @param dependency the dependency that is requested
   * @param callback the callback that should be invoked when the dependency is available
   */
  public constructor(
    public readonly dependency: Dep,
    public readonly callback: DependencyCallback<Dep>,
  ) {
    super('request-dependency', {bubbles: true, composed: true});
  }
}

/**
 * A resolved dependency is valid within the lifetime of a component, namely
 * between connectedCallback and disconnectedCallback.
 */
export interface ResolvedDependency<Dep extends DependencyToken<unknown, unknown>> {
  get(): DependencyValue<Dep>
  wait(): Promise<DependencyValue<Dep>>
}

export class DependencyError<D extends DependencyToken<unknown, unknown>> extends Error {
  constructor(public readonly dependency: D, message: string) {
    super(message);
  }
}

/**
 * A consumer of a dependency expresses this as a need that results in a promise
 * for the given dependency.
 * @param host
 * @param dependency
 * @returns
 */
export function need<Dep extends DependencyToken<unknown, unknown>>(
  host: ReactiveControllerHost & HTMLElement,
  dependency: Dep): ResolvedDependency<Dep> {
  const controller = new DependencySubscriber(host, dependency);
  host.addController(controller);
  return controller;
}

class DependencySubscriber<D extends DependencyToken<unknown, unknown>>
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
    if (this.value) return Promise.resolve(this.value);
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

class DependencyProvider<D extends DependencyToken<unknown, unknown>>
  implements ReactiveController {
    private value?: DependencyValue<D>;

    constructor(
      private readonly host: ReactiveControllerHost & HTMLElement,
      private readonly dependency: D,
      private readonly provider: () => DependencyValue<D>) {}

    hostConnected() {
      this.value = this.provider();
      this.host.addEventListener('request-dependency', this.fullfill);
    }

    hostDisconnected() {
      this.host.removeEventListener('request-dependency', this.fullfill);
      this.value = undefined;
    }

    private readonly fullfill =
      (ev: DependencyRequestEvent<DependencyToken<unknown, unknown>>) => {
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
export function provide<D extends DependencyToken<unknown, unknown>>(
  host: ReactiveControllerHost & HTMLElement,
  dependency: D,
  provider: () => DependencyValue<D>) {
  host.addController(
    new DependencyProvider<D>(host, dependency, provider));
}


// ----
// DEMO
// ----

// FOO SERVICE FILE
interface FooService {

}

const fooDependency = define<FooService>(Symbol('foo'));

// FOO Provider

class FooServiceImpl implements FooService {}

export class Provider extends LitElement {
  constructor() {
    super();
    provide(this, fooDependency, () => new FooServiceImpl());
  }
}

// FOO CONSUMER
export class Consumer extends LitElement {

  private readonly fooService = need(this, fooDependency);

  override connectedCallback() {
    super.connectedCallback();
    // Should be valid by now
    this.fooService.get();
  }
}
