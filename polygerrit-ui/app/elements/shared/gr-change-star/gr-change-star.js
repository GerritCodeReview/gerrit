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

    _handleStarTap: function() {
      var method = this.change.starred ? 'DELETE' : 'PUT';
      this.set('change.starred', !this.change.starred);
      this._send(method, this._restEndpoint()).catch(function(err) {
        this.set('change.starred', !this.change.starred);
        alert('Change couldn’t be starred. Check the console and contact ' +
            'the PolyGerrit team for assistance.');
        throw err;
      }.bind(this));
    },

    _send: function(method, url) {
      var xhr = document.createElement('gr-request');
      this._xhrPromise = xhr.send({
        method: method,
        url: url,
      });
      return this._xhrPromise;
    },

    _restEndpoint: function() {
      return '/accounts/self/starred.changes/' + this.change._number;
    },
  });
})();
