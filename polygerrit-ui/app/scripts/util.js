// Copyright (C) 2015 The Android Open Source Project
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

  var util = window.util || {};

  util.parseDate = function(dateStr) {
    // Timestamps are given in UTC and have the format
    // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
    // nanoseconds.
    // Munge the date into an ISO 8061 format and parse that.
    return new Date(dateStr.replace(' ', 'T') + 'Z');
  };

  util.getCookie = function(name) {
    var key = name + '=';
    var cookies = document.cookie.split(';');
    for (var i = 0; i < cookies.length; i++) {
      var c = cookies[i];
      while (c.charAt(0) == ' ') {
        c = c.substring(1);
      }
      if (c.indexOf(key) == 0) {
        return c.substring(key.length, c.length);
      }
    }
    return '';
  };

  /**
   * Truncates URLs to display filename only
   * Example
   * // returns '.../text.html'
   * util.truncatePath.('dir/text.html');
   * Example
   * // returns 'text.html'
   * util.truncatePath.('text.html');
   * @return {String} Returns the truncated value of a URL.
   */
  util.truncatePath = function(path) {
    var pathPieces = path.split('/');

    if (pathPieces.length < 2) {
      return path;
    }
    // Character is an ellipsis.
    return '\u2026/' + pathPieces[pathPieces.length - 1];
  };

  window.util = util;
})(window);
