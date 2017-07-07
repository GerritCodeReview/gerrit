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

  const util = window.util || {};

  util.parseDate = dateStr => {
    // Timestamps are given in UTC and have the format
    // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
    // nanoseconds.
    // Munge the date into an ISO 8061 format and parse that.
    return new Date(dateStr.replace(' ', 'T') + 'Z');
  };

  util.getCookie = name => {
    const key = name + '=';
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
      let c = cookies[i];
      while (c.charAt(0) === ' ') {
        c = c.substring(1);
      }
      if (c.startsWith(key)) {
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
  util.truncatePath = path => {
    const pathPieces = path.split('/');

    if (pathPieces.length < 2) {
      return path;
    }
    // Character is an ellipsis.
    return '\u2026/' + pathPieces[pathPieces.length - 1];
  };

  /**
   * If a node's parent is shadow root, it does not have a parent element.
   * Instead, traverse up to get its parent.
   * @return {Node} Returns the parent node.
   */
  util.getParentNode = function(node) {
    return node.parentElement || Polymer.dom(node).parentNode.host;
  };

  /**
   * TODO(beckysiegel) after Polymer2 upgrade can just use
   * node.getRootNode().getSelection()
   *
   * If in shadow dom, get selection based on the node.
   * @return {Object} Returns the selection object.
   */
  util.getSelection = node => {
    if (!Polymer.Settings.useShadow) {
      return window.getSelection();
    }
    let e = node;
    while (e.nodeType != 11) { // 11 = DOCUMENT_FRAGMENT_NODE
      e = e.parentNode;
    }
    return e.getSelection();
  };

  window.util = util;
})(window);
