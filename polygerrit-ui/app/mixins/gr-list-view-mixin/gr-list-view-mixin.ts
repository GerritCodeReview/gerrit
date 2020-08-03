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

import {encodeURL, getBaseUrl} from '../../utils/url-util.js';
import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin.js';

/**
 * @polymer
 * @mixinFunction
 */
export const ListViewMixin = dedupingMixin(superClass => {
  /**
   * @polymer
   * @mixinClass
   */
  class Mixin extends superClass {
    computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    }

    computeShownItems(items) {
      return items.slice(0, 25);
    }

    getUrl(path, item) {
      return getBaseUrl() + path + encodeURL(item, true);
    }

    /**
     * @param {Object} params
     * @return {string}
     */
    getFilterValue(params) {
      if (!params) { return ''; }
      return params.filter || '';
    }

    /**
     * @param {Object} params
     * @return {number}
     */
    getOffsetValue(params) {
      if (params && params.offset) {
        return params.offset;
      }
      return 0;
    }
  }

  return Mixin;
});
