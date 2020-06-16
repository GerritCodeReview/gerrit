/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import {descendedFromClass} from '../../utils/dom-util.js';

export const DomUtilBehavior = {
  /**
   * Are any ancestors of the element (or the element itself) members of the
   * given class.
   *
   * @param {!Element} element
   * @param {string} className
   * @param {Element=} opt_stopElement If provided, stop traversing the
   *     ancestry when the stop element is reached. The stop element's class
   *     is not checked.
   * @return {boolean}
   */
  descendedFromClass(element, className, opt_stopElement) {
    console.warn('DomUtilBehavior is deprecated.' +
     'Use descendedFromClass from utils directly.');
    return descendedFromClass(element, className, opt_stopElement);
  },
};

// TODO(dmfilippov) Remove the following lines with assignments
// Plugins can use the behavior because it was accessible with
// the global Gerrit... variable. To avoid breaking changes in plugins
// temporary assign global variables.
window.Gerrit = window.Gerrit || {};
window.Gerrit.DomUtilBehavior = DomUtilBehavior;
