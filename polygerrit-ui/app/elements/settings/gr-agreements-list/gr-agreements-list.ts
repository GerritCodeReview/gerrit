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

import {getBaseUrl} from '../../../utils/url-util';
import {ContributorAgreementInfo} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';

@customElement('gr-agreements-list')
export class GrAgreementsList extends LitElement {
  @property({type: Array})
  _agreements?: ContributorAgreementInfo[];

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this.loadData();
  }

  loadData() {
    return this.restApiService.getAccountAgreements().then(agreements => {
      this._agreements = agreements;
    });
  }

  static get styles() {
    return [
      sharedStyles,
      css`
        #agreements .nameColumn {
          min-width: 15em;
          width: auto;
        }
        #agreements .descriptionColumn {
          width: auto;
        }
      `,
      formStyles,
    ];
  }

  renderAgreement(agreement: ContributorAgreementInfo) {
    if (!agreement) return;
    return html`
      <tr>
        <td class="nameColumn">
          <a href="${this.getUrlBase(agreement.url)}" rel="external">
            ${agreement.name}
          </a>
        </td>
        <td class="descriptionColumn">${agreement.description}</td>
      </tr>
    `;
  }

  override render() {
    return html` <div class="gr-form-styles">
      <table id="agreements">
        <thead>
          <tr>
            <th class="nameColumn">Name</th>
            <th class="descriptionColumn">Description</th>
          </tr>
        </thead>
        <tbody>
          ${(this._agreements ?? []).map(agreement =>
            this.renderAgreement(agreement)
          )}
        </tbody>
      </table>
      <a href="${this.getUrl()}">New Contributor Agreement</a>
    </div>`;
  }

  getUrl() {
    return `${getBaseUrl()}/settings/new-agreement`;
  }

  getUrlBase(item: string) {
    return `${getBaseUrl()}/${item}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-agreements-list': GrAgreementsList;
  }
}
