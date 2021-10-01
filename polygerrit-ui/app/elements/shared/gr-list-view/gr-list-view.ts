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
import '@polymer/iron-icon/iron-icon';
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-list-view_html';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {property, customElement} from '@polymer/decorators';
import {fireEvent} from '../../../utils/event-util';
import {debounce, DelayedTask} from '../../../utils/async-util';

const REQUEST_DEBOUNCE_INTERVAL_MS = 200;

declare global {
  interface HTMLElementTagNameMap {
    'gr-list-view': GrListView;
  }
}

@customElement('gr-list-view')
export class GrListView extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean})
  createNew?: boolean;

  @property({type: Array})
  items?: unknown[];

  @property({type: Number})
  itemsPerPage = 25;

  @property({type: String, observer: '_filterChanged'})
  filter?: string;

  @property({type: Number})
  offset = 0;

  @property({type: Boolean})
  loading?: boolean;

  @property({type: String})
  path?: string;

  private reloadTask?: DelayedTask;

  override disconnectedCallback() {
    this.reloadTask?.cancel();
    super.disconnectedCallback();
  }

  _filterChanged(newFilter?: string, oldFilter?: string) {
    // newFilter can be empty string and then !newFilter === true
    if (!newFilter && !oldFilter) {
      return;
    }

    this._debounceReload(newFilter);
  }

  _debounceReload(filter?: string) {
    this.reloadTask = debounce(
      this.reloadTask,
      () => {
        if (this.path) {
          if (filter) {
            return page.show(
              `${this.path}/q/filter:` + encodeURL(filter, false)
            );
          }
          return page.show(this.path);
        }
      },
      REQUEST_DEBOUNCE_INTERVAL_MS
    );
  }

  _createNewItem() {
    fireEvent(this, 'create-clicked');
  }

  _computeNavLink(
    offset: number,
    direction: number,
    itemsPerPage: number,
    filter: string | undefined,
    path = ''
  ) {
    // Offset could be a string when passed from the router.
    offset = +(offset || 0);
    const newOffset = Math.max(0, offset + itemsPerPage * direction);
    let href = getBaseUrl() + path;
    if (filter) {
      href += '/q/filter:' + encodeURL(filter, false);
    }
    if (newOffset > 0) {
      href += `,${newOffset}`;
    }
    return href;
  }

  _computeCreateClass(createNew?: boolean) {
    return createNew ? 'show' : '';
  }

  _hidePrevArrow(loading?: boolean, offset?: number) {
    return loading || offset === 0;
  }

  _hideNextArrow(loading?: boolean, items?: unknown[]) {
    if (loading || !items || !items.length) {
      return true;
    }
    const lastPage = items.length < this.itemsPerPage + 1;
    return lastPage;
  }

  // TODO: fix offset (including itemsPerPage)
  // to either support a decimal or make it go to the nearest
  // whole number (e.g 3).
  _computePage(offset: number, itemsPerPage: number) {
    return offset / itemsPerPage + 1;
  }
}
