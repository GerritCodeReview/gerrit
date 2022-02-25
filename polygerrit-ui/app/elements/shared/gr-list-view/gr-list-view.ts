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
import '../gr-button/gr-button';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {fireEvent} from '../../../utils/event-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';

const REQUEST_DEBOUNCE_INTERVAL_MS = 200;

declare global {
  interface HTMLElementTagNameMap {
    'gr-list-view': GrListView;
  }
}

@customElement('gr-list-view')
export class GrListView extends LitElement {
  @property({type: Boolean})
  createNew?: boolean;

  @property({type: Array})
  items?: unknown[];

  @property({type: Number})
  itemsPerPage = 25;

  @property({type: String})
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

  static override get styles() {
    return [
      sharedStyles,
      css`
        #filter {
          max-width: 25em;
        }
        #filter:focus {
          outline: none;
        }
        #topContainer {
          align-items: center;
          display: flex;
          height: 3rem;
          justify-content: space-between;
          margin: 0 var(--spacing-l);
        }
        #createNewContainer:not(.show) {
          display: none;
        }
        a {
          color: var(--primary-text-color);
          text-decoration: none;
        }
        a:hover {
          text-decoration: underline;
        }
        nav {
          align-items: center;
          display: flex;
          height: 3rem;
          justify-content: flex-end;
          margin-right: 20px;
        }
        nav,
        iron-icon {
          color: var(--deemphasized-text-color);
        }
        iron-icon {
          height: 1.85rem;
          margin-left: 16px;
          width: 1.85rem;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div id="topContainer">
        <div class="filterContainer">
          <label>Filter:</label>
          <iron-input
            .bindValue=${this.filter}
            @bind-value-changed=${this.handleFilterBindValueChanged}
          >
            <input type="text" id="filter" />
          </iron-input>
        </div>
        <div id="createNewContainer" class=${this.createNew ? 'show' : ''}>
          <gr-button
            id="createNew"
            primary
            link
            @click=${() => this.createNewItem()}
          >
            Create New
          </gr-button>
        </div>
      </div>
      <slot></slot>
      <nav>
        Page ${this.computePage(this.offset, this.itemsPerPage)}
        <a
          id="prevArrow"
          href=${this.computeNavLink(
            this.offset,
            -1,
            this.itemsPerPage,
            this.filter,
            this.path
          )}
          ?hidden=${this.loading || this.offset === 0}
        >
          <iron-icon icon="gr-icons:chevron-left"></iron-icon>
        </a>
        <a
          id="nextArrow"
          href=${this.computeNavLink(
            this.offset,
            1,
            this.itemsPerPage,
            this.filter,
            this.path
          )}
          ?hidden=${this.hideNextArrow(this.loading, this.items)}
        >
          <iron-icon icon="gr-icons:chevron-right"></iron-icon>
        </a>
      </nav>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    // We have to do this for the tests.
    if (changedProperties.has('filter')) {
      this.filterChanged(
        this.filter,
        changedProperties.get('filter') as string
      );
    }
  }

  private filterChanged(newFilter?: string, oldFilter?: string) {
    // newFilter can be empty string and then !newFilter === true
    if (!newFilter && !oldFilter) {
      return;
    }
    this.debounceReload(newFilter);
  }

  // private but used in test
  debounceReload(filter?: string) {
    this.reloadTask = debounce(
      this.reloadTask,
      () => {
        if (!this.isConnected || !this.path) return;
        if (filter) {
          page.show(`${this.path}/q/filter:${encodeURL(filter, false)}`);
          return;
        }
        page.show(this.path);
      },
      REQUEST_DEBOUNCE_INTERVAL_MS
    );
  }

  private createNewItem() {
    fireEvent(this, 'create-clicked');
  }

  // private but used in test
  computeNavLink(
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

  // private but used in test
  hideNextArrow(loading?: boolean, items?: unknown[]) {
    if (loading || !items || !items.length) {
      return true;
    }
    const lastPage = items.length < this.itemsPerPage + 1;
    return lastPage;
  }

  // TODO: fix offset (including itemsPerPage)
  // to either support a decimal or make it go to the nearest
  // whole number (e.g 3).
  // private but used in test
  computePage(offset: number, itemsPerPage: number) {
    return offset / itemsPerPage + 1;
  }

  private handleFilterBindValueChanged(e: BindValueChangeEvent) {
    this.filter = e.detail.value;
  }
}
