/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/iconbutton/icon-button.js';
import '@material/web/progress/circular-progress.js';

import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {when} from 'lit/directives/when.js';
import {ifDefined} from 'lit/directives/if-defined.js';

import '../shared/gr-icon/gr-icon';
import '../shared/gr-button/gr-button';
import '../shared/gr-tooltip-content/gr-tooltip-content';

import {Action, ContextItemType} from '../../api/ai-code-review';
import {chatModelToken} from '../../models/chat/chat-model';
import {parseLink} from '../../models/chat/context-item-util';

import {resolve} from '../../models/dependency';
import {isDefined} from '../../types/types';
import {fireAlert} from '../../utils/event-util';
import {subscribe} from '../lit/subscription-controller';
import {materialStyles} from '../../styles/gr-material-styles';
import {modalStyles} from '../../styles/gr-modal-styles';

/**
 * A component that renders a single action as a clickable chip on the chat
 * splash page. Clicking the chip initiates a chat or action based on the
 * provided `Action` object.
 */
@customElement('splash-page-action')
export class SplashPageAction extends LitElement {
  private static readonly COLLAPSED_HEIGHT = 100;

  @property({type: Object}) action?: Action;

  @property({type: Boolean}) isFirst = false;

  @property({type: Boolean}) isLast = false;

  @state() contextItemTypes: readonly ContextItemType[] = [];

  @state() private isInstructionExpanded = false;

  @state() private isFilesExpanded = false;

  @state() private showExpandButton = false;

  @query('#detailsModal') private detailsModal?: HTMLDialogElement;

