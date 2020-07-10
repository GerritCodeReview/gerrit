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

import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin.js';
import {computeDisplayPath, computeTruncatedPath} from '../../utils/path-list-util.js';
/**
 * @polymer
 * @mixinFunction
 */
export const PathListMixin = dedupingMixin(superClass => {
  /**
   * @polymer
   * @mixinClass
   */
  class Mixin extends superClass {
    // Mixin wraps some path-list-util functions, so they can be used
    // in templates and computed properties

    computeDisplayPath(path) {
      return computeDisplayPath(path);
    }

    computeTruncatedPath(path) {
      return computeTruncatedPath(path);
    }
  }

  return Mixin;
});
