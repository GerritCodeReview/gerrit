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
  TextPart,
  Turn,
  UserType,
} from '../../models/chat/chat-model';
import './gemini-message';
import {Reference} from '../../api/ai-code-review';
import {changeModelToken} from '../../models/change/change-model';
import {GeminiMessage} from './gemini-message';
import {commentsModelToken} from '../../models/comments/comments-model';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {ParsedChangeInfo} from '../../types/types';

import {AiAgentEventDetails, Interaction} from '../../constants/reporting';
import {getAppContext} from '../../services/app-context';
import {KnownExperimentId} from '../../services/flags/flags';

suite('gemini-message tests', () => {
  let element: GeminiMessage;
  let chatModel: ChatModel;

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

  const RESPONSE_TEXT: TextPart = {
    id: 0,
    type: ResponsePartType.TEXT,
    content: 'test message',
  };
  const RESPONSE_CREATE_COMMENT: CreateCommentPart = {
    id: 1,
    type: ResponsePartType.CREATE_COMMENT,
    content: 'test comment',
    commentCreationId: 'test-id',
    comment: {
      message: 'test comment',
      path: '/test/path',
    },
  };

  test('renders thinking', async () => {
    const turn = createTurn({responseComplete: false});
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="user-info">
          <gr-icon class="gemini-icon" custom="" icon="ai" title=""></gr-icon>
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
          <gr-icon class="gemini-icon" custom="" icon="ai" title=""></gr-icon>
        </div>
        <p class="text-content">The server did not return any response.</p>
      `
    );
  });

  test('renders text response', async () => {
    const turn = createTurn({
      responseComplete: true,
      responseParts: [RESPONSE_TEXT],
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
    assert.include(error?.textContent, 'Server error');

    const retryButton = element.shadowRoot?.querySelector(
      'gr-button'
    ) as HTMLElement;
    assert.isOk(retryButton);
    assert.equal(retryButton.textContent, 'Retry');

    const spy = sinon.spy(chatModel, 'regenerateMessage');
    retryButton.click();
    assert.isTrue(spy.calledOnce);
    assert.deepEqual(spy.firstCall.args[0], {
      turnIndex: 0,
      regenerationIndex: 0,
    });
  });

  test('renders suggested comment link', async () => {
    sinon
      .stub(getAppContext().flagsService, 'isEnabled')
      .withArgs(
        KnownExperimentId.ENABLE_AI_COMMENTS || 'UiFeature__enable_ai_comments'
      )
      .returns(true);

    const comment: CreateCommentPart = {
      id: 1,
      type: ResponsePartType.CREATE_COMMENT,
      content: 'test comment',
      commentCreationId: 'test-id',
      comment: {
        message: 'test comment',
        path: 'test/path',
        range: {
          start_line: 1,
          end_line: 1,
          start_character: 0,
          end_character: 0,
        },
      },
    };
    const turn = createTurn({
      responseComplete: true,
      responseParts: [comment],
    });
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    element.currentClNumber = 123 as any;
    element.repo = 'test-repo' as any;
    element.latestPatchNum = 2 as any;
    await element.updateComplete;

    const commentPath = element.shadowRoot?.querySelector('.comment-path');
    assert.isOk(commentPath);
    assert.equal(commentPath?.textContent?.trim(), 'test/path');
    assert.equal(
      commentPath?.getAttribute('href'),
      '/c/test-repo/+/123/2/test/path#1'
    );

    const commentLine = element.shadowRoot?.querySelector('.comment-line');
    assert.isOk(commentLine);
    assert.equal(commentLine?.textContent?.trim(), 'Line #1');
    assert.equal(
      commentLine?.getAttribute('href'),
      '/c/test-repo/+/123/2/test/path#1'
    );
  });

  test('renders suggested comment details when flag is disabled', async () => {
    sinon
      .stub(getAppContext().flagsService, 'isEnabled')
      .withArgs(
        KnownExperimentId.ENABLE_AI_COMMENTS || 'UiFeature__enable_ai_comments'
      )
      .returns(false);

    const comment: CreateCommentPart = {
      id: 1,
      type: ResponsePartType.CREATE_COMMENT,
      content: 'test comment',
      commentCreationId: 'test-id',
      comment: {
        message: 'test comment message',
        path: 'test/path',
        range: {
          start_line: 1,
          end_line: 1,
          start_character: 0,
          end_character: 0,
        },
      },
    };
    const turn = createTurn({
      responseComplete: true,
      responseParts: [comment],
    });
    chatModel.updateState({...chatModel.getState(), turns: [turn]});
    element.currentClNumber = 123 as any;
    element.repo = 'test-repo' as any;
    element.latestPatchNum = 2 as any;
    await element.updateComplete;

    const commentPath = element.shadowRoot?.querySelector('.comment-path');
    assert.isOk(commentPath);
    assert.equal(commentPath?.textContent?.trim(), 'test/path');

    const commentLine = element.shadowRoot?.querySelector('.comment-line');
    assert.isOk(commentLine);
    assert.equal(commentLine?.textContent?.trim(), '#1'); // Restored logic renders ${displayLine} which is #1.

    const suggestedComment =
      element.shadowRoot?.querySelector('.suggested-comment');
    assert.isOk(suggestedComment);

    const message = suggestedComment?.querySelector(
      '.suggested-comment-message gr-formatted-text'
    );
    assert.isOk(message);
    assert.equal((message as any).content, 'test comment message');

    const addButton = suggestedComment?.querySelector(
      'gr-button.add-as-comment-button'
    );
    assert.isOk(addButton);
    assert.equal(addButton?.textContent?.trim(), 'Add as Comment');

    // Test clicking Add as Comment
    const saveDraftStub = sinon.stub(
      testResolver(commentsModelToken),
      'saveDraft'
    );
    const reloadStub = sinon.stub(
      testResolver(commentsModelToken),
      'reloadAllComments'
    );

    (addButton as HTMLElement).click();

    assert.isTrue(saveDraftStub.calledOnce);
    assert.isTrue(reloadStub.calledOnce);
  });

  test('renders citations', async () => {
    const turn = createTurn({
      responseComplete: true,
      responseParts: [RESPONSE_TEXT],
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
      responseParts: [RESPONSE_TEXT],
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

  test('reports AI_AGENT_SUGGESTIONS_SHOWN interaction', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      id: 'test-conversation-id',
      selectedModelId: 'gemini-model-id',
    });

    const reportStub = sinon.stub(
      getAppContext().reportingService,
      'reportInteraction'
    );

    const turn = createTurn({
      responseComplete: true,
      responseParts: [RESPONSE_TEXT, RESPONSE_CREATE_COMMENT],
    });
    const updatedTurn = {
      ...turn,
      userMessage: {...turn.userMessage, actionId: 'custom-agent-id'},
    };

    chatModel.updateState({...chatModel.getState(), turns: [updatedTurn]});
    await element.updateComplete;

    assert.isTrue(reportStub.calledOnce);
    assert.equal(
      reportStub.firstCall.args[0],
      Interaction.AI_AGENT_SUGGESTIONS_SHOWN
    );
    const details = reportStub.firstCall.args[1] as AiAgentEventDetails;
    assert.equal(details.conversationId, 'test-conversation-id');
    assert.equal(details.agentId, 'custom-agent-id');
    assert.equal(details.commentCount, 1);
  });
});
