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

import './common-test-setup.js';
import 'chai/chai.js';
import 'lodash/lodash.js';
self.assert = window.chai.assert;

/**
 * Triggers a flush of any pending events, observations, etc and calls you back
 * after they have been processed.
 *
 * @param {function()} callback
 */
function flush(callback) {
  // Ideally, this function would be a call to Polymer.dom.flush, but that
  // doesn't support a callback yet
  // (https://github.com/Polymer/polymer-dev/issues/851),
  // ...and there's cross-browser flakiness to deal with.
  // Make sure that we're invoking the callback with no arguments so that the
  // caller can pass Mocha callbacks, etc.
  const done = function done() {
    callback();
  };
  window.Polymer.dom.flush();
  window.setTimeout(done, 0);
}

self.flush = flush;

function stub(sandbox, name, keys) {
  // See web-component-tester/browser.js, extendInterfaces('stub', ...)
  // for details
  const proto = document.createElement(name).constructor.prototype;
  for (const k in keys) {
    if (keys.hasOwnProperty(k)) {
      sandbox.stub(proto, k, keys[k]);
    }
  }
}
self.stub = stub;
