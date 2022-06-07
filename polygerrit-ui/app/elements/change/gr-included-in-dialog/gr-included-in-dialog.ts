/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import {IncludedInInfo, NumericChangeId} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';

interface DisplayGroup {
  title: string;
  items: string[];
}

@customElement('gr-included-in-dialog')
export class GrIncludedInDialog extends LitElement {
  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  @property({type: Object})
  changeNum?: NumericChangeId;

  // private but used in test
  @state() includedIn?: IncludedInInfo;

  @state() private loaded = false;

  // private but used in test
  @state() filterText = '';

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      fontStyles,
      sharedStyles,
      css`
        :host {
          background-color: var(--dialog-background-color);
          display: block;
          max-height: 80vh;
          overflow-y: auto;
          padding: 4.5em var(--spacing-l) var(--spacing-l) var(--spacing-l);
        }
        header {
          background-color: var(--dialog-background-color);
          border-bottom: 1px solid var(--border-color);
          left: 0;
          padding: var(--spacing-l);
          position: absolute;
          right: 0;
          top: 0;
        }
        #title {
          display: inline-block;
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
          margin-top: var(--spacing-xs);
        }
        #filterInput {
          display: block;
          float: right;
          margin: 0 var(--spacing-l);
          padding: var(--spacing-xs);
        }
        .closeButtonContainer {
          float: right;
        }
        ul {
          margin-bottom: var(--spacing-l);
        }
        ul li {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          background: var(--chip-background-color);
          display: inline-block;
          margin: 0 var(--spacing-xs) var(--spacing-s) var(--spacing-xs);
          padding: var(--spacing-xs) var(--spacing-s);
        }
      `,
    ];
  }

  override render() {
    return html`
      <header>
        <h1 id="title" class="heading-1">Included In:</h1>
        <span class="closeButtonContainer">
          <gr-button
            id="closeButton"
            link
            @click=${(e: Event) => {
              this.handleCloseTap(e);
            }}
            >Close</gr-button
          >
        </span>
        <iron-input
          id="filterInput"
          .bindValue=${this.filterText}
          @bind-value-changed=${(e: BindValueChangeEvent) => {
            this.filterText = e.detail.value;
          }}
        >
          <input placeholder="Filter" />
        </iron-input>
      </header>
      ${this.renderLoading()}
      ${this.computeGroups().map(group => this.renderGroup(group))}
    `;
  }

  private renderLoading() {
    if (this.loaded) return;

    return html`<div>Loading...</div>`;
  }

  private renderGroup(group: DisplayGroup) {
    return html`
      <div>
        <span>${group.title}:</span>
        <ul>
          ${group.items.map(item => this.renderGroupItem(item))}
        </ul>
      </div>
    `;
  }

  private renderGroupItem(item: string) {
    return html`<li>${item}</li>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('changeNum')) {
      this.resetData();
    }
  }

  loadData() {
    if (!this.changeNum) {
      return Promise.reject(new Error('missing required property changeNum'));
    }
    this.filterText = '';
    return this.restApiService
      .getChangeIncludedIn(this.changeNum)
      .then(configs => {
        if (!configs) {
          return;
        }
        this.includedIn = configs;
        this.loaded = true;
      });
  }

  private resetData() {
    this.includedIn = undefined;
    this.loaded = false;
  }

  // private but used in test
  computeGroups() {
    if (!this.includedIn || this.filterText === undefined) {
      return [];
    }

    const filter = (item: string) =>
      !this.filterText.length ||
      item.toLowerCase().indexOf(this.filterText.toLowerCase()) !== -1;

    const groups: DisplayGroup[] = [
      {title: 'Branches', items: this.includedIn.branches.filter(filter)},
      {title: 'Tags', items: this.includedIn.tags.filter(filter)},
    ];
    if (this.includedIn.external) {
      for (const externalKey of Object.keys(this.includedIn.external)) {
        groups.push({
          title: externalKey,
          items: this.includedIn.external[externalKey].filter(filter),
        });
      }
    }
    return groups.filter(g => g.items.length);
  }

  private handleCloseTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('close', {
        composed: true,
        bubbles: false,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-included-in-dialog': GrIncludedInDialog;
  }
}
