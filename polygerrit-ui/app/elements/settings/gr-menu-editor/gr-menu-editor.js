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

  Polymer({
    is: 'gr-menu-editor',

    properties: {
      menuItems: Array,
      _newName: String,
      _newUrl: String,
    },

    _handleMoveUpButton(e) {
      const index = Number(Polymer.dom(e).localTarget.dataset.index);
      if (index === 0) { return; }
      const row = this.menuItems[index];
      const prev = this.menuItems[index - 1];
      this.splice('menuItems', index - 1, 2, row, prev);
    },

    _handleMoveDownButton(e) {
      const index = Number(Polymer.dom(e).localTarget.dataset.index);
      if (index === this.menuItems.length - 1) { return; }
      const row = this.menuItems[index];
      const next = this.menuItems[index + 1];
      this.splice('menuItems', index, 2, next, row);
    },

    _handleDeleteButton(e) {
      const index = Number(Polymer.dom(e).localTarget.dataset.index);
      this.splice('menuItems', index, 1);
    },

    _handleAddButton() {
      if (this._computeAddDisabled(this._newName, this._newUrl)) { return; }

      this.splice('menuItems', this.menuItems.length, 0, {
        name: this._newName,
        url: this._newUrl,
        target: '_blank',
      });

      this._newName = '';
      this._newUrl = '';
    },

    _computeAddDisabled(newName, newUrl) {
      return !newName.length || !newUrl.length;
    },

    _handleInputKeydown(e) {
      if (e.keyCode === 13) {
        e.stopPropagation();
        this._handleAddButton();
      }
    },
  });
})();
