/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import sinon from 'sinon';
import {
  ChatModel,
  chatModelToken,
  CreateCommentPart,
  GeminiMessage as GeminiMessageModel,
  ResponsePartType,
  Turn,
  UserType,
} from '../../models/chat/chat-model';
import './gemini-message';
import {Reference} from '../../api/ai-code-review';
import {commentsModelToken} from '../../models/comments/comments-model';
import {changeModelToken} from '../../models/change/change-model';
import {GeminiMessage} from './gemini-message';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {ParsedChangeInfo} from '../../types/types';
import {CommentsModel} from '../../models/comments/comments-model';

suite('gemini-message tests', () => {
  let element: GeminiMessage;
  let chatModel: ChatModel;
  let commentsModel: CommentsModel;
  let saveDraftStub: sinon.SinonStub;

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
    commentsModel = testResolver(commentsModelToken);
    saveDraftStub = sinon.stub(commentsModel, 'saveDraft');

    element = await fixture<GeminiMessage>(
      html`<gemini-message .turnIndex=${0}></gemini-message>`
    );
  });

  function createTurn(message: Partial<GeminiMessageModel>): Turn {
    return {
      userMessage: {
        userType: UserType.USER,
        content: 'test',
        contextItems: [],
      },
      geminiMessage: {
        userType: UserType.GEMINI,
        responseParts: [],
        regenerationIndex: 0,
        references: [],
        citations: [],
        ...message,
      },
    };
  }

  test('renders thinking', async () => {
    const turn = createTurn({responseComplete: false});
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="user-info">
          <gr-icon class="gemini-icon" icon="star_shine" title=""></gr-icon>
        </div>
        <div class="thinking-indicator">
          <p class="text-content">Thinking ...</p>
          <md-circular-progress
            class="thinking-spinner"
            indeterminate=""
            size="small"
          ></md-circular-progress>
        </div>
      `
    );
  });

  test('renders empty response', async () => {
    const turn = createTurn({responseComplete: true});
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="user-info">
          <gr-icon class="gemini-icon" icon="star_shine" title=""></gr-icon>
        </div>
        <p class="text-content">The server did not return any response.</p>
      `
    );
  });

  test('renders text response', async () => {
    const turn = createTurn({
      responseComplete: true,
      responseParts: [
        {id: 0, type: ResponsePartType.TEXT, content: 'test message'},
      ],
    });
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    await element.updateComplete;

    const formattedText =
      element.shadowRoot?.querySelector('gr-formatted-text');
    assert.isOk(formattedText);
    assert.equal(formattedText?.content, 'test message');
  });

  test('renders error', async () => {
    const turn = createTurn({errorMessage: 'test error'});
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    await element.updateComplete;

    const error = element.shadowRoot?.querySelector('.server-error');
    assert.isOk(error);
    assert.equal(error?.textContent, 'Server issue.');
  });

  test('renders suggested comment', async () => {
    const comment: CreateCommentPart = {
      id: 1,
      type: ResponsePartType.CREATE_COMMENT,
      content: 'test comment',
      commentCreationId: 'test-id',
      comment: {
        message: 'test comment',
        path: '/test/path',
      },
    };
    const turn = createTurn({
      responseComplete: true,
      responseParts: [comment],
    });
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    await element.updateComplete;

    const commentContainer = element.shadowRoot?.querySelector(
      '.suggested-comment-container'
    );
    assert.isOk(commentContainer);

    const button = commentContainer?.querySelector('md-filled-button');
    assert.isOk(button);
    (button as HTMLElement).click();

    assert.isTrue(saveDraftStub.called);
    const draft = saveDraftStub.lastCall.args[0];
    assert.equal(draft.message, 'test comment');
  });

  test('renders citations', async () => {
    const turn = createTurn({
      responseComplete: true,
      responseParts: [
        {id: 0, type: ResponsePartType.TEXT, content: 'test message'},
      ],
      citations: ['http://example.com'],
    });
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    element.isLatest = true;
    await element.updateComplete;

    const citationsBox = element.shadowRoot?.querySelector('citations-box');
    assert.isOk(citationsBox);
  });

  test('renders references', async () => {
    const references: Reference[] = [
      {
        type: 'test',
        displayText: 'test',
        externalUrl: 'http://example.com',
      },
    ];
    const turn = createTurn({
      responseComplete: true,
      responseParts: [
        {id: 0, type: ResponsePartType.TEXT, content: 'test message'},
      ],
      references,
    });
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    element.isLatest = true;
    await element.updateComplete;

    const referencesDropdown = element.shadowRoot?.querySelector(
      'references-dropdown'
    );
    assert.isOk(referencesDropdown);
  });
});
