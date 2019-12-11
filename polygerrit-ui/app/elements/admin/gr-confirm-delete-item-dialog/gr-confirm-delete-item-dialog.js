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

  const DETAIL_TYPES = {
    BRANCHES: 'branches',
    ID: 'id',
    TAGS: 'tags',
  };

  /**
   * @appliesMixin Gerrit.FireMixin
   */
  class GrConfirmDeleteItemDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-confirm-delete-item-dialog'; }
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

    static get properties() {
      return {
        item: String,
        itemType: String,
      };
    }

    _handleConfirmTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('confirm', null, {bubbles: false});
    }

    _handleCancelTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('cancel', null, {bubbles: false});
    }

    _computeItemName(detailType) {
      if (detailType === DETAIL_TYPES.BRANCHES) {
        return 'Branch';
      } else if (detailType === DETAIL_TYPES.TAGS) {
        return 'Tag';
      } else if (detailType === DETAIL_TYPES.ID) {
        return 'ID';
      }
    }
  }

  customElements.define(GrConfirmDeleteItemDialog.is,
      GrConfirmDeleteItemDialog);
})();
