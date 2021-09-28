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

import {encodeURL, getBaseUrl} from './url-util';

export interface ListViewParams {
  filter?: string | null;
  offset?: number | string;
}

export function computeLoadingClass(loading: boolean): string {
  return loading ? 'loading' : '';
}

export function computeShownItems<T>(items: T[]): T[] {
  return items.slice(0, 25);
}

export function getUrl(path: string, item: string) {
  return getBaseUrl() + path + encodeURL(item, true);
}

export function getFilterValue(params?: ListViewParams): string {
  return params?.filter ?? '';
}

export function getOffsetValue(params?: ListViewParams): number {
  if (params?.offset !== undefined) {
    return Number(params.offset);
  }
  return 0;
}
