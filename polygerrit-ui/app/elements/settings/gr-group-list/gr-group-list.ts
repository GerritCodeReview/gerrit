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
import '../../../styles/shared-styles';
import '../../../styles/gr-form-styles';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-group-list_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {GroupInfo, GroupId} from '../../../types/common';
import {appContext} from '../../../services/app-context';

declare global {
  interface HTMLElementTagNameMap {
    'gr-group-list': GrGroupList;
  }
}
@customElement('gr-group-list')
export class GrGroupList extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  _groups: GroupInfo[] = [];

  private readonly restApiService = appContext.restApiService;

  loadData() {
    return this.restApiService.getAccountGroups().then(groups => {
      if (!groups) return;
      this._groups = groups.sort((a, b) =>
        (a.name || '').localeCompare(b.name || '')
      );
    });
  }

  _computeVisibleToAll(group: GroupInfo) {
    return group.options && group.options.visible_to_all ? 'Yes' : 'No';
  }

  _computeGroupPath(group: GroupInfo) {
    if (!group || !group.id) {
      return;
    }

    // Group ID is already encoded from the API
    // Decode it here to match with our router encoding behavior
    return GerritNav.getUrlForGroup(decodeURIComponent(group.id) as GroupId);
  }
}
