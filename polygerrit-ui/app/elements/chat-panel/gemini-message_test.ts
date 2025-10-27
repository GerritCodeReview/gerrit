/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {BehaviorSubject} from 'rxjs';
import sinon from 'sinon';
import {
  CreateCommentPart,
  GeminiMessage as GeminiMessageModel,
  ResponsePartType,
  Turn,
  UserType,
  chatModelToken,
} from '../../models/chat/chat-model';
import './gemini-message';
import {Reference} from '../../api/ai-code-review';
import {commentsModelToken} from '../../models/comments/comments-model';
import {changeModelToken} from '../../models/change/change-model';
import {filesModelToken} from '../../models/change/files-model';
import {GeminiMessage} from './gemini-message';
import {provide} from '../../models/dependency';
import {customElement} from 'lit/decorators.js';
import {LitElement} from 'lit';

// Fake models needed by gemini-message and its children
const fakeChatModel = {
  turns$: new BehaviorSubject<Turn[]>([]),
  models$: new BehaviorSubject<any>({
    citation_url: 'http://google.com/citations',
  }),
  conversationId$: new BehaviorSubject<string | undefined>(undefined),
  contextItemTypes$: new BehaviorSubject<any[]>([]),
};
const fakeCommentsModel = {
  saveDraft: sinon.stub(),
};
const fakeChangeModel = {changeNum$: new BehaviorSubject(1)};
const fakeFilesModel = {files$: new BehaviorSubject([])};

@customElement('test-wrapper')
class TestWrapper extends LitElement {
  constructor() {
    super();
    provide(this, chatModelToken, () => fakeChatModel as any);
    provide(this, commentsModelToken, () => fakeCommentsModel as any);
    provide(this, changeModelToken, () => fakeChangeModel as any);
    provide(this, filesModelToken, () => fakeFilesModel as any);
  }

  override render() {
    return html`<gemini-message .turnIndex=${0}></gemini-message>`;
  }
}

suite('gemini-message tests', () => {
  let element: GeminiMessage;
  let wrapper: TestWrapper;

  setup(async () => {
    // Reset fake models between tests
    fakeChatModel.turns$.next([]);
    fakeCommentsModel.saveDraft.resetHistory();

    wrapper = await fixture<TestWrapper>(html`<test-wrapper></test-wrapper>`);
    element = document.createElement('gemini-message');
    element.turnIndex = 0;
    element.isLatest = true;
    element.isBackgroundRequest = false;
    wrapper.appendChild(element);
    await element.updateComplete;
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
    fakeChatModel.turns$.next([turn]);
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
    fakeChatModel.turns$.next([turn]);
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
    fakeChatModel.turns$.next([turn]);
    await element.updateComplete;

    const formattedText =
      element.shadowRoot?.querySelector('gr-formatted-text');
    assert.isOk(formattedText);
    assert.equal(formattedText?.content, 'test message');
  });

  test('renders error', async () => {
    const turn = createTurn({errorMessage: 'test error'});
    fakeChatModel.turns$.next([turn]);
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
    fakeChatModel.turns$.next([turn]);
    await element.updateComplete;

    const commentContainer = element.shadowRoot?.querySelector(
      '.suggested-comment-container'
    );
    assert.isOk(commentContainer);

    const button = commentContainer?.querySelector('md-filled-button');
    assert.isOk(button);
    (button as HTMLElement)!.click();

    assert.isTrue(fakeCommentsModel.saveDraft.called);
    const draft = fakeCommentsModel.saveDraft.lastCall.args[0];
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
    fakeChatModel.turns$.next([turn]);
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
    fakeChatModel.turns$.next([turn]);
    await element.updateComplete;

    const referencesDropdown = element.shadowRoot?.querySelector(
      'references-dropdown'
    );
    assert.isOk(referencesDropdown);
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-wrapper': TestWrapper;
  }
}
