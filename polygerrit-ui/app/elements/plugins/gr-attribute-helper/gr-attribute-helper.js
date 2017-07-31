// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window) {
  'use strict';

  function GrAttributeHelper(element) {
    this.element = element;
    this._promises = {};
  }

  GrAttributeHelper.prototype._getChangedEventName = function(name) {
    return name.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase() + '-changed';
  };

  GrAttributeHelper.prototype.bind = function(name, callback) {
    const attributeChangedEventName = this._getChangedEventName(name);
    const changedHandler = e => {
      try {
        callback(e.detail.value);
      } catch (e) {
        console.info(e);
      }
    };
    const unbind = () => {
      this.element.removeEventListener(
          attributeChangedEventName, changedHandler);
    };
    this.element.addEventListener(
        attributeChangedEventName, changedHandler);
    if (this.element[name] !== undefined) {
      try {
        callback(this.element[name]);
      } catch (e) {
        console.info(e);
      }
    }
    return unbind;
  };

  GrAttributeHelper.prototype.get = function(name) {
    if (this.element[name] !== undefined) {
      return Promise.resolve(this.element[name]);
    }
    if (!this._promises[name]) {
      let resolve;
      const promise = new Promise(r => {
        resolve = r;
      });
      const unbind = this.bind(name, value => {
        resolve(value);
        unbind();
      });
      this._promises[name] = promise;
    }
    return this._promises[name];
  };

  window.GrAttributeHelper = GrAttributeHelper;
})(window);
