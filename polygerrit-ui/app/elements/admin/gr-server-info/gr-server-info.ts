/**
 * @license
 * Copyright (C) 2024 The Android Open Source Project
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

import {MetadataInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {customElement, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {fireTitleChange} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {tableStyles} from '../../../styles/gr-table-styles';

@customElement('gr-server-info')
export class GrServerInfo extends LitElement {
  @state() metadataList = new Array<MetadataInfo>();

  @state() loading = true;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      tableStyles,
      sharedStyles,
      css`
        .metadataName,
        .metadataDescription,
        .metadataValue {
          white-space: nowrap;
        }
        .placeholder {
          color: var(--deemphasized-text-color);
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange('Server Info');
    this.loadServerInfo();
  }

  override render() {
    return html`
      <main class="gr-form-styles read-only">
        <table id="list" class="genericList">
          <tbody>
            <tr class="headerRow">
              <th class="metadataName topHeader">Name</th>
              <th class="metadataDescription topHeader">Description</th>
              <th class="metadataValue topHeader">Value</th>
            </tr>
            ${this.renderLoading()}
          </tbody>
          ${this.renderServerInfoTable()}
        </table>
      </main>
    `;
  }

  private renderLoading() {
    if (!this.loading) return;

    return html`
      <tr id="loading" class="loadingMsg loading">
        <td>Loading...</td>
      </tr>
    `;
  }

  private renderServerInfoTable() {
    if (this.loading) return;

    return html`
      <tbody>
        ${Array.from(this.metadataList, metadata =>
          this.renderServerInfo(metadata)
        )}
      </tbody>
    `;
  }

  private renderServerInfo(metadata: MetadataInfo) {
    return html`
      <tr class="table">
        <td class="metadataName">${metadata.name}</td>
        <td class="metadataDescription">
          ${metadata.description ? metadata.description : ''}
        </td>
        <td class="metadataValue">
          ${metadata.value
            ? metadata.value
            : html`<span class="placeholder">--</span>`
          }
        </td>
      </tr>
    `;
  }

  private loadServerInfo() {
    return this.restApiService
      .getConfig(true)
      .then(serverInfo => {
        let metadataList = new Array<MetadataInfo>();

        if (serverInfo?.accounts?.visibility) {
          const accountsVisibilityMetadata = {
            name: 'accounts.visibility',
            value: serverInfo.accounts.visibility,
            description: 'Controls visibility of other users\' dashboard pages and completion suggestions to web users.',
          };
          metadataList.push(accountsVisibilityMetadata);
        }

        if (serverInfo?.metadata) {
          metadataList = metadataList.concat(serverInfo.metadata);
        }

        this.metadataList = metadataList;
      })
      .finally(() => {
        this.loading = false;
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-server-info': GrServerInfo;
  }
}
