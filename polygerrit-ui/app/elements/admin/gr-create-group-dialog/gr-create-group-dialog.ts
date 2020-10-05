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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-create-group-dialog_html';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {customElement, property, observe} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {GroupName} from '../../../types/common';

export interface GrCreateGroupDialog {
  $: {
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-create-group-dialog')
export class GrCreateGroupDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasNewGroupName = false;

  @property({type: String})
  _name: GroupName | '' = '';

  @property({type: Boolean})
  _groupCreated = false;

  _computeGroupUrl(groupId: string) {
    return getBaseUrl() + '/admin/groups/' + encodeURL(groupId, true);
  }

  @observe('_name')
  _updateGroupName(name: string) {
    this.hasNewGroupName = !!name;
  }

  handleCreateGroup() {
    const name = this._name as GroupName;
    return this.$.restAPI.createGroup({name}).then(groupRegistered => {
      if (groupRegistered.status !== 201) {
        return;
      }
      this._groupCreated = true;
      return this.$.restAPI.getGroupConfig(name).then(group => {
        // TODO(TS): should group always defined ?
        page.show(this._computeGroupUrl(group!.group_id!));
      });
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-group-dialog': GrCreateGroupDialog;
  }
}
