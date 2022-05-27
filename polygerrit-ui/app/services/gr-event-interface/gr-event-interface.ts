/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../registry';
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type EventCallback = (...args: any) => void;
export type UnsubscribeMethod = () => void;

export interface EventEmitterService extends Finalizable {
  /**
   * Register an event listener to an event.
   */
  addListener(eventName: string, cb: EventCallback): UnsubscribeMethod;

  /**
   * Alias for addListener.
   */
  on(eventName: string, cb: EventCallback): UnsubscribeMethod;

  /**
   * Attach event handler only once. Automatically removed.
   */
  once(eventName: string, cb: EventCallback): UnsubscribeMethod;

  /**
   * De-register an event listener to an event.
   */
  removeListener(eventName: string, cb: EventCallback): void;

  /**
   * Alias to removeListener
   */
  off(eventName: string, cb: EventCallback): void;

  /**
   * Synchronously calls each of the listeners registered for
   * the event named eventName, in the order they were registered,
   * passing the supplied detail to each.
   *
   * @return true if the event had listeners, false otherwise.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  emit(eventName: string, detail: any): boolean;

  /**
   * Alias to emit.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  dispatch(eventName: string, detail: any): boolean;

  /**
   * Remove listeners for a specific event or all.
   *
   * @param eventName if not provided, will remove all
   */
  removeAllListeners(eventName: string): void;
}
