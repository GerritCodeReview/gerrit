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
import {KeydownEvent} from '../../../types/events';

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

  _handleInputKeydown(e: KeydownEvent) {
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
