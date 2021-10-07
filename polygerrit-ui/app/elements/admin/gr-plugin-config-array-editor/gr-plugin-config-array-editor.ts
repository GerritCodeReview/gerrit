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
import '@polymer/iron-input/iron-input';
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-plugin-config-array-editor_html';
import {property, customElement} from '@polymer/decorators';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {
  PluginConfigOptionsChangedEventDetail,
  ArrayPluginOption,
} from '../gr-repo-plugin-config/gr-repo-plugin-config-types';

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-config-array-editor': GrPluginConfigArrayEditor;
  }
}

@customElement('gr-plugin-config-array-editor')
export class GrPluginConfigArrayEditor extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the plugin config option changes.
   *
   * @event plugin-config-option-changed
   */

  @property({type: String})
  _newValue = '';

  // This property is never null, since this component in only about operations
  // on pluginOption.
  @property({type: Object})
  pluginOption!: ArrayPluginOption;

  @property({type: Boolean, computed: '_computeDisabled(pluginOption.*)'})
  disabled!: boolean; // _computeDisabled never returns null

  _computeDisabled(
    record: PolymerDeepPropertyChange<ArrayPluginOption, ArrayPluginOption>
  ) {
    return !(
      record &&
      record.base &&
      record.base.info &&
      record.base.info.editable
    );
  }

  _handleAddTap(e: MouseEvent) {
    e.preventDefault();
    this._handleAdd();
  }

  _handleInputKeydown(e: KeyboardEvent) {
    // Enter.
    if (e.keyCode === 13) {
      e.preventDefault();
      this._handleAdd();
    }
  }

  _handleAdd() {
    if (!this._newValue.length) {
      return;
    }
    this._dispatchChanged(
      this.pluginOption.info.values.concat([this._newValue])
    );
    this._newValue = '';
  }

  _handleDelete(e: MouseEvent) {
    const value = ((dom(e) as EventApi).localTarget as HTMLElement).dataset[
      'item'
    ];
    this._dispatchChanged(
      this.pluginOption.info.values.filter(str => str !== value)
    );
  }

  _dispatchChanged(values: string[]) {
    const {_key, info} = this.pluginOption;
    const detail: PluginConfigOptionsChangedEventDetail = {
      _key,
      info: {...info, values},
      notifyPath: `${_key}.values`,
    };
    this.dispatchEvent(
      new CustomEvent('plugin-config-option-changed', {detail})
    );
  }

  _computeShowInputRow(disabled: boolean) {
    return disabled ? 'hide' : '';
  }
}
