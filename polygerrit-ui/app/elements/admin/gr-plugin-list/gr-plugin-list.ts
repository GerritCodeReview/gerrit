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
import '../../../styles/gr-table-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-list-view/gr-list-view';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-plugin-list_html';
import {customElement, property} from '@polymer/decorators';
import {PluginInfo} from '../../../types/common';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {SHOWN_ITEMS_COUNT} from '../../../constants/constants';

interface PluginInfoWithName extends PluginInfo {
  name: string;
}

interface ListViewParams {
  filter?: string | null;
  offset?: number | string;
}

@customElement('gr-plugin-list')
export class GrPluginList extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * URL params passed from the router.
   */
  @property({type: Object, observer: '_paramsChanged'})
  params?: ListViewParams;

  /**
   * Offset of currently visible query results.
   */
  @property({type: Number})
  _offset = 0;

  @property({type: String})
  readonly _path = '/admin/plugins';

  @property({type: Array})
  _plugins?: PluginInfoWithName[];

  /**
   * Because  we request one more than the pluginsPerPage, _shownPlugins
   * maybe one less than _plugins.
   **/
  @property({type: Array, computed: 'computeShownItems(_plugins)'})
  _shownPlugins?: PluginInfoWithName[];

  @property({type: Number})
  _pluginsPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter = '';

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange(this, 'Plugins');
  }

  _paramsChanged(params: ListViewParams) {
    this._loading = true;
    this._filter = params?.filter ?? '';
    this._offset = Number(params?.offset ?? 0);

    return this._getPlugins(this._filter, this._pluginsPerPage, this._offset);
  }

  _getPlugins(filter: string, pluginsPerPage: number, offset?: number) {
    const errFn: ErrorCallback = response => {
      firePageError(response);
    };
    return this.restApiService
      .getPlugins(filter, pluginsPerPage, offset, errFn)
      .then(plugins => {
        if (!plugins) {
          this._plugins = [];
          return;
        }
        this._plugins = Object.keys(plugins).map(key => {
          return {...plugins[key], name: key};
        });
        this._loading = false;
      });
  }

  _status(item: PluginInfo) {
    return item.disabled === true ? 'Disabled' : 'Enabled';
  }

  _computePluginUrl(id: string) {
    return getBaseUrl() + '/' + encodeURL(id, true);
  }

  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  computeShownItems(plugins: PluginInfoWithName[]) {
    return plugins.slice(0, SHOWN_ITEMS_COUNT);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-list': GrPluginList;
  }
}
