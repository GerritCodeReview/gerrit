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
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import '../../../styles/shared-styles';
import '../../../styles/gr-form-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-menu-editor_html';
import {customElement, property} from '@polymer/decorators';
import {TopMenuItemInfo} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html} from 'lit';

@customElement('gr-menu-editor')
export class GrMenuEditor extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  menuItems!: TopMenuItemInfo[];

  @property({type: String})
  _newName?: string;

  @property({type: String})
  _newUrl?: string;

  styles = [
    sharedStyles,
    css`
      .buttonColumn {
        width: 2em;
      }
      .moveUpButton,
      .moveDownButton {
        width: 100%;
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
    `,
  ];

  render() {
    return html`
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
                  <gr-button
                    link=""
                    data-index$="[[index]]"
                    on-click="_handleMoveUpButton"
                    class="moveUpButton"
                    >↑</gr-button
                  >
                </td>
                <td class="buttonColumn">
                  <gr-button
                    link=""
                    data-index$="[[index]]"
                    on-click="_handleMoveDownButton"
                    class="moveDownButton"
                    >↓</gr-button
                  >
                </td>
                <td>
                  <gr-button
                    link=""
                    data-index$="[[index]]"
                    on-click="_handleDeleteButton"
                    class="remove-button"
                    >Delete</gr-button
                  >
                </td>
              </tr>
            </template>
          </tbody>
          <tfoot>
            <tr>
              <th>
                <iron-input
                  placeholder="New Title"
                  on-keydown="_handleInputKeydown"
                  bind-value="{{_newName}}"
                >
                  <input
                    is="iron-input"
                    placeholder="New Title"
                    on-keydown="_handleInputKeydown"
                    bind-value="{{_newName}}"
                  />
                </iron-input>
              </th>
              <th>
                <iron-input
                  class="newUrlInput"
                  placeholder="New URL"
                  on-keydown="_handleInputKeydown"
                  bind-value="{{_newUrl}}"
                >
                  <input
                    class="newUrlInput"
                    is="iron-input"
                    placeholder="New URL"
                    on-keydown="_handleInputKeydown"
                    bind-value="{{_newUrl}}"
                  />
                </iron-input>
              </th>
              <th></th>
              <th></th>
              <th>
                <gr-button
                  link=""
                  disabled$="[[_computeAddDisabled(_newName, _newUrl)]]"
                  on-click="_handleAddButton"
                  >Add</gr-button
                >
              </th>
            </tr>
          </tfoot>
        </table>
      </div>
    `;
  }

  _handleMoveUpButton(e: Event) {
    const target = (dom(e) as EventApi).localTarget;
    if (!(target instanceof HTMLElement)) return;
    const index = Number(target.dataset['index']);
    if (index === 0) {
      return;
    }
    const row = this.menuItems[index];
    const prev = this.menuItems[index - 1];
    this.splice('menuItems', index - 1, 2, row, prev);
  }

  _handleMoveDownButton(e: Event) {
    const target = (dom(e) as EventApi).localTarget;
    if (!(target instanceof HTMLElement)) return;
    const index = Number(target.dataset['index']);
    if (index === this.menuItems.length - 1) {
      return;
    }
    const row = this.menuItems[index];
    const next = this.menuItems[index + 1];
    this.splice('menuItems', index, 2, next, row);
  }

  _handleDeleteButton(e: Event) {
    const target = (dom(e) as EventApi).localTarget;
    if (!(target instanceof HTMLElement)) return;
    const index = Number(target.dataset['index']);
    this.splice('menuItems', index, 1);
  }

  _handleAddButton() {
    if (this._computeAddDisabled(this._newName, this._newUrl)) {
      return;
    }

    this.splice('menuItems', this.menuItems.length, 0, {
      name: this._newName,
      url: this._newUrl,
      target: '_blank',
    });

    this._newName = '';
    this._newUrl = '';
  }

  _computeAddDisabled(newName?: string, newUrl?: string) {
    return !newName?.length || !newUrl?.length;
  }

  _handleInputKeydown(e: KeyboardEvent) {
    if (e.keyCode === 13) {
      e.stopPropagation();
      this._handleAddButton();
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-menu-editor': GrMenuEditor;
  }
}
