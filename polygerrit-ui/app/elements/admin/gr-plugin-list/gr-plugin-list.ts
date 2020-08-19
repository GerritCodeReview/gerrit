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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-plugin-list_html';
import {
  ListViewMixin,
  ListViewParams,
} from '../../../mixins/gr-list-view-mixin/gr-list-view-mixin';
import {customElement, property} from '@polymer/decorators';
import {GrRestApiInterface} from '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {ErrorCallback} from '../../../services/services/gr-rest-api/gr-rest-api';
import {PluginInfo} from '../../../types/common';

interface PluginInfoWithName extends PluginInfo {
  name: string;
}
export interface GrPluginList {
  $: {
    restAPI: GrRestApiInterface;
  };
}
@customElement('gr-plugin-list')
export class GrPluginList extends ListViewMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object, observer: '_paramsChanged'})
  params?: ListViewParams;

  @property({type: Number})
  _offset = 0;

  @property({type: String})
  readonly _path = '/admin/plugins';

  @property({type: Array})
  _plugins?: PluginInfoWithName[];

  @property({type: Array, computed: 'computeShownItems(_plugins)'})
  _shownPlugins?: unknown;

  @property({type: Number})
  _pluginsPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter = '';

  /** @override */
  attached() {
    super.attached();
    this.dispatchEvent(
      new CustomEvent('title-change', {
        detail: {title: 'Plugins'},
        composed: true,
        bubbles: true,
      })
    );
  }

  _paramsChanged(params: ListViewParams) {
    this._loading = true;
    this._filter = this.getFilterValue(params);
    this._offset = this.getOffsetValue(params);

    return this._getPlugins(this._filter, this._pluginsPerPage, this._offset);
  }

  _getPlugins(filter: string, pluginsPerPage: number, offset?: number) {
    const errFn: ErrorCallback = response => {
      this.dispatchEvent(
        new CustomEvent('page-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
    };
    return this.$.restAPI
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
    return this.getUrl('/', id);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-list': GrPluginList;
  }
}
