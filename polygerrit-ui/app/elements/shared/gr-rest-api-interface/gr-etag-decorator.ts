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

// Limit cache size because /change/detail responses may be large.
const MAX_CACHE_SIZE = 30;

/**
 * Option to send with etag requests.
 */
export interface ETagOption {
  headers: Headers;
}

/**
 * GrTagDecorator class.
 */
export class GrEtagDecorator {
  _etags = new Map<string, string | null>();

  _payloadCache = new Map<string, string>();

  /**
   * Get or upgrade fetch options to include an ETag in a request.
   *
   */
  getOptions(url: string, options?: ETagOption) {
    const etag = this._etags.get(url);
    if (!etag) {
      return options;
    }
    const optionsCopy = {...options} as ETagOption;
    optionsCopy.headers = optionsCopy.headers || new Headers();
    optionsCopy.headers.set('If-None-Match', etag);
    return optionsCopy;
  }

  /**
   * Handle a response to a request with ETag headers, potentially incorporating
   * its result in the payload cache.
   *
   */
  collect(url: string, response: Response, payload: string) {
    if (!response || !response.ok || response.status !== 200) {
      // 304 Not Modified means etag is still valid.
      return;
    }
    this._payloadCache.set(url, payload);
    const etag = response.headers && response.headers.get('etag');
    if (!etag) {
      this._etags.delete(url);
    } else {
      this._etags.set(url, etag);
      this._truncateCache();
    }
  }

  /**
   * Get the cached payload for a given URL.
   */
  getCachedPayload(url: string) {
    return this._payloadCache.get(url);
  }

  /**
   * Limit the cache size to MAX_CACHE_SIZE.
   */
  _truncateCache() {
    for (const url of this._etags.keys()) {
      if (this._etags.size <= MAX_CACHE_SIZE) {
        break;
      }
      this._etags.delete(url);
      this._payloadCache.delete(url);
    }
  }
}
