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
    is: 'gr-confirm-cherrypick-dialog',

    /**
     * Fired when the confirm button is pressed.
     *
     * @event confirm
     */

    /**
     * Fired when the cancel button is pressed.
     *
     * @event cancel
     */

    properties: {
      branch: String,
      message: String,
      commitInfo: {
        type: Object,
        readOnly: true,
        observer: '_commitInfoChanged',
      },
    },

    _commitInfoChanged: function(commitInfo) {
      // Pre-populate cherry-pick message for editing from commit info.
      this.message = commitInfo.message;
    },

    _handleConfirmTap: function(e) {
      e.preventDefault();
      this.fire('confirm', null, {bubbles: false});
    },

    _handleCancelTap: function(e) {
      e.preventDefault();
      this.fire('cancel', null, {bubbles: false});
    },
  });
})();
