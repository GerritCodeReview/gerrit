/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

export type EventCallback = (...args: any) => void;
export type UnsubscribeMethod = () => void;

export interface EventEmitterService {
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
   * @returns true if the event had listeners, false otherwise.
   */
  emit(eventName: string, detail: any): boolean;

  /**
   * Alias to emit.
   */
  dispatch(eventName: string, detail: any): boolean;

  /**
   * Remove listeners for a specific event or all.
   *
   * @param eventName if not provided, will remove all
   */
  removeAllListeners(eventName: string): void;
}
