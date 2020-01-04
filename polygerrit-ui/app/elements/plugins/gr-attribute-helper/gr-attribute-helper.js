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

  function GrAttributeHelper(element) {
    this.element = element;
    this._promises = {};
  }

  GrAttributeHelper.prototype._getChangedEventName = function(name) {
    return name.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase() + '-changed';
  };

  /**
   * Returns true if the property is defined on wrapped element.
   *
   * @param {string} name
   * @return {boolean}
   */
  GrAttributeHelper.prototype._elementHasProperty = function(name) {
    return this.element[name] !== undefined;
  };

  GrAttributeHelper.prototype._reportValue = function(callback, value) {
    try {
      callback(value);
    } catch (e) {
      console.info(e);
    }
  };

  /**
   * Binds callback to property updates.
   *
   * @param {string} name Property name.
   * @param {function(?)} callback
   * @return {function()} Unbind function.
   */
  GrAttributeHelper.prototype.bind = function(name, callback) {
    const attributeChangedEventName = this._getChangedEventName(name);
    const changedHandler = e => this._reportValue(callback, e.detail.value);
    const unbind = () => this.element.removeEventListener(
        attributeChangedEventName, changedHandler);
    this.element.addEventListener(
        attributeChangedEventName, changedHandler);
    if (this._elementHasProperty(name)) {
      this._reportValue(callback, this.element[name]);
    }
    return unbind;
  };

  /**
   * Get value of the property from wrapped object. Waits for the property
   * to be initialized if it isn't defined.
   *
   * @param {string} name Property name.
   * @return {!Promise<?>}
   */
  GrAttributeHelper.prototype.get = function(name) {
    if (this._elementHasProperty(name)) {
      return Promise.resolve(this.element[name]);
    }
    if (!this._promises[name]) {
      let resolve;
      const promise = new Promise(r => resolve = r);
      const unbind = this.bind(name, value => {
        resolve(value);
        unbind();
      });
      this._promises[name] = promise;
    }
    return this._promises[name];
  };

  /**
   * Sets value and dispatches event to force notify.
   *
   * @param {string} name Property name.
   * @param {?} value
   */
  GrAttributeHelper.prototype.set = function(name, value) {
    this.element[name] = value;
    this.element.dispatchEvent(
        new CustomEvent(this._getChangedEventName(name), {detail: {value}}));
  };

  window.GrAttributeHelper = GrAttributeHelper;
})(window);
