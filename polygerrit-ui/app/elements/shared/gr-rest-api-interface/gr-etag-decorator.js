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

  // Prevent redefinition.
  if (window.GrEtagDecorator) { return; }

  // Limit cache size because /change/detail responses may be large.
  const MAX_CACHE_SIZE = 30;

  function GrEtagDecorator() {
    this._etags = new Map();
    this._payloadCache = new Map();
  }

  GrEtagDecorator.prototype.getOptions = function(url, opt_options) {
    const etag = this._etags.get(url);
    if (!etag) {
      return opt_options;
    }
    const options = Object.assign({}, opt_options);
    options.headers = options.headers || new Headers();
    options.headers.set('If-None-Match', this._etags.get(url));
    return options;
  };

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

  GrEtagDecorator.prototype.getCachedPayload = function(url) {
    let payload = this._payloadCache.get(url);

    if (typeof payload === 'object') {
      // Note: For the sake of cache transparency, shallow clone the respomse
      // object so that cache hits are not equal object references. Some code
      // expects every network response to deserialize to a fresh object.
      payload = Object.assign({}, payload);
    }

    return payload;
  };

  GrEtagDecorator.prototype._truncateCache = function() {
    for (const url of this._etags.keys()) {
      if (this._etags.size <= MAX_CACHE_SIZE) {
        break;
      }
      this._etags.delete(url);
      this._payloadCache.delete(url);
    }
  };

  window.GrEtagDecorator = GrEtagDecorator;
})(window);
