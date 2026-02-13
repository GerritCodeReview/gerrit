/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './prompt-box';
import {PromptBox} from './prompt-box';
import {
  ChatModel,
  chatModelToken,
  ChatState,
  UserType,
} from '../../models/chat/chat-model';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {ChangeModel, changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';
import sinon from 'sinon';
import {ContextItem} from '../../api/ai-code-review';

suite('prompt-box tests', () => {
  let element: PromptBox;
  let chatModel: ChatModel;
  let changeModel: ChangeModel;

  setup(async () => {
    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.pluginsModel.aiCodeReviewRegister({
      pluginName: 'test-plugin',
      provider: chatProvider,
    });

    changeModel = testResolver(changeModelToken);
    changeModel.updateState({
      change: createChange() as ParsedChangeInfo,
    });

    chatModel = testResolver(chatModelToken);
    chatModel.updateState({
      turns: [],
      draftUserMessage: {
        contextItems: [],
        content: '',
        userType: UserType.USER,
      },
    });

    element = await fixture<PromptBox>(html`<prompt-box></prompt-box>`);
    await element.updateComplete;
  });

  test('renders', () => {
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
              placeholder="Enter a prompt here..."
              style="height: 20px;"
            ></textarea>
          </div>
        </div>
        <md-chip-set class="context-chip-set">
          <context-input-chip> </context-input-chip>
          <context-chip class="this-change-context"> </context-chip>
        </md-chip-set>
      `
    );
  });

  test('chatInputDisabledText when model loading error', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      modelsLoadingError: 'Error loading models',
    });
    await element.updateComplete;
    assert.equal(
      element.chatInputDisabledText,
      'Failed to load models. Please reload the page.'
    );
  });

  test('updates userInput on input', async () => {
    const promptInput = element.shadowRoot?.querySelector('#promptInput');
    assert.isOk(promptInput);
    (promptInput as HTMLTextAreaElement).value = 'test input';
    promptInput?.dispatchEvent(new Event('input'));
    await element.updateComplete;
    assert.equal(element.userInput, 'test input');
  });

  test('dispatches user-input-change event on input', async () => {
    let eventFired = false;
    let eventDetail = null;
    element.addEventListener('user-input-change', (e: Event) => {
      eventFired = true;
      eventDetail = (e as CustomEvent).detail;
    });

    const promptInput = element.shadowRoot?.querySelector('#promptInput');
    assert.isOk(promptInput);
    (promptInput as HTMLTextAreaElement).value = 'test input';
    promptInput?.dispatchEvent(new Event('input'));
    await element.updateComplete;

    assert.isTrue(eventFired);
    assert.deepEqual(eventDetail, {value: 'test input'});
  });

  test('sends message on Enter', async () => {
    const initialTurns = chatModel.getState().turns.length;
    const promptInput = element.shadowRoot?.querySelector('#promptInput');
    assert.isOk(promptInput);
    element.userInput = 'test input';
    await element.updateComplete;

    promptInput?.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'}));
    await element.updateComplete;

    const turns = chatModel.getState().turns;
    assert.equal(turns.length, initialTurns + 1);
    assert.equal(turns[turns.length - 1].userMessage.content, 'test input');
  });

  test('renders context items', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      draftUserMessage: {
        ...chatModel.getState().draftUserMessage,
        contextItems: [
          {type_id: 'file', title: 'test.ts', link: 'link1'},
          {type_id: 'file', title: 'test2.ts', link: 'link2'},
        ],
      },
    });
    await element.updateComplete;
    const contextChips = element.shadowRoot?.querySelectorAll(
      'context-chip.external-context'
    );
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

  test('accepts suggested context item', async () => {
    const addContextItemSpy = sinon.spy(chatModel, 'addContextItem');
    const suggestion: ContextItem = {
      type_id: 'file',
      title: 'suggested.ts',
      link: 'link3',
      identifier: 'id3',
    };
    element.dynamicContextItemsSuggestions = [suggestion];
    await element.updateComplete;

    const suggestedChip = element.shadowRoot?.querySelector(
      '.suggestion-context'
    );
    assert.isOk(suggestedChip);
    suggestedChip.dispatchEvent(new Event('accept-context-item-suggestion'));
    await element.updateComplete;

    assert.isTrue(addContextItemSpy.calledOnceWith(suggestion));
  });

  test('removes context item', async () => {
    const removeContextItemSpy = sinon.spy(chatModel, 'removeContextItem');
    const item: ContextItem = {
      type_id: 'file',
      title: 'test.ts',
      link: 'link1',
      identifier: 'id1',
    };
    chatModel.updateState({
      ...chatModel.getState(),
      draftUserMessage: {
        ...chatModel.getState().draftUserMessage,
        contextItems: [item],
      },
    });
    await element.updateComplete;

    const contextChip = element.shadowRoot?.querySelector(
      'context-chip.external-context'
    );
    assert.isOk(contextChip);
    contextChip.dispatchEvent(new Event('remove-context-chip'));
    await element.updateComplete;

    assert.isTrue(removeContextItemSpy.calledOnceWith(item));
  });

  test('shows context toggle when too many items', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      draftUserMessage: {
        ...chatModel.getState().draftUserMessage,
        contextItems: [
          {type_id: 'file', title: 'test.ts', link: 'link1'},
          {type_id: 'file', title: 'test2.ts', link: 'link2'},
          {type_id: 'file', title: 'test3.ts', link: 'link3'},
          {type_id: 'file', title: 'test4.ts', link: 'link4'},
        ],
      },
    });
    await element.updateComplete;
    const toggleChip = element.shadowRoot?.querySelector(
      '.context-toggle-chip'
    );
    assert.isOk(toggleChip);
  });

  test('toggles showAllContextItems', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      draftUserMessage: {
        ...chatModel.getState().draftUserMessage,
        contextItems: [
          {type_id: 'file', title: 'test.ts', link: 'link1'},
          {type_id: 'file', title: 'test2.ts', link: 'link2'},
          {type_id: 'file', title: 'test3.ts', link: 'link3'},
          {type_id: 'file', title: 'test4.ts', link: 'link4'},
        ],
      },
    });
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
    chatModel.updateState({
      ...chatModel.getState(),
      models: {
        models: [
          {
            model_id: 'gemini-pro',
            short_text: 'Gemini Pro',
            full_display_text: 'Gemini Pro',
          },
        ],
        default_model_id: 'gemini-pro',
      },
      turns: [
        {
          userMessage: {
            content: 'test',
            userType: 0,
            contextItems: [],
          },
          geminiMessage: {
            responseComplete: false,
            userType: 1,
            responseParts: [],
            regenerationIndex: 0,
            references: [],
            citations: [],
          },
        },
      ],
    } as Partial<ChatState>);
    await element.updateComplete;
    assert.equal(element.chatInputDisabledText, 'Thinking ...');
  });

  test('grows on input', async () => {
    const promptInput = element.shadowRoot?.querySelector('#promptInput');
    assert.isOk(promptInput);
    const textarea = promptInput as HTMLTextAreaElement;

    // Mock scrollHeight to simulate content growth
    Object.defineProperty(textarea, 'scrollHeight', {
      value: 50,
      configurable: true,
    });

    textarea.value = 'line 1\nline 2';
    textarea.dispatchEvent(new Event('input'));
    await element.updateComplete;

    assert.equal(textarea.style.height, '50px');

    // Mock scrollHeight to simulate further growth
    Object.defineProperty(textarea, 'scrollHeight', {
      value: 100,
      configurable: true,
    });

    textarea.value = 'line 1\nline 2\nline 3\nline 4';
    textarea.dispatchEvent(new Event('input'));
    await element.updateComplete;

    assert.equal(textarea.style.height, '100px');
  });
});
