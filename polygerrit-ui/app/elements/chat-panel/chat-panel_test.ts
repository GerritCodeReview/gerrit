/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {ChatPanel} from './chat-panel';
import {
  chatModelToken,
  ChatPanelMode,
  Turn,
} from '../../models/chat/chat-model';
import {commentsModelToken} from '../../models/comments/comments-model';
import {changeModelToken} from '../../models/change/change-model';
import {filesModelToken} from '../../models/change/files-model';
import {customElement} from 'lit/decorators.js';
import {BehaviorSubject, of} from 'rxjs';
import {provide} from '../../models/dependency';
import sinon from 'sinon';
import {userModelToken} from '../../models/user/user-model';
import {AccountDetailInfo} from '../../types/common';
import {ModelInfo} from '../../api/ai-code-review';

const availableModels: ModelInfo[] = [
  {
    model_id: 'gemini-pro',
    short_text: 'Gemini Pro',
    full_display_text: 'Gemini Pro',
  },
  {
    model_id: 'gemini-ultra',
    short_text: 'Gemini Ultra',
    full_display_text: 'Gemini Ultra',
  },
];

const mockChatModel = {
  selectedModel$: new BehaviorSubject<ModelInfo | undefined>(
    availableModels[0]
  ),
  modelsLoadingError$: new BehaviorSubject<string | undefined>(undefined),
  turns$: new BehaviorSubject<Turn[]>([]),
  errorMessage$: new BehaviorSubject<string | undefined>(undefined),
  contextItemTypes$: new BehaviorSubject<any[]>([
    {id: 'file', name: 'File', icon: 'file_copy'},
  ]),
  userContextItems$: new BehaviorSubject<any[]>([]),
  addContextItem: sinon.stub(),
  removeContextItem: sinon.stub(),
  startNewChatWithPredefinedPrompt: sinon.stub(),
  startNewChatWithUserInput: sinon.stub(),
  models$: new BehaviorSubject({
    privacy_url: 'http://privacy.url',
    models: availableModels,
  }),
  selectedModelId$: of(availableModels[0].model_id),
  availableModelsMap$: of(new Map(availableModels.map(m => [m.model_id, m]))),
  actions$: new BehaviorSubject<any[]>([
    {
      id: 'test-action',
      display_text: 'Test Action',
      enable_splash_page_card: true,
    },
  ]),
  defaultActionId$: of(undefined),
  defaultAction$: of(undefined),
  nextTurnIndex$: new BehaviorSubject(0),
  conversations$: of([]),
  conversationId$: new BehaviorSubject(''),
  mode$: new BehaviorSubject(ChatPanelMode.CONVERSATION),
  userInput$: new BehaviorSubject(''),
  state$: of({} as any),
  contextItemToType: (item: any) => {
    if (!item) return undefined;
    return (
      mockChatModel.contextItemTypes$
        .getValue()
        .find(type => type.id === item.type_id) ??
      ({icon: `${item.type_id}-icon`} as any)
    );
  },
  updateUserInput: sinon.stub(),
};

const mockCommentsModel = {
  saveDraft: sinon.stub(),
};

const mockChangeModel = {changeNum$: new BehaviorSubject(1)};
const mockFilesModel = {files$: new BehaviorSubject([])};

const mockUserModel = {
  account$: new BehaviorSubject<AccountDetailInfo | undefined>(undefined),
};

@customElement('test-chat-panel')
class TestChatPanel extends ChatPanel {
  constructor() {
    super();
    provide(this, chatModelToken, () => mockChatModel as any);
    provide(this, commentsModelToken, () => mockCommentsModel as any);
    provide(this, changeModelToken, () => mockChangeModel as any);
    provide(this, filesModelToken, () => mockFilesModel as any);
    provide(this, userModelToken, () => mockUserModel as any);

    (this as any).getChatModel = () => mockChatModel;
  }
}

suite('chat-panel tests', () => {
  let element: TestChatPanel;

  setup(async () => {
    element = await fixture(html`<test-chat-panel></test-chat-panel>`);
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="chat-panel-container">
          <chat-header></chat-header>
          <splash-page></splash-page>
          <div class="prompt-section">
            <prompt-box></prompt-box>
            <div class="ai-policy">
              Review agent may display inaccurate info.
              <a href="http://privacy.url" target="_blank">
                AI privacy policy
              </a>
            </div>
          </div>
        </div>
      `
    );
  });

  test('renders history mode', async () => {
    mockChatModel.mode$.next(ChatPanelMode.HISTORY);
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="chat-panel-container">
          <chat-header></chat-header>
          <chat-history></chat-history>
        </div>
      `
    );
  });

  test('renders chat mode', async () => {
    mockChatModel.mode$.next(ChatPanelMode.CONVERSATION);

    mockChatModel.turns$.next([
      {
        userMessage: {
          content: 'hello',

          userType: 0, // UserType.USER

          contextItems: [],
        },

        geminiMessage: {
          responseParts: [],

          regenerationIndex: 0,

          references: [],

          citations: [],

          userType: 1, // UserType.GEMINI
        },
      },
    ] as any);

    await element.updateComplete;

    assert.shadowDom.equal(
      element,

      /* HTML */ `
        <div class="chat-panel-container">
          <chat-header></chat-header>

          <div class="messages-container" id="scrollableDiv">
            <user-message></user-message>

            <gemini-message
              class="latest"
              style="min-height: 0px"
            ></gemini-message>
          </div>

          <div class="prompt-section">
            <prompt-box></prompt-box>

            <div class="ai-policy">
              Review agent may display inaccurate info.

              <a href="http://privacy.url" target="_blank">
                AI privacy policy
              </a>
            </div>
          </div>
        </div>
      `
    );
  });

  test('renders privacy policy if url is present', async () => {
    mockChatModel.models$.next({privacy_url: 'http://privacy.url'} as any);
    await element.updateComplete;
    const policy = element.shadowRoot!.querySelector('.ai-policy');
    assert.isOk(policy);
    assert.include(
      policy!.textContent,
      'Review agent may display inaccurate info'
    );
    const link = policy!.querySelector('a');
    assert.isOk(link);
    assert.equal(link!.getAttribute('href'), 'http://privacy.url');
  });

  test('onUserInputChange updates model', async () => {
    element.onUserInputChange('test input');
    assert.isTrue(mockChatModel.updateUserInput.calledOnce);
    assert.equal(mockChatModel.updateUserInput.lastCall.args[0], 'test input');
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-chat-panel': TestChatPanel;
  }
}
