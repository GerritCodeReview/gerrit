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

/** @polymerBehavior Gerrit.FireBehavior */
export const FireBehavior = {
  /**
   * Dispatches a custom event with an optional detail value.
   *
   * @param {string} type Name of event type.
   * @param {*=} detail Detail value containing event-specific
   *   payload.
   * @param {{ bubbles: (boolean|undefined), cancelable: (boolean|undefined),
   *     composed: (boolean|undefined) }=}
   *  options Object specifying options.  These may include:
   *  `bubbles` (boolean, defaults to `true`),
   *  `cancelable` (boolean, defaults to false), and
   *  `composed` (boolean, defaults to true).
   * @return {!Event} The new event that was fired.
   * @override
   */
  fire(type, detail, options) {
    console.warn('\'fire\' is deprecated, please use dispatchEvent instead!');
    options = options || {};
    detail = (detail === null || detail === undefined) ? {} : detail;
    const event = new Event(type, {
      bubbles: options.bubbles === undefined ? true : options.bubbles,
      cancelable: Boolean(options.cancelable),
      composed: options.composed === undefined ? true: options.composed,
    });
    event.detail = detail;
    this.dispatchEvent(event);
    return event;
  },
};

// TODO(dmfilippov) Remove the following lines with assignments
// Plugins can use the behavior because it was accessible with
// the global Gerrit... variable. To avoid breaking changes in plugins
// temporary assign global variables.
window.Gerrit = window.Gerrit || {};
window.Gerrit.FireBehavior = FireBehavior;
