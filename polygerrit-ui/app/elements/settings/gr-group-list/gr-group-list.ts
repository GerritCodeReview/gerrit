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

import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {GroupInfo, GroupId} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';

declare global {
  interface HTMLElementTagNameMap {
    'gr-group-list': GrGroupList;
  }
}
@customElement('gr-group-list')
export class GrGroupList extends GrLitElement {
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

  static get styles() {
    return [
      sharedStyles,
      formStyles,
      css`
        #groups .nameColumn {
          min-width: 11em;
          width: auto;
        }
        .descriptionHeader {
          min-width: 21.5em;
        }
        .visibleCell {
          text-align: center;
          width: 6em;
        }
      `,
    ];
  }

  _renderGroups(groups: GroupInfo[]) {
    if (!groups) return;
    return html`${groups.map(
      group => html`<tr>
          <td class="nameColumn">
          <a href$="${this._computeGroupPath(group)}> ${group.name} </a>
          </td>
          <td>${group.description}</td>
          <td class="visibleCell">${this._computeVisibleToAll(group)}</td>
          </tr>
          `
    )}`;
  }

  render() {
    return html` <div class="gr-form-styles">
      <table id="groups">
        <thead>
          <tr>
            <th class="nameHeader">Name</th>
            <th class="descriptionHeader">Description</th>
            <th class="visibleCell">Visible to all</th>
          </tr>
        </thead>
        <tbody>
          ${this._renderGroups(this._groups)}
        </tbody>
      </table>
    </div>`;
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
