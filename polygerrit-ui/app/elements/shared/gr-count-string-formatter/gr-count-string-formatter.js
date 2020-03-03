/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
(function(window) {
  'use strict';
  const GrCountStringFormatter = window.GrCountStringFormatter || {};

  /**
   * Returns a count plus string that is pluralized when necessary.
   *
   * @param {number} count
   * @param {string} noun
   * @return {string}
   */
  GrCountStringFormatter.computePluralString = function(count, noun) {
    return this.computeString(count, noun) + (count > 1 ? 's' : '');
  };

  /**
   * Returns a count plus string that is not pluralized.
   *
   * @param {number} count
   * @param {string} noun
   * @return {string}
   */
  GrCountStringFormatter.computeString = function(count, noun) {
    if (count === 0) { return ''; }
    return count + ' ' + noun;
  };

  /**
   * Returns a count plus arbitrary text.
   *
   * @param {number} count
   * @param {string} text
   * @return {string}
   */
  GrCountStringFormatter.computeShortString = function(count, text) {
    if (count === 0) { return ''; }
    return count + text;
  };
  window.GrCountStringFormatter = GrCountStringFormatter;
})(window);
