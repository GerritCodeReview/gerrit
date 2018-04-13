/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
    is: 'gr-confirm-submit-dialog',

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
      change: Object,
      action: Object,
      _submittedTogether: Array,
      _loading: Boolean,
    },

    load() {
      this._loading = true;
      return this.$.restAPI.getChangesSubmittedTogether(this.change._number)
          .then(submittedTogether => {
            this._loading = false;
            this._submittedTogether = submittedTogether;
          });
    },

    _handleEnterKey(e) {
      this._confirm();
    },

    _handleConfirmTap(e) {
      e.preventDefault();
      this._confirm();
    },

    _confirm() {
      this.fire('confirm', null, {bubbles: false});
    },

    _handleCancelTap(e) {
      e.preventDefault();
      this.fire('cancel', null, {bubbles: false});
    },

    _computeShowSubmittedTogether(submittedTogether, loading) {
      return !loading && !!submittedTogether.length;
    },

    _isThisChnage(thisChange, otherChange) {
      return thisChange._number === otherChange._number;
    },

    _getChangeUrl(change) {
      return Gerrit.Nav.getUrlForChange(change);
    },
  });
})();
