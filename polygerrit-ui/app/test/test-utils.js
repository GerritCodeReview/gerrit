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

import {_testOnly_resetPluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader.js';
import {testOnly_resetInternalState} from '../elements/shared/gr-js-api-interface/gr-api-utils.js';
import {_testOnly_resetEndpoints} from '../elements/shared/gr-js-api-interface/gr-plugin-endpoints.js';

export const mockPromise = () => {
  let res;
  const promise = new Promise(resolve => {
    res = resolve;
  });
  promise.resolve = res;
  return promise;
};
export const isHidden = el => getComputedStyle(el).display === 'none';

// Provide reset plugins function to clear installed plugins between tests.
// No gr-app found (running tests)
export const resetPlugins = () => {
  testOnly_resetInternalState();
  _testOnly_resetEndpoints();
  _testOnly_resetPluginLoader();
};
