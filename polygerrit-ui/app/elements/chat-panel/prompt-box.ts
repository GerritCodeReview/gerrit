/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/chips/assist-chip.js';
import '@material/web/chips/chip-set.js';
import './context-chip';
import './context-input-chip';

import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

import {
  ContextItem,
  ContextItemType,
  ModelInfo,
} from '../../api/ai-code-review';
import {chatModelToken, Turn} from '../../models/chat/chat-model';
import {
  contextItemEquals,
  searchForContextLinks,
} from '../../models/chat/context-item-util';
import {resolve} from '../../models/dependency';
import {debounce, DelayedTask} from '../../utils/async-util';
import {subscribe} from '../lit/subscription-controller';

const MAX_VISIBLE_CONTEXT_ITEMS_COLLAPSED = 3;
const MAX_VISIBLE_SUGGESTED_CONTEXT_ITEMS_COLLAPSED = 1;
const MAX_VISIBLE_INPUT_LINES = 5;

interface PromptBoxContextItem extends ContextItem {
  isProvisional?: boolean;
}

@customElement('prompt-box')
export class PromptBox extends LitElement {
  @query('#promptInput') promptInput?: HTMLTextAreaElement;

  @property({type: Boolean})
  isDisabled = false;

  @property({type: String})
  disabledMessage = 'Review Agent is disabled.';

  @state() hasModelLoadingError = false;

  @state() selectedModel?: ModelInfo;

  @state() userInput = '';

  @state() previousMessageIndex = -1;

  @state() turns: readonly Turn[] = [];

  @state() errorMessage?: string;

  @state() contextItems: readonly PromptBoxContextItem[] = [];

  @state() dynamicContextItemsSuggestions: PromptBoxContextItem[] = [];

  @state() showAllContextItems = false;

  @state() contextItemTypes: readonly ContextItemType[] = [];

  // TODO(milutin): Find out if we need this.
  // @ts-ignore
  private turnBasisForUserInput?: number;

  private lineHeight = 0;

  private maxInputHeight = 0;

  private updateDynamicContextItemsTask?: DelayedTask;

