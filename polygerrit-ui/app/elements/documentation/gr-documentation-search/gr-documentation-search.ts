/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-list-view/gr-list-view';
import {getBaseUrl} from '../../../utils/url-util';
import {DocResult} from '../../../types/common';
import {fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {LitElement, html} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {
  createDocumentationUrl,
  documentationViewModelToken,
} from '../../../models/views/documentation';

@customElement('gr-documentation-search')
export class GrDocumentationSearch extends LitElement {
  // private but used in test
  @state() documentationSearches?: DocResult[];

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() filter = '';

  private readonly restApiService = getAppContext().restApiService;

  private readonly getViewModel = resolve(this, documentationViewModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getViewModel().state$,
      x => {
        this.filter = x?.filter ?? '';
        if (x !== undefined) this.getDocumentationSearches();
      }
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange('Documentation Search');
  }

  static override get styles() {
    return [sharedStyles, tableStyles];
  }

  override render() {
    return html` <gr-list-view
      .filter=${this.filter}
      .offset=${0}
      .loading=${this.loading}
      .path=${createDocumentationUrl()}
    >
      <table id="list" class="genericList">
        <tbody>
          <tr class="headerRow">
            <th class="name topHeader">Name</th>
            <th class="name topHeader"></th>
            <th class="name topHeader"></th>
          </tr>
          <tr id="loading" class="loadingMsg ${this.loading ? 'loading' : ''}">
            <td>Loading...</td>
          </tr>
        </tbody>
        <tbody class=${this.loading ? 'loading' : ''}>
          ${this.documentationSearches?.map(search =>
            this.renderDocumentationList(search)
          )}
        </tbody>
      </table>
    </gr-list-view>`;
  }

  private renderDocumentationList(search: DocResult) {
    return html`
      <tr class="table">
        <td class="name">
          <a href=${this.computeSearchUrl(search.url)}>${search.title}</a>
        </td>
        <td></td>
        <td></td>
      </tr>
    `;
  }

  getDocumentationSearches() {
    const filter = this.filter;
    this.loading = true;
    this.documentationSearches = [];
    return this.restApiService
      .getDocumentationSearches(filter)
      .then(searches => {
        // Late response.
        if (filter !== this.filter || !searches) {
          return;
        }
        this.documentationSearches = searches;
      })
      .finally(() => {
        this.loading = false;
      });
  }

  private computeSearchUrl(url?: string) {
    if (!url) return '';
    return `${getBaseUrl()}/${url}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-documentation-search': GrDocumentationSearch;
  }
}
