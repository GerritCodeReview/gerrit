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
  if (window.GrGerritAuth) { return; }

  function GrGerritAuth() {}

  GrGerritAuth.prototype._getCookie = function(name) {
    const key = name + '=';
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
      let c = cookies[i];
      while (c.charAt(0) == ' ') {
        c = c.substring(1);
      }
      if (c.startsWith(key)) {
        return c.substring(key.length, c.length);
      }
    }
    return '';
  };

  GrGerritAuth.prototype.fetch = function(url, options) {
    options = Object.assign({}, options);
    if (options.method && options.method !== 'GET') {
      const token = this._getCookie('XSRF_TOKEN');
      if (token) {
        options.headers = options.headers || new Headers();
        options.headers.append('X-Gerrit-Auth', token);
      }
    }
    options.credentials = options.credentials || 'same-origin';
    return fetch(url, options);
  };

  window.GrGerritAuth = GrGerritAuth;
})(window);
