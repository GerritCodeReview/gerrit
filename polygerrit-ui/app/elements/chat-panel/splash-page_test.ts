/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './splash-page';
import {SplashPage} from './splash-page';
import {
  ChatModel,
  chatModelToken,
  ChatState,
  Turn,
} from '../../models/chat/chat-model';
import {BehaviorSubject, of} from 'rxjs';
import {Action} from '../../api/ai-code-review';
import {provide} from '../../models/dependency';
import {customElement, state} from 'lit/decorators.js';
import {LitElement} from 'lit';
import {SinonSpy} from 'sinon';
import {navigationToken} from '../core/gr-navigation/gr-navigation';
import {userModelToken} from '../../models/user/user-model';
import {AccountDetailInfo} from '../../types/common';

const chatModel = {
  selectedModel$: new BehaviorSubject<any | undefined>(undefined),
  modelsLoadingError$: new BehaviorSubject<string | undefined>(undefined),
  turns$: new BehaviorSubject<Turn[]>([]),
  errorMessage$: new BehaviorSubject<string | undefined>(undefined),
  contextItemTypes$: new BehaviorSubject<any[]>([]),
  userContextItems$: new BehaviorSubject<any[]>([]),
  addContextItem: (() => {}) as SinonSpy,
  removeContextItem: (() => {}) as SinonSpy,
  startNewChatWithPredefinedPrompt: (() => {}) as SinonSpy,
  startNewChatWithUserInput: (() => {}) as SinonSpy,
  models$: of(undefined),
  selectedModelId$: of(undefined),
  availableModelsMap$: of(new Map()),
  actions$: new BehaviorSubject<Action[]>([]),
  defaultActionId$: of(undefined),
  defaultAction$: of(undefined),
  nextTurnIndex$: of(0),
  conversations$: of([]),
  conversationId$: of(undefined),
  mode$: of(undefined),
  userInput$: of(''),
  state$: of({} as ChatState),
  contextItemToType: (item: any) => {
    return {icon: `${item.type_id}-icon`};
  },
  get a() {
    return this as unknown as ChatModel;
  },
} as unknown as ChatModel;

const userModel = {
  account$: new BehaviorSubject<AccountDetailInfo | undefined>(undefined),
};

@customElement('splash-page-test-wrapper')
class SplashPageTestWrapper extends LitElement {
  @state()
  _chatModel = chatModel;

  @state()
  _userModel = userModel;

  constructor() {
    super();
    provide(this, chatModelToken, () => this._chatModel);
    provide(this, userModelToken, () => this._userModel as any);
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
    return html`<splash-page></splash-page>`;
  }
}

suite('splash-page tests', () => {
  let element: SplashPage;
  let wrapper: SplashPageTestWrapper;

  setup(async () => {
    wrapper = await fixture<SplashPageTestWrapper>(
      html`<splash-page-test-wrapper></splash-page-test-wrapper>`
    );
    element = wrapper.shadowRoot!.querySelector<SplashPage>('splash-page')!;
    await element.updateComplete;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="splash-container">
          <h1 class="splash-greeting">Hello,</h1>
          <p class="splash-question">How can I help you today?</p>
          <div class="action-container-title suggested-actions-title">
            Capabilities
          </div>
          <md-chip-set class="action-container"> </md-chip-set>
        </div>
      `
    );
  });

  test('displays user name', async () => {
    userModel.account$.next({display_name: 'Test User'} as AccountDetailInfo);
    await element.updateComplete;
    const greeting = element.shadowRoot!.querySelector('.splash-greeting');
    assert.dom.equal(
      greeting,
      '<h1 class="splash-greeting">Hello, Test User</h1>'
    );
  });

  test('renders actions', async () => {
    const actions: Action[] = [
      {id: 'action1', display_text: 'Action 1', enable_splash_page_card: true},
      {id: 'action2', display_text: 'Action 2', enable_splash_page_card: true},
    ];
    (chatModel.actions$ as BehaviorSubject<Action[]>).next(actions);
    await element.updateComplete;
    const actionElements =
      element.shadowRoot!.querySelectorAll('splash-page-action');
    assert.lengthOf(actionElements, 2);
  });

  test('renders background request', async () => {
    const turns: Turn[] = [
      {
        userMessage: {
          userType: 0,
          content: 'Test background request',
          isBackgroundRequest: true,
          contextItems: [],
        },
        geminiMessage: {
          userType: 1,
          responseParts: [],
          regenerationIndex: 0,
          responseComplete: false,
          references: [],
          citations: [],
        },
      },
    ];
    (chatModel.turns$ as BehaviorSubject<Turn[]>).next(turns);
    await element.updateComplete;
    const backgroundRequestContainer = element.shadowRoot!.querySelector(
      '.background-request-container'
    );
    assert.isOk(backgroundRequestContainer);
  });

  test('toggles background request expansion', async () => {
    const turns: Turn[] = [
      {
        userMessage: {
          userType: 0,
          content: 'Test background request',
          isBackgroundRequest: true,
          contextItems: [],
        },
        geminiMessage: {
          userType: 1,
          responseParts: [],
          regenerationIndex: 0,
          responseComplete: false,
          references: [],
          citations: [],
        },
      },
    ];
    (chatModel.turns$ as BehaviorSubject<Turn[]>).next(turns);
    await element.updateComplete;

    const expansionButton = element.shadowRoot!.querySelector(
      '.info-panel-expansion-button'
    ) as HTMLElement;
    assert.isOk(expansionButton);

    const innerContainer = element.shadowRoot!.querySelector(
      '.background-request-container-inner'
    );
    assert.isFalse(innerContainer!.classList.contains('expanded'));

    expansionButton.click();
    await element.updateComplete;
    assert.isTrue(innerContainer!.classList.contains('expanded'));

    expansionButton.click();
    await element.updateComplete;
    assert.isFalse(innerContainer!.classList.contains('expanded'));
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'splash-page-test-wrapper': SplashPageTestWrapper;
  }
}
