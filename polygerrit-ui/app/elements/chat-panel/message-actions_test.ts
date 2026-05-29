/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {
  ChatModel,
  chatModelToken,
  GeminiMessage,
  ResponsePartType,
  Turn,
  UniqueTurnId,
  UserType,
} from '../../models/chat/chat-model';
import './message-actions';
import {MessageActions} from './message-actions';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';

suite('message-actions tests', () => {
  let element: MessageActions;
  let chatModel: ChatModel;

  const turnId: UniqueTurnId = {turnIndex: 0, regenerationIndex: 0};

  function createTurn(text: string): Turn {
    return {
      userMessage: {
        userType: UserType.USER,
        content: 'test',
        contextItems: [],
      },
      geminiMessage: {
        userType: UserType.GEMINI,
        responseParts: [
          {
            id: 0,
            type: ResponsePartType.TEXT,
            content: text,
          },
        ],
        regenerationIndex: 0,
        references: [],
        citations: [],
      } as GeminiMessage,
    };
  }

  setup(async () => {
    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.pluginsModel.aiCodeReviewRegister({
      pluginName: 'test-plugin',
      provider: chatProvider,
    });

    const changeModel = testResolver(changeModelToken);
    changeModel.updateState({
      change: createChange() as ParsedChangeInfo,
    });

    chatModel = testResolver(chatModelToken);

    element = await fixture<MessageActions>(
      html`<message-actions
        .turnId=${turnId}
        .isLatest=${true}
      ></message-actions>`
    );
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [createTurn('test message')],
    });
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-copy-clipboard class="copy-button" hideinput="">
        </gr-copy-clipboard>
        <md-icon-button
          class="regenerate-button"
          data-aria-label="Regenerate response"
          title="Regenerate response"
          value=""
        >
          <md-icon aria-hidden="true">refresh</md-icon>
        </md-icon-button>
      `
    );
  });

  test('hides copy and regenerate when not latest', async () => {
    element.isLatest = false;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-copy-clipboard class="copy-button" hidden="" hideinput="">
        </gr-copy-clipboard>
        <md-icon-button
          class="regenerate-button"
          data-aria-label="Regenerate response"
          hidden=""
          title="Regenerate response"
          value=""
        >
          <md-icon aria-hidden="true">refresh</md-icon>
        </md-icon-button>
      `
    );
  });

  test('regenerate button calls model', async () => {
    const initialTurn = createTurn('test message');
    chatModel.updateState({...chatModel.getState(), turns: [initialTurn]});
    await element.updateComplete;

    const button = element.shadowRoot?.querySelector('.regenerate-button');
    assert.isOk(button);
    (button as HTMLElement).click();

    // Wait for the model update to propagate.
    await element.updateComplete;

    const turns = chatModel.getState().turns;
    assert.equal(turns.length, 1);
    assert.equal(turns[0].geminiMessage?.regenerationIndex, 1);
  });

  test('copy clipboard has correct text', async () => {
    const turn = createTurn('another message');
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    await element.updateComplete;
    const copy = element.shadowRoot?.querySelector('gr-copy-clipboard');
    assert.isOk(copy);
    assert.equal(copy?.text, 'another message');
  });
});
