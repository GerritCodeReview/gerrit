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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-documentation-search_html';
import {
  ListViewMixin,
  ListViewParams,
} from '../../../mixins/gr-list-view-mixin/gr-list-view-mixin';
import {getBaseUrl} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {DocResult} from '../../../types/common';

export interface GrDocumentationSearch {
  $: {
    restAPI: RestApiService & Element;
  };
}
@customElement('gr-documentation-search')
export class GrDocumentationSearch extends ListViewMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
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
  _filter = '';

  /** @override */
  attached() {
    super.attached();
    this.dispatchEvent(
      new CustomEvent('title-change', {detail: {title: 'Documentation Search'}})
    );
  }

  _paramsChanged(params: ListViewParams) {
    this._loading = true;
    this._filter = this.getFilterValue(params);

    return this._getDocumentationSearches(this._filter);
  }

  _getDocumentationSearches(filter: string) {
    this._documentationSearches = [];
    return this.$.restAPI.getDocumentationSearches(filter).then(searches => {
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-documentation-search': GrDocumentationSearch;
  }
}
