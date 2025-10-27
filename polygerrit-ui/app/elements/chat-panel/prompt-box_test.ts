/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import './prompt-box';
import {PromptBox} from './prompt-box';
import {
  ChatModel,
  chatModelToken,
  ChatState,
} from '../../models/chat/chat-model';
import {BehaviorSubject, of} from 'rxjs';
import {ContextItemType, ModelInfo} from '../../api/ai-code-review';
import {provide} from '../../models/dependency';
import {customElement, state} from 'lit/decorators.js';
import {LitElement} from 'lit';
import {SinonSpy} from 'sinon';
import {navigationToken} from '../core/gr-navigation/gr-navigation';

const chatModel = {
  selectedModel$: new BehaviorSubject<ModelInfo | undefined>(undefined),
  modelsLoadingError$: new BehaviorSubject<string | undefined>(undefined),
  turns$: new BehaviorSubject<any[]>([]),
  errorMessage$: new BehaviorSubject<string | undefined>(undefined),
  contextItemTypes$: new BehaviorSubject<any[]>([]),
  userContextItems$: new BehaviorSubject<any[]>([]),
  addContextItem: (() => {}) as SinonSpy,
  removeContextItem: (() => {}) as SinonSpy,
  models$: of(undefined),
  selectedModelId$: of(undefined),
  availableModelsMap$: of(new Map()),
  actions$: of([]),
  defaultActionId$: of(undefined),
  defaultAction$: of(undefined),
  nextTurnIndex$: of(0),
  conversations$: of([]),
  conversationId$: of(undefined),
  mode$: of(undefined),
  userInput$: of(''),
  state$: of({} as ChatState),
  contextItemToType: (item: any) =>
    ({icon: `${item.type_id}-icon`} as ContextItemType),
  get a() {
    return this as unknown as ChatModel;
  },
} as unknown as ChatModel;

@customElement('prompt-box-test-wrapper')
class PromptBoxTestWrapper extends LitElement {
  @state()
  _chatModel = chatModel;

  constructor() {
    super();
    provide(this, chatModelToken, () => this._chatModel);
    provide(this, navigationToken, () => {
      return {
        setUrl: () => {},
        replaceUrl: () => {},
        finalize: () => {},
        blockNavigation: () => {},
        releaseNavigation: () => {},
      };
    });
  }

  override render() {
    return html`<prompt-box></prompt-box>`;
  }
}

suite('prompt-box tests', () => {
  let element: PromptBox;
  let wrapper: PromptBoxTestWrapper;

  setup(async () => {
    wrapper = await fixture<PromptBoxTestWrapper>(
      html`<prompt-box-test-wrapper></prompt-box-test-wrapper>`
    );
    element = wrapper.shadowRoot!.querySelector<PromptBox>('prompt-box')!;
    await element.updateComplete;
    (chatModel.selectedModel$ as BehaviorSubject<ModelInfo | undefined>).next(
      undefined
    );
    (chatModel.modelsLoadingError$ as BehaviorSubject<string | undefined>).next(
      undefined
    );
    (chatModel.turns$ as BehaviorSubject<any[]>).next([]);
    (chatModel.errorMessage$ as BehaviorSubject<string | undefined>).next(
      undefined
    );
    (chatModel.contextItemTypes$ as BehaviorSubject<any[]>).next([]);
    (chatModel.userContextItems$ as BehaviorSubject<any[]>).next([]);
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
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
              disabled=""
              placeholder="Loading models..."
              style="height: 18px;"
            ></textarea>
          </div>
        </div>
        <md-chip-set class="context-chip-set">
          <context-input-chip> </context-input-chip>
        </md-chip-set>
      `
    );
  });

  test('chatInputDisabledText when model loading error', async () => {
    (chatModel.modelsLoadingError$ as BehaviorSubject<string | undefined>).next(
      'Error loading models'
    );
    await element.updateComplete;
    assert.equal(
      element.chatInputDisabledText,
      'Failed to load models. Please reload the page.'
    );
  });

  test('chatInputDisabledText when model is selected', async () => {
    (chatModel.selectedModel$ as BehaviorSubject<ModelInfo | undefined>).next({
      model_id: 'gemini-pro',
      short_text: 'Gemini Pro',
      full_display_text: 'Gemini Pro',
    });
    await element.updateComplete;
    assert.equal(element.chatInputDisabledText, '');
  });

  test('updates userInput on input', async () => {
    const promptInput = element.shadowRoot?.querySelector('#promptInput');
    assert.isOk(promptInput);
    (promptInput as HTMLTextAreaElement).value = 'test input';
    promptInput?.dispatchEvent(new Event('input'));
    await element.updateComplete;
    assert.equal(element.userInput, 'test input');
  });

  test('renders context items', async () => {
    (chatModel.userContextItems$ as BehaviorSubject<any[]>).next([
      {type_id: 'file', title: 'test.ts', link: 'link1'},
      {type_id: 'file', title: 'test2.ts', link: 'link2'},
    ]);
    await element.updateComplete;
    const contextChips = element.shadowRoot?.querySelectorAll('context-chip');
    assert.isOk(contextChips);
    assert.equal(contextChips?.length, 2);
  });

  test('renders suggested context items', async () => {
    element.dynamicContextItemsSuggestions = [
      {type_id: 'file', title: 'suggested.ts', link: 'link3'},
    ];
    await element.updateComplete;
    const suggestedChips = element.shadowRoot?.querySelectorAll(
      '.suggestion-context'
    );
    assert.isOk(suggestedChips);
    assert.equal(suggestedChips?.length, 1);
  });

  test('shows context toggle when too many items', async () => {
    (chatModel.userContextItems$ as BehaviorSubject<any[]>).next([
      {type_id: 'file', title: 'test.ts', link: 'link1'},
      {type_id: 'file', title: 'test2.ts', link: 'link2'},
      {type_id: 'file', title: 'test3.ts', link: 'link3'},
      {type_id: 'file', title: 'test4.ts', link: 'link4'},
    ]);
    await element.updateComplete;
    const toggleChip = element.shadowRoot?.querySelector(
      '.context-toggle-chip'
    );
    assert.isOk(toggleChip);
  });

  test('toggles showAllContextItems', async () => {
    (chatModel.userContextItems$ as BehaviorSubject<any[]>).next([
      {type_id: 'file', title: 'test.ts', link: 'link1'},
      {type_id: 'file', title: 'test2.ts', link: 'link2'},
      {type_id: 'file', title: 'test3.ts', link: 'link3'},
      {type_id: 'file', title: 'test4.ts', link: 'link4'},
    ]);
    await element.updateComplete;
    assert.isFalse(element.showAllContextItems);
    const toggleChip = element.shadowRoot?.querySelector(
      '.context-toggle-chip'
    );
    (toggleChip as HTMLElement).click();
    await element.updateComplete;
    assert.isTrue(element.showAllContextItems);
  });

  test('chatInputDisabledText when message is processing', async () => {
    (chatModel.selectedModel$ as BehaviorSubject<ModelInfo | undefined>).next({
      model_id: 'gemini-pro',
      short_text: 'Gemini Pro',
      full_display_text: 'Gemini Pro',
    });
    (chatModel.turns$ as BehaviorSubject<any[]>).next([
      {
        userMessage: {content: 'test'},
        geminiMessage: {responseComplete: false},
      },
    ]);
    await element.updateComplete;
    assert.equal(element.chatInputDisabledText, 'Thinking ...');
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'prompt-box-test-wrapper': PromptBoxTestWrapper;
  }
}
