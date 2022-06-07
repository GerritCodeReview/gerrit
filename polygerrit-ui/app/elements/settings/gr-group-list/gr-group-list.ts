/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {GroupInfo, GroupId} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, state} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-group-list': GrGroupList;
  }
}
@customElement('gr-group-list')
export class GrGroupList extends LitElement {
  @state()
  protected _groups: GroupInfo[] = [];

  private readonly restApiService = getAppContext().restApiService;

  loadData() {
    return this.restApiService.getAccountGroups().then(groups => {
      if (!groups) return;
      this._groups = groups.sort((a, b) =>
        (a.name || '').localeCompare(b.name || '')
      );
    });
  }

  static override get styles() {
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

  override render() {
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
          ${(this._groups ?? []).map(group => {
            const href = this._computeGroupPath(group);
            return html`
              <tr>
                <td class="nameColumn">
                  <a href=${href}> ${group.name} </a>
                </td>
                <td>${group.description}</td>
                <td class="visibleCell">
                  ${group?.options?.visible_to_all ? 'Yes' : 'No'}
                </td>
              </tr>
            `;
          })}
        </tbody>
      </table>
    </div>`;
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
