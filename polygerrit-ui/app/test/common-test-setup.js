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

/**
 * Helps looking up the proper iron-input element during the Polymer 2
 * transition. Polymer 2 uses the <iron-input> element, while Polymer 1 uses
 * the nested <input is="iron-input"> element.
 */
window.ironInput = function(element) {
  return Polymer.dom(element).querySelector(
      Polymer.Element ? 'iron-input' : 'input[is=iron-input]');
};
