/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import {
  EventCallback,
  EventEmitterService,
  UnsubscribeMethod,
} from './gr-event-interface';
/**
 * An lite implementation of
 * https://nodejs.org/api/events.html#events_class_eventemitter.
 *
 * This is unrelated to the native DOM events, you should use it when you want
 * to enable EventEmitter interface on any class.
 *
 * @example
 *
 * class YourClass extends EventEmitter {
 *   // now all instance of YourClass will have this EventEmitter interface
 * }
 *
 */
export class EventEmitter implements EventEmitterService {
  private _listenersMap = new Map<string, EventCallback[]>();

  /**
   * Register an event listener to an event.
   */
  addListener(eventName: string, cb: EventCallback): UnsubscribeMethod {
    if (!eventName || !cb) {
      console.warn('A valid eventname and callback is required!');
      return () => {};
    }

    const listeners = this._listenersMap.get(eventName) || [];
    listeners.push(cb);
    this._listenersMap.set(eventName, listeners);

    return () => {
      this.off(eventName, cb);
    };
  }

  /**
   * Alias for addListener.
   */
  on(eventName: string, cb: EventCallback): UnsubscribeMethod {
    return this.addListener(eventName, cb);
  }

  /**
   * Attach event handler only once. Automatically removed.
   */
  once(eventName: string, cb: EventCallback): UnsubscribeMethod {
    const onceWrapper = (...args: any[]) => {
      cb(...args);
      this.off(eventName, onceWrapper);
    };
    return this.on(eventName, onceWrapper);
  }

  /**
   * De-register an event listener to an event.
   */
  removeListener(eventName: string, cb: EventCallback): void {
    let listeners = this._listenersMap.get(eventName) || [];
    listeners = listeners.filter(listener => listener !== cb);
    this._listenersMap.set(eventName, listeners);
  }

  /**
   * Alias to removeListener
   */
  off(eventName: string, cb: EventCallback): void {
    this.removeListener(eventName, cb);
  }

  /**
   * Synchronously calls each of the listeners registered for
   * the event named eventName, in the order they were registered,
   * passing the supplied detail to each.
   *
   * @returns true if the event had listeners, false otherwise.
   */
  emit(eventName: string, detail: any): boolean {
    const listeners = this._listenersMap.get(eventName) || [];
    for (const listener of listeners) {
      try {
        listener(detail);
      } catch (e) {
        console.error(e);
      }
    }
    return listeners.length !== 0;
  }

  /**
   * Alias to emit.
   */
  dispatch(eventName: string, detail: any): boolean {
    return this.emit(eventName, detail);
  }

  /**
   * Remove listeners for a specific event or all.
   *
   * @param eventName if not provided, will remove all
   */
  removeAllListeners(eventName: string): void {
    if (eventName) {
      this._listenersMap.set(eventName, []);
    } else {
      this._listenersMap = new Map();
    }
  }
}
