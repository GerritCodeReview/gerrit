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
(function(window) {
  'use strict';

  function GrEventHelper(element) {
    this.element = element;
    this._unsubscribers = [];
  }

  /**
   * Add a callback to arbitrary event.
   * The callback may return false to prevent event bubbling.
   *
   * @param {string} event Event name
   * @param {function(Event):boolean} callback
   * @return {function()} Unsubscribe function.
   */
  GrEventHelper.prototype.on = function(event, callback) {
    return this._listen(this.element, callback, {event});
  };

  /**
   * Alias of onClick
   *
   * @see onClick
   */
  GrEventHelper.prototype.onTap = function(callback) {
    return this._listen(this.element, callback);
  };

  /**
   * Add a callback to element click or touch.
   * The callback may return false to prevent event bubbling.
   *
   * @param {function(Event):boolean} callback
   * @return {function()} Unsubscribe function.
   */
  GrEventHelper.prototype.onClick = function(callback) {
    return this._listen(this.element, callback);
  };

  /**
   * Alias of captureClick
   *
   * @see captureClick
   */
  GrEventHelper.prototype.captureTap = function(callback) {
    return this._listen(this.element.parentElement, callback, {capture: true});
  };

  /**
   * Add a callback to element click or touch ahead of normal flow.
   * Callback is installed on parent during capture phase.
   * https://www.w3.org/TR/DOM-Level-3-Events/#event-flow
   * The callback may return false to cancel regular event listeners.
   *
   * @param {function(Event):boolean} callback
   * @return {function()} Unsubscribe function.
   */
  GrEventHelper.prototype.captureClick = function(callback) {
    return this._listen(this.element.parentElement, callback, {capture: true});
  };

  GrEventHelper.prototype._listen = function(container, callback, opt_options) {
    const capture = opt_options && opt_options.capture;
    const event = opt_options && opt_options.event || 'click';
    const handler = e => {
      if (e.path.indexOf(this.element) !== -1) {
        let mayContinue = true;
        try {
          mayContinue = callback(e);
        } catch (e) {
          console.warn(`Plugin error handing event: ${e}`);
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
  };

  window.GrEventHelper = GrEventHelper;
})(window);
