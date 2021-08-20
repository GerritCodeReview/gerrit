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
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-create-group-dialog_html';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {customElement, property, observe} from '@polymer/decorators';
import {GroupName} from '../../../types/common';
import {appContext} from '../../../services/app-context';

@customElement('gr-create-group-dialog')
export class GrCreateGroupDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasNewGroupName = false;

  @property({type: String})
  _name: GroupName | '' = '';

  @property({type: Boolean})
  _groupCreated = false;

  private readonly restApiService = appContext.restApiService;

  _computeGroupUrl(groupId: string) {
    return getBaseUrl() + '/admin/groups/' + encodeURL(groupId, true);
  }

  @observe('_name')
  _updateGroupName(name: string) {
    this.hasNewGroupName = !!name;
  }

  override focus() {
    this.shadowRoot?.querySelector('input')?.focus();
  }

  handleCreateGroup() {
    const name = this._name as GroupName;
    return this.restApiService.createGroup({name}).then(groupRegistered => {
      if (groupRegistered.status !== 201) {
        return;
      }
      this._groupCreated = true;
      return this.restApiService.getGroupConfig(name).then(group => {
        // TODO(TS): should group always defined ?
        page.show(this._computeGroupUrl(String(group!.group_id!)));
      });
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-group-dialog': GrCreateGroupDialog;
  }
}
