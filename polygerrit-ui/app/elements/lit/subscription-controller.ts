/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {Observable, Subscription} from 'rxjs';
import {Provider} from '../../models/dependency';

export class SubscriptionError extends Error {
  constructor(message: string) {
    super(message);
  }
}

/**
 * Enables components to simply hook up a property with an Observable like so:
 *
 * subscribe(this, () => obs$, x => (this.prop = x));
 */
export function subscribe<T>(
  host: ReactiveControllerHost & HTMLElement,
  provider: Provider<Observable<T>>,
  callback: (t: T) => void
) {
  if (host.isConnected)
    throw new Error(
      'Subscriptions should happen before a component is connected'
    );
  const controller = new SubscriptionController(provider, callback);
  host.addController(controller);
}

export class SubscriptionController<T> implements ReactiveController {
  private sub?: Subscription;

  constructor(
    private readonly provider: Provider<Observable<T>>,
    private readonly callback: (t: T) => void
  ) {}

  hostConnected() {
    this.sub = this.provider().subscribe(v => this.update(v));
  }

  update(value: T) {
    this.callback(value);
  }

  hostDisconnected() {
    this.sub?.unsubscribe();
  }
}
