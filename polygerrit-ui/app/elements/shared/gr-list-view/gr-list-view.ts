/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../gr-button/gr-button';
import '../gr-icon/gr-icon';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {fire} from '../../../utils/event-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {BindValueChangeEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {formStyles} from '../../../styles/form-styles';

const REQUEST_DEBOUNCE_INTERVAL_MS = 200;

declare global {
  interface HTMLElementTagNameMap {
    'gr-list-view': GrListView;
  }
  interface HTMLElementEventMap {
    'create-clicked': CustomEvent<{}>;
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

  /** Must include the base path. */
  @property({type: String})
  path?: string;

  private reloadTask?: DelayedTask;

  private readonly getNavigation = resolve(this, navigationToken);

  override disconnectedCallback() {
    this.reloadTask?.cancel();
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      formStyles,
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
          color: var(--deemphasized-text-color);
        }
        gr-icon {
          font-size: 1.85rem;
          margin-left: 16px;
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
        Page ${this.computePage()}
        <a
          id="prevArrow"
          href=${this.computeNavLink(-1)}
          ?hidden=${this.loading || this.offset === 0}
        >
          <gr-icon icon="chevron_left"></gr-icon>
        </a>
        <a
          id="nextArrow"
          href=${this.computeNavLink(1)}
          ?hidden=${this.hideNextArrow()}
        >
          <gr-icon icon="chevron_right"></gr-icon>
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
          this.getNavigation().setUrl(
            `${this.path}/q/filter:${encodeURL(filter)}`
          );
          return;
        }
        this.getNavigation().setUrl(this.path);
      },
      REQUEST_DEBOUNCE_INTERVAL_MS
    );
  }

  private createNewItem() {
    fire(this, 'create-clicked', {});
  }

  // private but used in test
  computeNavLink(direction: number) {
    // Offset could be a string when passed from the router.
    const offset = +(this.offset || 0);
    const newOffset = Math.max(0, offset + this.itemsPerPage * direction);
    // Note that `this.path` already includes the base URL, if set and non-empty;
    let href = this.path || getBaseUrl();
    if (this.filter) {
      href += '/q/filter:' + encodeURL(this.filter);
    }
    if (newOffset > 0) {
      href += `,${newOffset}`;
    }
    return href;
  }

  // private but used in test
  hideNextArrow() {
    if (this.loading || !this.items?.length) return true;
    const lastPage = this.items.length < this.itemsPerPage + 1;
    return lastPage;
  }

  // TODO: fix offset (including itemsPerPage)
  // to either support a decimal or make it go to the nearest
  // whole number (e.g 3).
  // private but used in test
  computePage() {
    return this.offset / this.itemsPerPage + 1;
  }

  private handleFilterBindValueChanged(e: BindValueChangeEvent) {
    this.filter = e.detail.value;
  }
}
