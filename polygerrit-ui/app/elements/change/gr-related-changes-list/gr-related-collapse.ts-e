/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html, nothing, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {Interaction} from '../../../constants/reporting';
import {fontStyles} from '../../../styles/gr-font-styles';

/** What is the maximum number of shown changes in collapsed list? */
export const DEFALT_NUM_CHANGES_WHEN_COLLAPSED = 3;

@customElement('gr-related-collapse')
export class GrRelatedCollapse extends LitElement {
  @property()
  override title = '';

  @property({type: Boolean})
  showAll = false;

  @property({type: Boolean, reflect: true})
  collapsed = true;

  @property({type: Number})
  length = 0;

  @property({type: Number})
  numChangesWhenCollapsed = DEFALT_NUM_CHANGES_WHEN_COLLAPSED;

  private readonly reporting = getAppContext().reportingService;

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        .title {
          color: var(--deemphasized-text-color);
          display: flex;
          align-self: flex-end;
          margin-left: 20px;
        }
        gr-button {
          display: flex;
        }
        gr-button iron-icon {
          color: inherit;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
        .container {
          justify-content: space-between;
          display: flex;
          margin-bottom: var(--spacing-s);
        }
        :host(.first) .container {
          margin-bottom: var(--spacing-m);
        }
      `,
    ];
  }

  override render() {
    const title = html`<h3 class="title heading-3">${this.title}</h3>`;

    const collapsible = this.length > this.numChangesWhenCollapsed;
    this.collapsed = !this.showAll && collapsible;

    let button: TemplateResult | typeof nothing = nothing;
    if (collapsible) {
      let buttonText = 'Show less';
      let buttonIcon = 'expand-less';
      if (!this.showAll) {
        buttonText = `Show all (${this.length})`;
        buttonIcon = 'expand-more';
      }
      button = html`<gr-button link="" @click=${this.toggle}
        >${buttonText}<iron-icon icon="gr-icons:${buttonIcon}"></iron-icon
      ></gr-button>`;
    }

    return html`<div class="container">${title}${button}</div>
      <div><slot></slot></div>`;
  }

  private toggle(e: MouseEvent) {
    e.stopPropagation();
    this.showAll = !this.showAll;
    this.reporting.reportInteraction(Interaction.TOGGLE_SHOW_ALL_BUTTON, {
      sectionName: this.title,
      toState: this.showAll ? 'Show all' : 'Show less',
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-collapse': GrRelatedCollapse;
  }
}