  private readonly getChatModel = resolve(this, chatModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().contextItemTypes$,
      types => (this.contextItemTypes = types)
    );
  }

  static override styles = [
    materialStyles,
    modalStyles,
    css`
      :host {
        display: flex;
        justify-content: center;
        flex-wrap: wrap;
        width: 100%;
        position: relative;
      }

      .action-chip {
        display: flex;
        background-color: var(--background-color-tertiary);
        color: var(--primary-default);
        height: 60px;
        align-items: center;
        border-radius: 4px;
        margin: 0;
        width: 100%;
        overflow: hidden;
        cursor: pointer;
        --md-assist-chip-outline-width: 0;
      }

      .action-chip[disabled] {
        opacity: 0.6;
        cursor: default;
      }

      .action-chip.first-action-chip {
        border-top-left-radius: 16px;
        border-top-right-radius: 16px;
      }

      .action-chip.last-action-chip {
        border-bottom-left-radius: 16px;
        border-bottom-right-radius: 16px;
      }

      .action-chip.custom-action-chip {
        background-color: var(--custom-action-chip-bg-color);
      }

      .action-icon {
        padding: 4px;
        border-radius: 8px;
        background-color: var(--background-color-primary);
        flex-shrink: 0;
      }
      .action-text-container {
        display: flex;
        flex-direction: column;
        height: 100%;
        overflow: hidden;
      }
      .main-action-text-container {
        margin-left: 20px;
        font-weight: 400;
        display: flex;
        align-items: center;
      }
      .main-action-text-container.has-subtext {
        margin-top: 12px;
        margin-bottom: -2px;
      }
      .action-text {
        font-family: var(--font-family);
        font-size: var(--font-size-normal);
        font-weight: var(--font-weight-normal);
        line-height: var(--line-height-normal);
        color: var(--primary-text-color);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .action-subtext {
        vertical-align: super;
        margin-left: 5px;
        padding: 0px 15px 8px;
        font-size: 0.8em;
        color: var(--chat-splash-page-question-color);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .action-subtext.is-passed {
        color: var(--file-reviewed-color);
      }
      .action-subtext.is-actionable {
        color: var(--tonal-red);
      }
      .info-button {
        margin-left: 10px;
        margin-right: 10px;
        font-size: 24px;
        --button-background-color: transparent;
      }
      .info-button gr-icon {
        color: inherit;
      }
      .chip-content {
        display: flex;
        align-items: center;
      }
      .modalHeader {
        padding: var(--spacing-l) var(--spacing-xl);
        background-color: var(--dialog-background-color);
        border-bottom: 1px solid var(--border-color);
        font-weight: var(--font-weight-medium);
      }
      .modalActions {
        padding: var(--spacing-l) var(--spacing-xl);
        background-color: var(--dialog-background-color);
        border-top: 1px solid var(--border-color);
        display: flex;
        justify-content: flex-end;
      }
      .detailsContent {
        padding: var(--spacing-m) var(--spacing-xl);
        background-color: var(--dialog-background-color);
        flex: 1;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
      .info-button:hover {
        background-color: var(--hover-background-color, rgba(0, 0, 0, 0.08));
        border-radius: 50%;
      }
      .container {
        position: relative;
        display: flex;
        width: 100%;
        align-items: center;
      }
      .action-chip {
        width: 100%;
      }
      .info-button-container {
        position: absolute;
        right: var(--spacing-s);
        top: 50%;
        transform: translateY(-50%);
        z-index: 1;
      }
      #detailsModal {
        width: calc(72ch + 2px + 2 * var(--spacing-m) + 0.4px);
        max-width: 90vw;
        max-height: 80vh;
      }
      #detailsModal > div {
        display: flex;
        flex-direction: column;
        height: 100%;
      }
      .description-section {
        margin-bottom: var(--spacing-m);
      }
      .modal-row {
        display: flex;
        align-items: flex-start;
        margin-top: var(--spacing-m);
        gap: var(--spacing-s);
      }
      .modal-row gr-icon {
        color: var(--deemphasized-text-color);
        margin-top: 2px;
      }
      .modal-row-content {
        display: flex;
        flex-direction: column;
      }
      .modal-row-title {
        color: var(--deemphasized-text-color);
        font-weight: var(--font-weight-bold);
      }
      .modal-row-text {
        color: var(--primary-text-color);
      }
      .modal-row-text a {
        color: var(--info-foreground);
      }
      .instruction-row {
        flex: 1;
        min-height: 0;
      }
      .instruction-row .modal-row-content {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
      .instruction-text {
        flex: 1;
      }
      .instruction-text.collapsed {
        max-height: ${SplashPageAction.COLLAPSED_HEIGHT}px;
        overflow: hidden;
      }
      .instruction-text.expanded {
        max-height: none;
        overflow-y: auto;
      }
      .expand-button {
        color: var(--link-color);
        cursor: pointer;
        padding: var(--spacing-xs) 0;
        font-size: var(--font-size-small);
        background: none;
        border: none;
        text-align: left;
      }
      .expand-button:hover {
        text-decoration: underline;
      }
      .file-list {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-xs);
      }
      .file-item {
        word-break: break-all;
        color: var(--primary-text-color);
      }
      .link-row {
        display: flex;
        align-items: center;
        gap: var(--spacing-xs);
        color: var(--link-color);
        cursor: pointer;
      }
    `,
  ];

  override render() {
    if (!this.action) return;

    const chipClasses = {
      'action-chip': true,
      'first-action-chip': this.isFirst,
      'last-action-chip': this.isLast,
    };

    return html`
      <div class="container">
        <md-assist-chip
          class=${classMap(chipClasses)}
          title=${this.action.hover_text ?? ''}
          @click=${this.handleAction}
        >
          <div class="chip-content">
            <gr-icon
              class="action-icon"
              icon=${this.action.icon ?? 'ai'}
            ></gr-icon>
            <div class="action-text-container">
              <div
                class=${classMap({
                  'main-action-text-container': true,
                  'has-subtext': !!this.action.subtext,
                })}
              >
                <span class="action-text">${this.action.display_text}</span>
              </div>
              ${when(
                this.action.subtext,
                () => html` <span
                  class=${classMap({
                    'action-subtext': true,
                  })}
                  >${this.action?.subtext}</span
                >`
              )}
            </div>
          </div>
        </md-assist-chip>
        <gr-tooltip-content
          class="info-button-container"
          has-tooltip
          title="Capability details"
        >
          <gr-button
            flatten
            class="info-button"
            @click=${this.displayDetailsCard}
          >
            <gr-icon icon="info"></gr-icon>
          </gr-button>
        </gr-tooltip-content>
      </div>
      <dialog id="detailsModal" tabindex="-1">
        <div role="dialog" aria-labelledby="detailsTitle">
          <h3 class="heading-3 modalHeader" id="detailsTitle">
            ${this.action?.display_text}
          </h3>
          <div class="detailsContent">
            ${when(
              this.action?.initial_user_prompt,
              () => html`
                <div class="modal-row instruction-row">
                  <gr-icon icon="terminal"></gr-icon>
                  <div class="modal-row-content">
                    <div class="modal-row-title">Instruction:</div>
                    <div
                      class="modal-row-text instruction-text ${this
                        .isInstructionExpanded
                        ? 'expanded'
                        : 'collapsed'}"
                    >
                      ${this.action?.initial_user_prompt}
                    </div>
                    ${when(
                      this.showExpandButton,
                      () => html`
                        <button
                          class="expand-button"
                          aria-expanded=${this.isInstructionExpanded}
                          @click=${() =>
                            (this.isInstructionExpanded =
                              !this.isInstructionExpanded)}
                        >
                          ${this.isInstructionExpanded
                            ? 'Show less'
                            : 'Show more'}
                        </button>
                      `
                    )}
                  </div>
                </div>
              `
            )}
            ${when(
              this.action?.matched_files &&
                this.action.matched_files.length > 0,
              () => html`
                <div class="modal-row matched-files-row">
                  <gr-icon icon="folder"></gr-icon>
                  <div class="modal-row-content">
                    <div class="modal-row-text">
                      ${this.renderMatchedFiles()}
                    </div>
                  </div>
                </div>
              `
            )}
            ${when(
              this.action?.capability_definition_url,
              () => html`
                <div class="modal-row">
                  <gr-icon icon="link"></gr-icon>
                  <div class="modal-row-content">
                    <div class="modal-row-text">
                      <a
                        href=${ifDefined(
                          this.action?.capability_definition_url
                        )}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Capability Definition
                      </a>
                    </div>
                  </div>
                </div>
              `
            )}
          </div>
          <div class="modalActions">
            <gr-button
              id="closeButton"
              link=""
              primary=""
              @click=${() => this.detailsModal?.close()}
            >
              Close
            </gr-button>
          </div>
        </div>
      </dialog>
    `;
  }

  private handleAction() {
    const action = this.action;
    if (!action) return;

    const contextItems = (action.context_item_links ?? []).map(link =>
      parseLink(link, this.contextItemTypes)
    );
    if (action.external_contexts) {
      contextItems.push(...action.external_contexts);
    }

    if (contextItems.some(item => !item)) {
      fireAlert(this, 'Failed to parse one or more context item links.');
    }
    if (action.enable_send_without_input) {
      this.getChatModel().startNewChatWithPredefinedPrompt(
        action.id,
        contextItems.filter(isDefined)
      );
    } else {
      this.getChatModel().startNewChatWithUserInput(
        action.initial_user_prompt ?? '',
        action.id,
        contextItems.filter(isDefined),
        /* useCurrentContext */ false // we want to use the context from the action
      );
    }
  }

  private renderMatchedFiles() {
    const files = this.action?.matched_files || [];
    if (files.length === 0) return nothing;

    const showAll = this.isFilesExpanded || files.length <= 4;
    const filesToDisplay = showAll ? files : files.slice(0, 4);

    return html`
      <div class="modal-row-title">Matched files:</div>
      <div class="file-list ${this.isFilesExpanded ? 'expanded' : ''}">
        ${filesToDisplay.map(
          file => html`<div class="file-item">${file}</div>`
        )}
      </div>
      ${when(
        files.length > 4,
        () => html`
          <button
            class="expand-button"
            aria-expanded=${this.isFilesExpanded}
            @click=${() => (this.isFilesExpanded = !this.isFilesExpanded)}
          >
            ${this.isFilesExpanded ? 'Show less' : 'Show more'}
          </button>
        `
      )}
    `;
  }

  private async displayDetailsCard(event: MouseEvent) {
    event.stopPropagation();
    this.isInstructionExpanded = false;
    this.isFilesExpanded = false;
    this.detailsModal?.showModal();
    await this.updateComplete;
    const textEl = this.shadowRoot?.querySelector('.instruction-text');
    if (textEl) {
      this.showExpandButton =
        textEl.scrollHeight > SplashPageAction.COLLAPSED_HEIGHT;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'splash-page-action': SplashPageAction;
  }
}
