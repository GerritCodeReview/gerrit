/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

interface EventWithPath extends Event {
  path?: HTMLElement[];
}

export interface ListenOptions {
  event?: string;
  capture?: boolean;
}

export class GrEventHelper {
  constructor(readonly element: HTMLElement) {}

  _unsubscribers: any[] = [];

  /**
   * Add a callback to arbitrary event.
   * The callback may return false to prevent event bubbling.
   *
   * @param {string} event Event name
   * @param {function(Event):boolean} callback
   * @return {function()} Unsubscribe function.
   */
  on(event: string, callback: (event: Event) => boolean) {
    return this._listen(this.element, callback, {event});
  }

  /**
   * Alias of onClick
   *
   * @see onClick
   */
  onTap(callback: (event: Event) => boolean) {
    return this._listen(this.element, callback);
  }

  /**
   * Add a callback to element click or touch.
   * The callback may return false to prevent event bubbling.
   *
   * @param {function(Event):boolean} callback
   * @return {function()} Unsubscribe function.
   */
  onClick(callback: (event: Event) => boolean) {
    return this._listen(this.element, callback);
  }

  /**
   * Alias of captureClick
   *
   * @see captureClick
   */
  captureTap(callback: (event: Event) => boolean) {
    const parent = this.element.parentElement!;
    return this._listen(parent, callback, {capture: true});
  }

  /**
   * Add a callback to element click or touch ahead of normal flow.
   * Callback is installed on parent during capture phase.
   * https://www.w3.org/TR/DOM-Level-3-Events/#event-flow
   * The callback may return false to cancel regular event listeners.
   */
  captureClick(callback: (event: Event) => boolean) {
    const parent = this.element.parentElement!;
    return this._listen(parent, callback, {capture: true});
  }

  _listen(
    container: HTMLElement,
    callback: (event: Event) => boolean,
    opt_options?: ListenOptions | null
  ) {
    const capture = !!opt_options && !!opt_options.capture;
    const event = (opt_options && opt_options.event) || 'click';
    const handler = (e: EventWithPath) => {
      if (!e.path) return;
      if (e.path.indexOf(this.element) !== -1) {
        let mayContinue = true;
        try {
          mayContinue = callback(e);
        } catch (exception) {
          console.warn(`Plugin error handing event: ${exception}`);
        }
        if (mayContinue === false) {
          e.stopImmediatePropagation();
          e.stopPropagation();
          e.preventDefault();
        }
      }
    };
    container.addEventListener(event, handler, capture);
    const unsubscribe = () =>
      container.removeEventListener(event, handler, capture);
    this._unsubscribers.push(unsubscribe);
    return unsubscribe;
  }
}
