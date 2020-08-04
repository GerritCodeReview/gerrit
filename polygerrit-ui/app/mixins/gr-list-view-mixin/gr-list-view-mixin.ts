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

import {encodeURL, getBaseUrl} from '../../utils/url-util';
import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin';
import {PolymerElement} from '@polymer/polymer';
import {Constructor} from '../../utils/common-util';

/**
 * @polymer
 * @mixinFunction
 */
export const ListViewMixin = dedupingMixin(
  <T extends Constructor<PolymerElement>>(
    superClass: T
  ): T & Constructor<ListViewMixinInterface> => {
    /**
     * @polymer
     * @mixinClass
     */
    class Mixin extends superClass {
      computeLoadingClass(loading: boolean): string {
        return loading ? 'loading' : '';
      }

      computeShownItems<T>(items: T[]): T[] {
        return items.slice(0, 25);
      }

      getUrl(path: string, item: string) {
        return getBaseUrl() + path + encodeURL(item, true);
      }

      getFilterValue(params: ListViewParams): string {
        if (!params) {
          return '';
        }
        return params.filter || '';
      }

      getOffsetValue(params: ListViewParams): number {
        if (params && params.offset) {
          return params.offset;
        }
        return 0;
      }
    }

    return Mixin;
  }
);

export interface ListViewMixinInterface {
  computeLoadingClass(loading: boolean): string;
  computeShownItems<T>(items: T[]): T[];
  getUrl(path: string, item: string): string;
  getFilterValue(params: ListViewParams): string;
  getOffsetValue(params: ListViewParams): number;
}

export interface ListViewParams {
  filter?: string;
  offset?: number;
}
