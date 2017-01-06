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
    is: 'gr-change-star',

    properties: {
      change: {
        type: Object,
        notify: true,
      },

      _xhrPromise: Object,  // Used for testing.
    },

    _computeStarClass: function(starred) {
      var classes = ['starButton'];
      if (starred) {
        classes.push('starButton-active');
      }
      return classes.join(' ');
    },

    toggleStar: function() {
      var newVal = !this.change.starred;
      this.set('change.starred', newVal);
      this._xhrPromise = this.$.restAPI.saveChangeStarred(this.change._number,
          newVal);
    },
  });
})();
