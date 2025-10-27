/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/button/text-button';
import '@material/web/icon/icon';
import '../shared/gr-icon/gr-icon';

import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

import {ContextItemType} from '../../api/ai-code-review';
import {chatModelToken, Turn} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {subscribe} from '../lit/subscription-controller';

/**
 * A component to display a dropdown with references used by the model.
 */
@customElement('references-dropdown')
export class ReferencesDropdown extends LitElement {
  private readonly getChatModel = resolve(this, chatModelToken);

  @property({type: Number}) turnIndex = 0;

  @state() private turns: readonly Turn[] = [];

  @state() private contextItemTypes: readonly ContextItemType[] = [];

  @state() private showReferences = false;

  @state() private listWarnings = false;

  static override styles = [
    css`
      :host {
        display: block;
      }
      .references-dropdown-container {
        display: flex;
        flex-direction: row;
        align-items: center;
      }
      .references-dropdown-content {
        display: block;
        gap: var(--spacing-m);
        padding: var(--spacing-m) var(--spacing-m) 0;
      }
      .button-outer-wrapper {
        display: inline-block;
        margin-bottom: 4px;
        max-width: 100%;
      }
      .reference-button {
        height: 32px;
        background-color: var(--background-color-tertiary);
        border-radius: 8px;
        max-width: inherit;
        /* For <a> and <button> to look like buttons. */
        box-sizing: border-box;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 0 12px;
        text-decoration: none;
        border: none;
        font: inherit;
        cursor: pointer;
      }
      .reference-button.cl-references-button,
      .reference-button.list-warnings-button,
      .reference-button.selection-button {
        background-color: transparent;
        margin-bottom: 4px;
      }
      .reference-button[disabled] {
        cursor: default;
      }
      .reference-wrapper {
        display: flex;
        align-items: center;
        vertical-align: middle;
        gap: var(--spacing-m);
        color: var(--focus-outline-color);
        padding-top: 2px;
        /* TODO: check typography variables */
        /* font-family: var(--font-family-title); */
        font-weight: var(--font-weight-medium);
        font-size: var(--font-size-small);
        line-height: var(--line-height-small);
      }
      .reference-wrapper .display-text {
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
        flex: 0 1 auto;
      }
      .reference-wrapper .additional-text {
        flex-shrink: 0;
      }
      .reference-wrapper .reference-icon {
        flex: 0 0 auto;
        height: 18px;
        width: 18px;
      }
      .reference-wrapper .reference-icon.selection-icon {
        max-width: 100%;
        max-height: 100%;
        color: var(--file-chip-avatar-color);
        margin-right: 3px;
        margin-left: -3px;
        margin-top: -5px;
        transform: scale(0.85);
      }
      .reference-button .expand-icon {
        transform: scale(0.5);
      }
      .cl-references,
      .warnings-list {
        margin: var(--spacing-m) 0;
        padding-left: 20px;
        list-style-type: disc;
      }
      .cl-references li,
      .warnings-list li {
        color: var(--focus-outline-color);
        overflow-wrap: break-word;
        font-size: var(--font-size-small);
        line-height: var(--line-height-small);
      }
      ul.cl-references-file-diffs {
        padding-left: 20px;
        list-style-type: disc;
      }
      .warning-icon {
        color: var(--warning-icon, purple);
      }
      .warning-icon.reference-icon {
        transform: scale(0.8);
        margin-left: -2px;
        margin-top: -6px;
        padding-right: 2px;
      }
    `,
  ];

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().turns$,
      x => (this.turns = x ?? [])
    );
    subscribe(
      this,
      () => this.getChatModel().contextItemTypes$,
      x => (this.contextItemTypes = x ?? [])
    );
  }

  private get turn(): Turn | undefined {
    if (!this.turns || this.turnIndex >= this.turns.length) {
      return undefined;
    }
    return this.turns[this.turnIndex];
  }

  private get dynamicReferences() {
    return this.turn?.geminiMessage.references ?? [];
  }

  get validDynamicReferences() {
    return this.dynamicReferences.filter(reference => !reference.errorMsg);
  }

  get dynamicReferencesWithErrors() {
    return this.dynamicReferences.filter(reference => !!reference.errorMsg);
  }

  get totalReferencesCount() {
    return this.validDynamicReferences.length;
  }

  get hasErrors() {
    return this.dynamicReferencesWithErrors.length !== 0;
  }

  private toggleShowReferences() {
    this.showReferences = !this.showReferences;
  }

  private toggleListWarnings() {
    this.listWarnings = !this.listWarnings;
  }

  private renderReferenceIcon(type: string) {
    const contextItemType = this.contextItemTypes.find(
      contextItemType => contextItemType.id === type
    );
    const icon = contextItemType?.icon ?? '';
    if (!icon) return nothing;
    return html`<gr-icon class="reference-icon" .icon=${icon}></gr-icon>`;
  }

  override render() {
    if (this.totalReferencesCount === 0) return nothing;

    return html`
      <div class="references-dropdown-container">
        <md-text-button
          @click=${this.toggleShowReferences}
          class="references-dropdown-button"
        >
          <md-icon
            >${this.showReferences ? 'expand_less' : 'expand_more'}</md-icon
          >
          Context used (${this.totalReferencesCount})
        </md-text-button>
        ${when(
          this.hasErrors,
          () => html`
            <md-icon
              class="warning-icon"
              title="There were errors loading some references."
              >warning_amber</md-icon
            >
          `
        )}
      </div>

      ${when(this.showReferences, () => this.renderDropdownContent())}
    `;
  }

  private renderDropdownContent() {
    return html`
      <div class="references-dropdown-content">
        ${this.validDynamicReferences.map(
          reference => html`
            <div class="button-outer-wrapper">
              <a
                class="reference-button"
                .href=${reference.externalUrl}
                target="_blank"
                .title=${reference.tooltip ?? ''}
              >
                <div class="reference-wrapper">
                  ${this.renderReferenceIcon(reference.type)}
                  <span class="display-text">${reference.displayText}</span>
                  ${when(
                    reference.secondaryText,
                    () => html`<span class="additional-text"
                      >- ${reference.secondaryText}</span
                    >`
                  )}
                </div>
              </a>
            </div>
            <br />
          `
        )}
        ${when(
          this.hasErrors,
          () => html`
            <button
              class="reference-button list-warnings-button"
              @click=${this.toggleListWarnings}
            >
              <div class="reference-wrapper">
                <md-icon class="reference-icon warning-icon"
                  >warning_amber</md-icon
                >
                <span class="display-text">Warnings</span>
                <md-icon class="expand-icon"
                  >${this.listWarnings ? 'expand_less' : 'expand_more'}</md-icon
                >
              </div>
            </button>
          `
        )}
        ${when(this.hasErrors && this.listWarnings, () =>
          this.renderWarningsList()
        )}
      </div>
    `;
  }

  private renderWarningsList() {
    return html`
      <ul class="warnings-list">
        ${this.dynamicReferencesWithErrors.map(
          reference => html`
            <li>
              Failed to load ${reference.displayText}: ${reference.errorMsg}
            </li>
          `
        )}
      </ul>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'references-dropdown': ReferencesDropdown;
  }
}