  private readonly getChatModel = resolve(this, chatModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().modelsLoadingError$,
      x => (this.hasModelLoadingError = !!x)
    );
    subscribe(
      this,
      () => this.getChatModel().turns$,
      x => {
        const newLength = x?.length ?? 0;
        if (this.turns.length !== newLength) {
          this.previousMessageIndex = newLength;
        }
        this.turns = x ?? [];
      }
    );
    subscribe(
      this,
      () => this.getChatModel().errorMessage$,
      x => (this.errorMessage = x)
    );
    subscribe(
      this,
      () => this.getChatModel().selectedModel$,
      x => (this.selectedModel = x)
    );
    subscribe(
      this,
      () => this.getChatModel().contextItemTypes$,
      x => (this.contextItemTypes = x)
    );
    subscribe(
      this,
      () => this.getChatModel().userContextItems$,
      x => (this.contextItems = x)
    );
  }

  protected get suggestedContextItems(): ContextItem[] {
    return this.dynamicContextItemsSuggestions.filter(item =>
      this.contextItems.every(
        existingItem => !contextItemEquals(item, existingItem)
      )
    );
  }

  private get nextTurnIndex() {
    return this.turns.length;
  }

  private get lastTurn() {
    if (this.turns.length === 0) {
      return undefined;
    }
    return this.turns[this.turns.length - 1];
  }

  get chatInputDisabledText() {
    if (this.hasModelLoadingError) {
      return 'Failed to load models. Please reload the page.';
    }
    if (!this.selectedModel) {
      return 'Loading models...';
    }
    const lastTurn = this.lastTurn;
    const geminiMessage = lastTurn?.geminiMessage;
    const isBackgroundRequest = lastTurn?.userMessage.isBackgroundRequest;
    if (
      !!this.errorMessage ||
      (geminiMessage !== undefined &&
        !geminiMessage.responseComplete &&
        !isBackgroundRequest)
    ) {
      return 'Thinking ...';
    }
    if (this.isDisabled) {
      return this.disabledMessage;
    }
    return '';
  }

  get chatInputDisabled() {
    return !!this.chatInputDisabledText;
  }

  private get previousUserMessages() {
    return this.turns.map(turn => turn.userMessage.content);
  }

  private get promptHasInput() {
    return !!this.userInput;
  }

  private get selectedPreviousMessage() {
    const previousUserMessages = this.previousUserMessages;
    return this.previousMessageIndex >= 0 &&
      this.previousMessageIndex < previousUserMessages.length
      ? previousUserMessages[this.previousMessageIndex]
      : '';
  }

  /**
   * Calculates the number of context items (both regular and suggested)
   * that are not visible when the context chip set is collapsed.
   */
  get numExcessContextItems() {
    const regularContextItems = this.contextItems;
    const suggestedContextItems = this.suggestedContextItems;
    return (
      Math.max(
        0,
        regularContextItems.length - MAX_VISIBLE_CONTEXT_ITEMS_COLLAPSED
      ) +
      Math.max(
        0,
        suggestedContextItems.length -
          MAX_VISIBLE_SUGGESTED_CONTEXT_ITEMS_COLLAPSED
      )
    );
  }

  get shouldShowContextToggle() {
    return this.numExcessContextItems > 0;
  }

  get contextToggleTooltip() {
    return this.showAllContextItems
      ? 'Collapse'
      : `${this.numExcessContextItems} additional items or suggestions`;
  }

  get contextToggleText() {
    return this.showAllContextItems ? '▲' : `+${this.numExcessContextItems}`;
  }

  static override styles = css`
    :host {
      background-color: var(--background-color-tertiary);
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      /* For high contrast mode. */
      outline: 1px solid transparent;
      padding: var(--spacing-l) var(--spacing-l) var(--spacing-m)
        var(--spacing-xl);
      transition: box-shadow 280ms cubic-bezier(0.4, 0, 0.2, 1);
    }
    :host(:focus-within) {
      background: var(
        --search-box-focus-bg-color,
        var(--background-color-primary)
      );
      box-shadow: 0px 1px 2px 0px var(--elevation-color),
        0px 2px 6px 2px var(--elevation-color);
    }
    .tab-chip-set md-assist-chip.tab-chip {
      --md-assist-chip-container-height: 16px;
      --md-assist-chip-label-text-size: 10px;
      --md-assist-chip-label-text-color: var(--primary-fg-color);
      --md-assist-chip-label-line-height: 16px;
      background-color: var(--tab-chip-bg-color);
      border-radius: 4px;
      margin: 0;
      padding: 0;
    }
    gr-icon,
    md-icon {
      font-size: 20px;
      line-height: 24px;
    }
    .prompt-box-inner-container {
      display: flex;
      height: auto;
      min-height: 32px;
      flex-direction: row;
      align-items: center;
      justify-content: space-between;
      padding-bottom: 8px;
    }
    .prompt-input-container {
      flex-grow: 1;
      height: inherit;
      margin-right: var(--spacing-xs);
      position: relative;
    }
    .prompt-input {
      background: transparent;
      color: var(--primary-text-color);
      font: inherit;
      font-size: 16px; /* $font-size-large */
      border: none;
      outline: none;
      resize: none;
      padding: 0;
      margin: 0;
      height: auto;
      width: 100%;
      max-width: 100%;
    }
    .prompt-input::placeholder {
      color: var(--deemphasized-text-color);
    }
    .context-chip-set {
      gap: 4px;
    }
    .context-chip-set md-assist-chip.context-toggle-chip {
      --md-assist-chip-container-height: 20px;
      --md-assist-chip-label-text-size: 12px;
      --md-assist-chip-label-text-weight: 500;
      --md-assist-chip-label-line-height: 16px;
      --md-assist-chip-label-text-color: var(--primary-default, purple);
      margin: 0;
      margin-left: auto;
      border-color: var(--border-color);
      background-color: var(--elevation-2);
      border-radius: 8px;
      display: flex;
      padding: 0 4px;
    }
  `;

  override render() {
    return html`
      <div class="prompt-box-inner-container">
        <div class="prompt-input-container">
          <textarea
            id="promptInput"
            rows="1"
            class="prompt-input"
            name="search"
            role="searchbox"
            autocomplete="off"
            spellcheck="false"
            aria-label="Ask Gemini"
            ?disabled=${this.chatInputDisabled}
            .placeholder=${this.placeHolderText()}
            .value=${this.userInput}
            @keydown=${this.onKeyDown}
            @input=${this.onInput}
            @scroll=${this.onScroll}
          ></textarea>
        </div>
        ${this.renderTabChip()}
      </div>
      ${this.renderAddContext()}
    `;
  }

  private renderTabChip() {
    return html` ${when(
      this.tabChipVisible(),
      () => html`
        <md-chip-set class="tab-chip-set">
          <md-assist-chip class="tab-chip" label="Tab"></md-assist-chip>
        </md-chip-set>
      `
    )}`;
  }

  private renderAddContext() {
    return html`
      <md-chip-set class="context-chip-set">
        <context-input-chip
          @context-item-added=${(e: CustomEvent<ContextItem>) =>
            this.onContextItemAdded(e.detail)}
        ></context-input-chip>
        ${(this.showAllContextItems
          ? this.contextItems
          : this.contextItems.slice(0, MAX_VISIBLE_CONTEXT_ITEMS_COLLAPSED)
        ).map(
          contextItem => html`
            <context-chip
              class="external-context"
              .text=${contextItem.title}
              .contextItem=${contextItem}
              .tooltip=${contextItem.tooltip}
              @remove-context-chip=${() => this.removeContextItem(contextItem)}
            ></context-chip>
          `
        )}
        ${(this.showAllContextItems
          ? this.suggestedContextItems
          : this.suggestedContextItems.slice(
              0,
              MAX_VISIBLE_SUGGESTED_CONTEXT_ITEMS_COLLAPSED
            )
        ).map(
          contextItem => html`
            <context-chip
              class="suggestion-context"
              .contextItem=${contextItem}
              .text=${contextItem.title}
              .isSuggestion=${true}
              .tooltip=${`Add this ${contextItem.title} as context`}
              @accept-context-item-suggestion=${() =>
                this.acceptContextItemSuggestion(contextItem)}
            ></context-chip>
          `
        )}
        ${when(
          this.shouldShowContextToggle,
          () => html`
            <md-assist-chip
              class="context-toggle-chip"
              @click=${this.toggleShowAllContext}
              .title=${this.contextToggleTooltip}
              .label=${this.contextToggleText}
            >
            </md-assist-chip>
          `
        )}
      </md-chip-set>
    `;
  }

  override firstUpdated() {
    this.lineHeight = this.promptInput?.offsetHeight ?? 0;
    this.maxInputHeight = this.lineHeight * MAX_VISIBLE_INPUT_LINES;
  }

  override updated(changedProperties: Map<string | number | symbol, unknown>) {
    if (changedProperties.has('userInput')) {
      if (!this.userInput) {
        this.turnBasisForUserInput = undefined;
        this.resetInputHeight();
      } else {
        this.adjustInputHeight();
      }
    }
    if (changedProperties.has('chatInputDisabled')) {
      if (!this.chatInputDisabled) {
        this.refocusPromptInput();
      }
    }
  }

  private onScroll() {
    this.scrollTop = this.promptInput?.scrollTop ?? 0;
    this.adjustInputHeight();
  }

  private onInput(e: Event) {
    this.userInput = (e.target as HTMLTextAreaElement).value;
    this.adjustInputHeight();
    this.updateDynamicContextItemsDebounced();
  }

  private placeHolderText() {
    if (this.chatInputDisabledText) {
      return this.chatInputDisabledText;
    }
    const selectedPreviousMessage = this.selectedPreviousMessage;
    if (selectedPreviousMessage) {
      return selectedPreviousMessage;
    }
    if (this.isTextInputFocused()) {
      return '';
    }
    return 'Enter a prompt here...';
  }

  private onContextItemAdded(contextItem: ContextItem) {
    this.addContextItem(contextItem);
  }

  private addContextItem(contextItem: ContextItem) {
    this.getChatModel().addContextItem(contextItem);
  }

  private removeContextItem(contextItem: ContextItem) {
    this.getChatModel().removeContextItem(contextItem);
  }

  private acceptContextItemSuggestion(contextItem: ContextItem) {
    this.addContextItem(contextItem);
    this.updateDynamicContextItems();
  }

  private tabChipVisible() {
    return (
      !this.chatInputDisabled &&
      !this.promptHasInput &&
      !!this.selectedPreviousMessage
    );
  }

  private onKeyDown(event: KeyboardEvent) {
    if (
      event.ctrlKey ||
      event.altKey ||
      event.metaKey ||
      (event.shiftKey && event.key === 'Enter')
    ) {
      return;
    }

    switch (event.key) {
      case 'Enter':
        this.onEnter(event);
        break;
      case 'ArrowUp':
      case 'ArrowDown':
        this.handleArrowKey(event);
        break;
      case 'Tab':
        this.onTab(event);
        break;
      default:
        break;
    }
  }

  private handleArrowKey(event: KeyboardEvent) {
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
    const previousMessageIndex = this.previousMessageIndex;
    const previousUserMessages = this.previousUserMessages;
    const index =
      event.key === 'ArrowUp'
        ? previousMessageIndex - 1
        : previousMessageIndex + 1;
    if (index < 0 || index > previousUserMessages.length) return;
    this.doKeyboardAction(event, () => {
      this.previousMessageIndex = index;
    });
  }

  private onTab(event: Event) {
    const previousMessageIndex = this.previousMessageIndex;
    const previousUserMessages = this.previousUserMessages;
    if (
      previousMessageIndex < 0 ||
      previousMessageIndex >= previousUserMessages.length
    ) {
      return;
    }
    this.doKeyboardAction(event, () => {
      this.userInput = previousUserMessages[previousMessageIndex];
      this.turnBasisForUserInput = previousMessageIndex;
    });
  }

  /** Shared handler for up/down/tab keyboard actions. */
  private doKeyboardAction(event: Event, action: () => void) {
    if (this.promptHasInput) return;
    event.preventDefault();
    event.stopPropagation();
    action();
    this.adjustInputHeight();
  }

  private onEnter(event: Event) {
    const userInput = this.userInput;
    if (!userInput.trim()) return;
    event.preventDefault();
    event.stopPropagation();
    this.dynamicContextItemsSuggestions = [];
    const lastTurn = this.lastTurn;
    const isLastTurnBackgroundRequest =
      lastTurn?.userMessage.isBackgroundRequest;
    const isLastTurnComplete = lastTurn?.geminiMessage.responseComplete;
    if (isLastTurnBackgroundRequest && !isLastTurnComplete) {
      // We don't want to block the user from typing and hitting enter in the
      // prompt box if the current background request is not yet complete.
      // Instead, we start a new conversation in this case.
      this.getChatModel().startNewChatWithUserInput(
        userInput,
        undefined,
        undefined,
        true
      );
    } else {
      this.getChatModel().chat(
        userInput,
        lastTurn?.userMessage.actionId,
        this.nextTurnIndex
        // TODO(milutin): Find out if we need this.
        // this.turnBasisForUserInput
      );
    }
    // Reset height after sending
    this.resetInputHeight();
  }

  private toggleShowAllContext() {
    this.showAllContextItems = !this.showAllContextItems;
  }

  private isTextInputFocused() {
    return document.activeElement === this.promptInput;
  }

  private adjustInputHeight() {
    if (!this.promptInput) {
      return;
    }
    // Reset height to auto to correctly calculate scrollHeight for shrinking
    this.promptInput.style.height = 'auto';
    const scrollHeight = this.promptInput.scrollHeight;
    this.promptInput.style.height = `${Math.min(
      scrollHeight,
      this.maxInputHeight
    )}px`;
  }

  private resetInputHeight() {
    if (this.promptInput) {
      this.promptInput.style.height = `${this.lineHeight}px`;
    }
  }

  private refocusPromptInput() {
    setTimeout(() => {
      this.promptInput?.focus();
    });
  }

  private updateDynamicContextItemsDebounced() {
    this.updateDynamicContextItemsTask = debounce(
      this.updateDynamicContextItemsTask,
      () => {
        this.updateDynamicContextItems();
      },
      200
    );
  }

  private updateDynamicContextItems() {
    const suggestedContextItems = searchForContextLinks(
      this.userInput,
      this.contextItemTypes
    ).map(item => {
      return {
        ...item,
        isProvisional: true,
      };
    });
    this.dynamicContextItemsSuggestions = suggestedContextItems;
    // include all suggested context items as materialized context items
    // by default
    suggestedContextItems.forEach(contextItem => {
      if (
        !this.contextItems.some(item => contextItemEquals(item, contextItem))
      ) {
        this.addContextItem(contextItem);
      }
    });
    // remove materialized context items from the store if they previously came
    // from a suggestion, if they no longer exist in the prompt input
    this.contextItems.forEach(contextItem => {
      if (
        !suggestedContextItems.some(item =>
          contextItemEquals(item, contextItem)
        ) &&
        contextItem.isProvisional
      ) {
        this.removeContextItem(contextItem);
      }
    });
  }
}

export interface ContextItemAddedEvent extends CustomEvent<ContextItem> {
  type: 'context-item-added';
}

declare global {
  interface HTMLElementEventMap {
    'context-item-added': ContextItemAddedEvent;
  }
  interface HTMLElementTagNameMap {
    'prompt-box': PromptBox;
  }
}
