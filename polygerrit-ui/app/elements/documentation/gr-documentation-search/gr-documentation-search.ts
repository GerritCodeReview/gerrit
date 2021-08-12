/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../shared/gr-list-view/gr-list-view';
import {getBaseUrl} from '../../../utils/url-util';
import {DocResult} from '../../../types/common';
import {fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ListViewParams} from '../../gr-app-types';
import {sharedStyles} from '../../../styles/shared-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {GrLitElement} from '../../lit/gr-lit-element';
import {customElement, html, property, PropertyValues} from 'lit-element';

@customElement('gr-documentation-search')
export class GrDocumentationSearch extends GrLitElement {
  /**
   * URL params passed from the router.
   */
  @property({type: Object})
  params?: ListViewParams;

  @property({type: Array})
  _documentationSearches?: DocResult[];

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter?: string;

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange(this, 'Documentation Search');
  }

  static get styles() {
    return [
      sharedStyles,
      tableStyles,
    ];
  }

  render() {
    return html` <gr-list-view
      .filter="${this._filter}"
      .offset="${0}"
      .loading="${this._loading}"
      .path="/Documentation"
    >
      <table id="list" class="genericList">
        <tbody>
          <tr class="headerRow">
            <th class="name topHeader">Name</th>
            <th class="name topHeader"></th>
            <th class="name topHeader"></th>
          </tr>
          <tr
            id="loading"
            class="loadingMsg ${this.computeLoadingClass(this._loading)}"
          >
            <td>Loading...</td>
          </tr>
        </tbody>
        <tbody class="${this.computeLoadingClass(this._loading)}">
          ${this._documentationSearches.map(
            search => html`
              <tr class="table">
                <td class="name">
                  <a href="${this._computeSearchUrl(search.url)}"
                    >${search.title}</a
                  >
                </td>
                <td></td>
                <td></td>
              </tr>
            `
          )}
        </tbody>
      </table>
    </gr-list-view>`;
  }

  updated(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this._paramsChanged(this.params);
    }
  }

  _paramsChanged(params: ListViewParams) {
    this._loading = true;
    this._filter = params?.filter ?? '';

    return this._getDocumentationSearches(this._filter);
  }

  _getDocumentationSearches(filter: string) {
    this._documentationSearches = [];
    return this.restApiService
      .getDocumentationSearches(filter)
      .then(searches => {
        // Late response.
        if (filter !== this._filter || !searches) {
          return;
        }
        this._documentationSearches = searches;
        this._loading = false;
      });
  }

  _computeSearchUrl(url?: string) {
    if (!url) {
      return '';
    }
    return `${getBaseUrl()}/${url}`;
  }

  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-documentation-search': GrDocumentationSearch;
  }
}
