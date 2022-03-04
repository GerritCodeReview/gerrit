/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {Observable, Subscription} from 'rxjs';

const SUBSCRIPTION_SYMBOL = Symbol('subscriptions');

// Checks whether a subscription can be added. Returns true if it can be added,
// return false if it's already present.
// Subscriptions are stored on the host so they have the same life-time as the
// host.
function checkSubscription<T>(
  host: ReactiveControllerHost,
  obs$: Observable<T>,
  setProp: (t: T) => void
): boolean {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const hostSubscriptions = ((host as any)[SUBSCRIPTION_SYMBOL] ||= new Map());
  if (!hostSubscriptions.has(obs$)) hostSubscriptions.set(obs$, new Set());
  const obsSubscriptions = hostSubscriptions.get(obs$);
  if (obsSubscriptions.has(setProp)) return false;
  obsSubscriptions.add(setProp);
  return true;
}

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
  if (!checkSubscription(host, obs$, setProp)) return;
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
