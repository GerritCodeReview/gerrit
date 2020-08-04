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

/** @constructor */
export function GrEtagDecorator() {
  this._etags = new Map();
  this._payloadCache = new Map();
}

/**
 * Get or upgrade fetch options to include an ETag in a request.
 *
 * @param {string} url The URL being fetched.
 * @param {!Object=} opt_options Optional options object in which to include
 *     the ETag request header. If omitted, the result will be a fresh option
 *     set.
 * @return {!Object}
 */
GrEtagDecorator.prototype.getOptions = function(url, opt_options) {
  const etag = this._etags.get(url);
  if (!etag) {
    return opt_options;
  }
  const options = {...opt_options};
  options.headers = options.headers || new Headers();
  options.headers.set('If-None-Match', this._etags.get(url));
  return options;
};

/**
 * Handle a response to a request with ETag headers, potentially incorporating
 * its result in the payload cache.
 *
 * @param {string} url The URL of the request.
 * @param {!Response} response The response object.
 * @param {string} payload The raw, unparsed JSON contained in the response
 *     body. Note: because response.text() cannot be read twice, this must be
 *     provided separately.
 */
GrEtagDecorator.prototype.collect = function(url, response, payload) {
  if (!response ||
      !response.ok ||
      response.status !== 200 ||
      response.status === 304) {
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
};

/**
 * Get the cached payload for a given URL.
 *
 * @param {string} url
 * @return {string|undefined} Returns the unparsed JSON payload from the
 *     cache.
 */
GrEtagDecorator.prototype.getCachedPayload = function(url) {
  return this._payloadCache.get(url);
};

/**
 * Limit the cache size to MAX_CACHE_SIZE.
 */
GrEtagDecorator.prototype._truncateCache = function() {
  for (const url of this._etags.keys()) {
    if (this._etags.size <= MAX_CACHE_SIZE) {
      break;
    }
    this._etags.delete(url);
    this._payloadCache.delete(url);
  }
};
