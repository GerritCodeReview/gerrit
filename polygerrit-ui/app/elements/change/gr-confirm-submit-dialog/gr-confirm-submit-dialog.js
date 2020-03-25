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
    _legacyUndefinedCheck: true,

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
      /**
       * @type {{
       *    is_private: boolean,
       *    subject: string,
       *  }}
       */
      change: Object,

      /**
       * @type {{
       *    label: string,
       *  }}
       */
      action: Object,
    },

    resetFocus(e) {
      this.$.dialog.resetFocus();
    },

<<<<<<< HEAD   (d0d5fe Verify hostname when sending emails via SMTP server with SMT)
    _handleConfirmTap(e) {
      e.preventDefault();
      this.dispatchEvent(new CustomEvent('confirm', {bubbles: false}));
    },
=======
  _computeHasChangeEdit(change) {
    return !!change.revisions &&
        Object.values(change.revisions).some(rev => rev._number == 'edit');
  }

  _computeUnresolvedCommentsWarning(change) {
    const unresolvedCount = change.unresolved_comment_count;
    const plural = unresolvedCount > 1 ? 's' : '';
    return `Heads Up! ${unresolvedCount} unresolved comment${plural}.`;
  }
>>>>>>> CHANGE (a36f08 Add a warning if submitting a change with an open change edi)

    _handleCancelTap(e) {
      e.preventDefault();
      this.dispatchEvent(new CustomEvent('cancel', {bubbles: false}));
    },
  });
})();
