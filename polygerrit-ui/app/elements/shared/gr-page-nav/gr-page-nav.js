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
(function() {
  'use strict';

  Polymer({
    is: 'gr-page-nav',

    properties: {
      _headerHeight: Number,
    },

    attached() {
      this.listen(window, 'scroll', '_handleBodyScroll');
    },

    detached() {
      this.unlisten(window, 'scroll', '_handleBodyScroll');
    },

    _handleBodyScroll() {
      if (this._headerHeight === undefined) {
        let top = this._getOffsetTop(this);
        for (let offsetParent = this.offsetParent;
           offsetParent;
           offsetParent = this._getOffsetParent(offsetParent)) {
          top += this._getOffsetTop(offsetParent);
        }
        this._headerHeight = top;
      }

      this.$.nav.classList.toggle('pinned',
          this._getScrollY() >= this._headerHeight);
    },

    /* Functions used for test purposes */
    _getOffsetParent(element) {
      if (!element || !element.offsetParent) { return ''; }
      return element.offsetParent;
    },

    _getOffsetTop(element) {
      return element.offsetTop;
    },

    _getScrollY() {
      return window.scrollY;
    },
  });
})();
