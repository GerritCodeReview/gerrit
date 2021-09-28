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
import '../../../styles/gr-table-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-list-view/gr-list-view';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-documentation-search_html';
import {getBaseUrl} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import {DocResult} from '../../../types/common';
import {fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';

export interface ListViewParams {
  filter?: string | null;
  offset?: number | string;
}

@customElement('gr-documentation-search')
export class GrDocumentationSearch extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * URL params passed from the router.
   */
  @property({type: Object, observer: '_paramsChanged'})
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
