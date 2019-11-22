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

(function (window) {
  'use strict';

  /**
   * This callback is displayed as part of the Requester class.
   * @callback eventCallback
   * @param {*} event
   */

  /**
   * An lite implementation on
   * https://nodejs.org/api/events.html#events_class_eventemitter.
   */
  class EventEmitter {
    /**
     * Shared events pool.
     * @type {!Object<string, Array<eventCallback>>}
     */
    _listenersMap = new Map();

    /**
     * Register an event listener to an event.
     *
     * @param {string} eventName
     * @param {eventCallback} cb
     */
    addListener(eventName, cb) {
      if (!eventName || !cb) {
        console.warn('A valid eventname and callback is required!');
        return;
      }

      const listeners = this._listenersMap.get(eventName) || [];
      listeners.push(cb);
      this._listenersMap.set(eventName, listeners);

      // return an unsubscribe method
      return () => {
        this.off(eventName, cb);
      };
    }

    // Alias for addListener.
    on(eventName, cb) {
      return this.addListener(eventName, cb);
    }

    // Attach event handler only once. Automatically removed.
    once(eventName, cb) {
      const onceWrapper = (...args) => {
        cb(...args);
        this.off(eventName, onceWrapper);
      }
      return this.on(eventName, onceWrapper);
    }

    /**
     * De-register an event listener to an event.
     *
     * @param {string} eventName
     * @param {eventCallback} cb
     */
    removeListener(eventName, cb) {
      let listeners = this._listenersMap.get(eventName) || [];
      listeners = listeners.filter(listener => listener !== cb);
      this._listenersMap.set(eventName, listeners);
    }

    // Alias to removeListener
    off(eventName, cb) {
      this.removeListener(eventName, cb);
    }

    /**
     * Synchronously calls each of the listeners registered for
     * the event named eventName, in the order they were registered,
     * passing the supplied detail to each.
     *
     * Returns true if the event had listeners, false otherwise.
     *
     * @param {string} eventName
     * @param {*} detail
     */
    emit(eventName, detail) {
      const listeners = this._listenersMap.get(eventName) || [];
      for (const listener of listeners) {
        try {
          listener(detail);
        } catch (e) { }
      }
      return listeners.length !== 0;
    }

    // Alias to emit.
    dispatch(eventName, detail) {
      return this.emit(eventName, detail);
    }

    /**
     * Remove listeners for a specific event or all.
     *
     * @param {string} eventName if not provided, will remove all
     */
    removeAllListeners(eventName) {
      if (eventName) {
        this._listenersMap.set(eventName, []);
      } else {
        this._listenersMap = new Map();
      }
    }

    /**
     * Extend any object to a EventEmitter interface.
     *
     * This is needed to support current code base,
     * Once moved to classes, should use class extends instead.
     *
     * @param {!Object} obj
     */
    static mixin(obj) {
      const _eventEmitter = new EventEmitter();
      ['addListener',
        'dispatch',
        'emit',
        'off',
        'on',
        'once',
        'removeAllListeners',
        'removeListener'
      ].forEach(method => {
        if (obj[method]) {
          throw new Error('Incompatible interface detected!');
        }
        obj[method] = _eventEmitter[method].bind(_eventEmitter);
      });
      return obj;
    }
  }

  window.EventEmitter = EventEmitter;
})(window);