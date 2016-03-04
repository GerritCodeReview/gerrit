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
    is: 'gr-keyboard-shortcuts-dialog',

    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    properties: {
      view: String,
    },

    hostAttributes: {
      role: 'dialog',
    },

    _computeInView: function(currentView, view) {
      return view == currentView;
    },

    _computeInChangeListView: function(currentView) {
      return currentView == 'gr-change-list-view' ||
          currentView == 'gr-dashboard-view';
    },

    _handleCloseTap: function(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },
  });
})();
