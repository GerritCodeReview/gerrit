/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

  /**
    * @appliesMixin Gerrit.ChangeTableMixin
    */
  class GrChangeTableEditor extends Polymer.mixinBehaviors( [
    Gerrit.ChangeTableBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-change-table-editor'; }

    static get properties() {
      return {
        displayedColumns: {
          type: Array,
          notify: true,
        },
        showNumber: {
          type: Boolean,
          notify: true,
        },
      };
    }

    /**
     * Get the list of enabled column names from whichever checkboxes are
     * checked (excluding the number checkbox).
     * @return {!Array<string>}
     */
    _getDisplayedColumns() {
      return Array.from(Polymer.dom(this.root)
          .querySelectorAll('.checkboxContainer input:not([name=number])'))
          .filter(checkbox => checkbox.checked)
          .map(checkbox => checkbox.name);
    }

    /**
     * Handle a click on a checkbox container and relay the click to the checkbox it
     * contains.
     */
    _handleCheckboxContainerClick(e) {
      const checkbox = Polymer.dom(e.target).querySelector('input');
      if (!checkbox) { return; }
      checkbox.click();
    }

    /**
     * Handle a click on the number checkbox and update the showNumber property
     * accordingly.
     */
    _handleNumberCheckboxClick(e) {
      this.showNumber = Polymer.dom(e).rootTarget.checked;
    }

    /**
     * Handle a click on a displayed column checkboxes (excluding number) and
     * update the displayedColumns property accordingly.
     */
    _handleTargetClick(e) {
      this.set('displayedColumns', this._getDisplayedColumns());
    }
  }

  customElements.define(GrChangeTableEditor.is, GrChangeTableEditor);
})();
