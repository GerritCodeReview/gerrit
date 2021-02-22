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
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-create-pointer-dialog_html';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {customElement, property, observe} from '@polymer/decorators';
import {BranchName, RepoName} from '../../../types/common';
import {appContext} from '../../../services/app-context';

enum DetailType {
  branches = 'branches',
  tags = 'tags',
}

@customElement('gr-create-pointer-dialog')
export class GrCreatePointerDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  detailType?: string;

  @property({type: String})
  repoName?: RepoName;

  @property({type: Boolean, notify: true})
  hasNewItemName = false;

  @property({type: String})
  itemDetail?: DetailType;

  @property({type: String})
  _itemName?: BranchName;

  @property({type: String})
  _itemRevision?: string;

  @property({type: String})
  _itemAnnotation?: string;

  @observe('_itemName')
  _updateItemName(name?: string) {
    this.hasNewItemName = !!name;
  }

  private readonly restApiService = appContext.restApiService;

  handleCreateItem() {
    if (!this.repoName) {
      throw new Error('repoName name is not set');
    }
    if (!this._itemName) {
      throw new Error('itemName name is not set');
    }
    const USE_HEAD = this._itemRevision ? this._itemRevision : 'HEAD';
    const url = `${getBaseUrl()}/admin/repos/${encodeURL(this.repoName, true)}`;
    if (this.itemDetail === DetailType.branches) {
      return this.restApiService
        .createRepoBranch(this.repoName, this._itemName, {revision: USE_HEAD})
        .then(itemRegistered => {
          if (itemRegistered.status === 201) {
            page.show(`${url},branches`);
          }
        });
    } else if (this.itemDetail === DetailType.tags) {
      return this.restApiService
        .createRepoTag(this.repoName, this._itemName, {
          revision: USE_HEAD,
          message: this._itemAnnotation || undefined,
        })
        .then(itemRegistered => {
          if (itemRegistered.status === 201) {
            page.show(`${url},tags`);
          }
        });
    }
    throw new Error(`Invalid itemDetail: ${this.itemDetail}`);
  }

  _computeHideItemClass(type: DetailType) {
    return type === DetailType.branches ? 'hideItem' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-pointer-dialog': GrCreatePointerDialog;
  }
}
