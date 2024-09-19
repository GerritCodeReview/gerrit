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

import '../../shared/gr-weblink/gr-weblink';
import {MetadataInfo, ServerInfo, WebLinkInfo} from '../../../types/common';
import {configModelToken} from '../../../models/config/config-model';
import {customElement, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {fireTitleChange} from '../../../utils/event-util';
import {map} from 'lit/directives/map.js';
import {resolve} from '../../../models/dependency';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {tableStyles} from '../../../styles/gr-table-styles';

@customElement('gr-server-info')
export class GrServerInfo extends LitElement {
  @state() serverInfo?: ServerInfo;

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      serverInfo => {
        this.serverInfo = serverInfo;
      }
    );
  }

  static override get styles() {
    return [
      tableStyles,
      sharedStyles,
      css`
        .genericList tr td:last-of-type {
          text-align: left;
        }
        .genericList tr th:last-of-type {
          text-align: left;
        }
        .metadataDescription,
        .metadataName,
        .metadataValue,
        .metadataWebLinks {
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
  }

  override render() {
    return html`
      <main class="gr-form-styles read-only">
        <table id="list" class="genericList">
          <tbody>
            <tr class="headerRow">
              <th class="metadataName topHeader">Name</th>
              <th class="metadataValue topHeader">Value</th>
              <th class="metadataWebLinks topHeader">Links</th>
              <th class="metadataDescription topHeader">Description</th>
            </tr>
          </tbody>
          ${this.renderServerInfoTable()}
        </table>
      </main>
    `;
  }

  private renderServerInfoTable() {
    return html`
      <tbody>
        ${map(this.getServerInfoAsMetadataInfos(), metadata =>
          this.renderServerInfo(metadata)
        )}
      </tbody>
    `;
  }

  private renderServerInfo(metadata: MetadataInfo) {
    return html`
      <tr class="table">
        <td class="metadataName">${metadata.name}</td>
        <td class="metadataValue">
          ${metadata.value
            ? metadata.value
            : html`<span class="placeholder">--</span>`}
        </td>
        <td class="metadataWebLinks">
          ${metadata.web_links
            ? map(metadata.web_links, webLink => this.renderWebLink(webLink))
            : ''}
        </td>
        <td class="metadataDescription">
          ${metadata.description ? metadata.description : ''}
        </td>
      </tr>
    `;
  }

  private renderWebLink(info: WebLinkInfo) {
    return html`<p><gr-weblink imageAndText .info=${info}></gr-weblink></p>`;
  }

  private getServerInfoAsMetadataInfos() {
    let metadataList = new Array<MetadataInfo>();

    const accountsVisibilityMetadata = this.createAccountVisibilityMetadata();
    if (accountsVisibilityMetadata) {
      metadataList.push(accountsVisibilityMetadata);
    }

    if (this.serverInfo?.metadata) {
      metadataList = metadataList.concat(this.serverInfo.metadata);
    }

    return metadataList;
  }

  private createAccountVisibilityMetadata(): MetadataInfo | undefined {
    if (this.serverInfo?.accounts?.visibility) {
      const accountsVisibilityMetadata = {
        name: 'accounts.visibility',
        value: this.serverInfo.accounts.visibility,
        description:
          "Controls visibility of other users' dashboard pages and completion suggestions to web users.",
        web_links: new Array<WebLinkInfo>(),
      };
      if (this.serverInfo?.gerrit?.doc_url) {
        const docWebLink = {
          name: 'Documentation',
          url:
            this.serverInfo.gerrit.doc_url +
            'config-gerrit.html#accounts.visibility',
        };
        accountsVisibilityMetadata.web_links.push(docWebLink);
      }
      return accountsVisibilityMetadata;
    }
    return undefined;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-server-info': GrServerInfo;
  }
}
