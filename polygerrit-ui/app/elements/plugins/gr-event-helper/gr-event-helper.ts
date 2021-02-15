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
import {
  EventHelperPluginApi,
  UnsubscribeCallback,
} from '../../../api/event-helper';

export interface ListenOptions {
  event?: string;
  capture?: boolean;
}

export class GrEventHelper implements EventHelperPluginApi {
  constructor(readonly element: HTMLElement) {}

  /**
   * Add a callback to arbitrary event.
   * The callback may return false to prevent event bubbling.
   */
  on(event: string, callback: (event: Event) => boolean) {
    return this._listen(this.element, callback, {event});
  }

  /**
   * Alias for @see onClick
   */
  onTap(callback: (event: Event) => boolean) {
    return this.onClick(callback);
  }

  /**
   * Add a callback to element click or touch.
   * The callback may return false to prevent event bubbling.
   */
  onClick(callback: (event: Event) => boolean) {
    return this._listen(this.element, callback);
  }

  /**
   * Alias for @see captureClick
   */
  captureTap(callback: (event: Event) => boolean) {
    return this.captureClick(callback);
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
    options?: ListenOptions | null
  ): UnsubscribeCallback {
    const capture = options?.capture;
    const event = options?.event || 'click';
    const handler = (e: Event) => {
      const path = e.composedPath();
      if (!path) return;
      if (path.indexOf(this.element) !== -1) {
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
    return unsubscribe;
  }
}
