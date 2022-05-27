/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {getBaseUrl} from '../../../utils/url-util';
import {ContributorAgreementInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';

@customElement('gr-agreements-list')
export class GrAgreementsList extends LitElement {
  @property({type: Array})
  _agreements?: ContributorAgreementInfo[];

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this.loadData();
  }

  loadData() {
    return this.restApiService.getAccountAgreements().then(agreements => {
      this._agreements = agreements;
    });
  }

  static override get styles() {
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
          <a href=${this.getUrlBase(agreement?.url)} rel="external">
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
      <a href=${this.getUrl()}>New Contributor Agreement</a>
    </div>`;
  }

  getUrl() {
    return `${getBaseUrl()}/settings/new-agreement`;
  }

  getUrlBase(item?: string) {
    return item ? `${getBaseUrl()}/${item}` : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-agreements-list': GrAgreementsList;
  }
}
