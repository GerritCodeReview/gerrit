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
import {ListViewParams} from '../../gr-app-types';
import {sharedStyles} from '../../../styles/shared-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {LitElement, PropertyValues, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';

@customElement('gr-documentation-search')
export class GrDocumentationSearch extends LitElement {
  /**
   * URL params passed from the router.
   */
  @property({type: Object})
  params?: ListViewParams;

  // private but used in test
  @state() documentationSearches?: DocResult[];

  // private but used in test
  @state() loading = true;

  @state() private filter = '';

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange(this, 'Documentation Search');
  }

  static override get styles() {
    return [sharedStyles, tableStyles];
  }

  override render() {
    return html` <gr-list-view
      .filter=${this.filter}
      .offset=${0}
      .loading=${this.loading}
      .path=${'/Documentation'}
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

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  // private but used in test
  paramsChanged() {
    this.loading = true;
    this.filter = this.params?.filter ?? '';

    return this.getDocumentationSearches(this.filter);
  }

  private getDocumentationSearches(filter: string) {
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
