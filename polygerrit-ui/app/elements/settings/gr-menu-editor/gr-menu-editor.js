/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../../@polymer/iron-input/iron-input.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import '../../../styles/gr-form-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      .buttonColumn {
        width: 2em;
      }
      .moveUpButton,
      .moveDownButton {
        width: 100%
      }
      tbody tr:first-of-type td .moveUpButton,
      tbody tr:last-of-type td .moveDownButton {
        display: none;
      }
      td.urlCell {
        word-break: break-word;
      }
      .newUrlInput {
        min-width: 23em;
      }
    </style>
    <style include="gr-form-styles"></style>
    <div class="gr-form-styles">
      <table>
        <thead>
          <tr>
            <th class="nameHeader">Name</th>
            <th class="url-header">URL</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[menuItems]]">
            <tr>
              <td>[[item.name]]</td>
              <td class="urlCell">[[item.url]]</td>
              <td class="buttonColumn">
                <gr-button link="" data-index="[[index]]" on-tap="_handleMoveUpButton" class="moveUpButton">↑</gr-button>
              </td>
              <td class="buttonColumn">
                <gr-button link="" data-index="[[index]]" on-tap="_handleMoveDownButton" class="moveDownButton">↓</gr-button>
              </td>
              <td>
                <gr-button link="" data-index="[[index]]" on-tap="_handleDeleteButton" class="remove-button">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
        <tfoot>
          <tr>
            <th>
              <input is="iron-input" placeholder="New Title" on-keydown="_handleInputKeydown" bind-value="{{_newName}}">
            </th>
            <th>
              <input class="newUrlInput" is="iron-input" placeholder="New URL" on-keydown="_handleInputKeydown" bind-value="{{_newUrl}}">
            </th>
            <th></th>
            <th></th>
            <th>
              <gr-button link="" disabled\$="[[_computeAddDisabled(_newName, _newUrl)]]" on-tap="_handleAddButton">Add</gr-button>
            </th>
          </tr>
        </tfoot>
      </table>
    </div>
`,

  is: 'gr-menu-editor',

  properties: {
    menuItems: Array,
    _newName: String,
    _newUrl: String,
  },

  _handleMoveUpButton(e) {
    const index = Polymer.dom(e).localTarget.dataIndex;
    if (index === 0) { return; }
    const row = this.menuItems[index];
    const prev = this.menuItems[index - 1];
    this.splice('menuItems', index - 1, 2, row, prev);
  },

  _handleMoveDownButton(e) {
    const index = Polymer.dom(e).localTarget.dataIndex;
    if (index === this.menuItems.length - 1) { return; }
    const row = this.menuItems[index];
    const next = this.menuItems[index + 1];
    this.splice('menuItems', index, 2, next, row);
  },

  _handleDeleteButton(e) {
    const index = Polymer.dom(e).localTarget.dataIndex;
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
  }
});
