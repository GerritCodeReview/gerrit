// Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  Polymer({
    is: 'gr-header',

    properties: {
      keepOnScroll: {
        type: Boolean,
      },
      _topInitial: Number,
      _topLast: Number,
      _headerHeight: Number,
    },

    attached: function() {
      var rect = this.$.header.getBoundingClientRect();
      this._headerTopInitial = rect.top;
      this._headerTopLast = rect.top;
      this._headerHeight = rect.height;
      this.customStyle['--header-height'] = rect.height + 'px';
      this.updateStyles();
      this.listen(window, 'scroll', '_handleScroll');
    },

    detached: function() {
      this.unlisten(window, 'scroll', '_handleScroll');
    },

    _getScrollY: function() {
      return window.scrollY;
    },

    _handleScroll: function() {
      this.debounce('scroll', function() {
        var header = this.$.header;
        var scrollY = this._headerTopInitial - this._getScrollY();
        if (this.keepOnScroll) {
          if (scrollY > 0) {
            // Reposition to imitate natural scrolling.
            header.style.top = scrollY + 'px';
          } else if (this._headerTopLast != 0) {
            header.style.top = '0px';
          }
        } else if (scrollY > -this._headerHeight ||
            this._headerTopLast < -this._headerHeight) {
          // Allow to scroll away, but ignore when far behind the edge.
          header.style.top = scrollY + 'px';
        }
        this._headerTopLast = scrollY;
      });
    },
  });
})();
