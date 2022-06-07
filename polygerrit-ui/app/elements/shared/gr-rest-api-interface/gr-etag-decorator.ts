/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Limit cache size because /change/detail responses may be large.
const MAX_CACHE_SIZE = 30;

/**
 * Option to send with etag requests.
 */
export interface ETagOption {
  headers?: Headers;
}

/**
 * GrTagDecorator class.
 *
 * Defines common methods to help cache and build ETag into a request header.
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
    const optionsCopy: ETagOption = {...options};
    optionsCopy.headers = optionsCopy.headers || new Headers();
    optionsCopy.headers.set('If-None-Match', etag);
    return optionsCopy;
  }

  /**
   * Handle a response to a request with ETag headers, potentially incorporating
   * its result in the payload cache.
   *
   *
   * @param url The URL of the request.
   * @param response The response object.
   * @param payload The raw, unparsed JSON contained in the response
   *     body. Note: because response.text() cannot be read twice, this must be
   *     provided separately.
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
