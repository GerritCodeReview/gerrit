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

  const MAX_ETAGS_COUNT = 100;

  function GrEtagDecorator() {
    this._etags = new Map();
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

  GrEtagDecorator.prototype.collect = function(url, response) {
    if (!response.ok || (response.status !== 200 && response.status !== 304)) {
      return;
    }
    const etag = response.headers && response.headers.get('etag');
    if (!etag) {
      this._etags.delete(url);
    } else {
      this._etags.set(url, etag);
      this._trunkateEtags();
    }
  };

  GrEtagDecorator.prototype._trunkateEtags = function() {
    for (const url of this._etags.keys()) {
      if (this._etags.size <= MAX_ETAGS_COUNT) {
        break;
      }
      this._etags.delete(url);
    }
  };

  window.GrEtagDecorator = GrEtagDecorator;
})(window);
