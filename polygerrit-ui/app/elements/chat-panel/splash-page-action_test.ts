/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './splash-page-action';
import {SplashPageAction} from './splash-page-action';
import {
  chatModelToken,
  ChatModel,
  ChatState,
} from '../../models/chat/chat-model';
import {BehaviorSubject, of} from 'rxjs';
import {Action} from '../../api/ai-code-review';
import {provide} from '../../models/dependency';
import {customElement, state} from 'lit/decorators.js';
import {LitElement} from 'lit';
import {SinonSpy} from 'sinon';
import sinon from 'sinon';
import {navigationToken} from '../core/gr-navigation/gr-navigation';

const chatModel = {
  selectedModel$: new BehaviorSubject<any | undefined>(undefined),
  modelsLoadingError$: new BehaviorSubject<string | undefined>(undefined),
  turns$: new BehaviorSubject<any[]>([]),
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
  actions$: of([]),
  defaultActionId$: of(undefined),
  defaultAction$: of(undefined),
  nextTurnIndex$: of(0),
  conversations$: of([]),
  conversationId$: of(undefined),
  mode$: of(undefined),
  userInput$: of(''),
  state$: of({} as ChatState),
  contextItemToType: (item: any) => ({icon: `${item.type_id}-icon`}),
  get a() {
    return this as unknown as ChatModel;
  },
} as unknown as ChatModel;

@customElement('splash-page-action-test-wrapper')
class SplashPageActionTestWrapper extends LitElement {
  @state()
  _chatModel = chatModel;

  constructor() {
    super();
    provide(this, chatModelToken, () => this._chatModel);
    provide(this, navigationToken, () => ({
      setUrl: () => {},
      replaceUrl: () => {},
      finalize: () => {},
      blockNavigation: () => {},
      releaseNavigation: () => {},
    }));
  }

  override render() {
    return html`<splash-page-action></splash-page-action>`;
  }
}

suite('splash-page-action tests', () => {
  let element: SplashPageAction;
  let wrapper: SplashPageActionTestWrapper;

  setup(async () => {
    wrapper = await fixture<SplashPageActionTestWrapper>(
      html`<splash-page-action-test-wrapper></splash-page-action-test-wrapper>`
    );
    element = wrapper.shadowRoot!.querySelector<SplashPageAction>(
      'splash-page-action'
    )!;
    await element.updateComplete;
  });

  test('renders with action', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      hover_text: 'Test hover',
      subtext: 'Test subtext',
      icon: 'test-icon',
    };
    element.action = action;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <md-assist-chip class="action-chip" title="Test hover">
          <div class="chip-content">
            <gr-icon class="action-icon" icon="test-icon"></gr-icon>
            <div class="action-text-container">
              <div class="main-action-text-container has-subtext">
                <span class="action-text">Test Action</span>
                <gr-tooltip-content has-tooltip="" title="Capability details">
                  <gr-button
                    aria-disabled="false"
                    class="info-button"
                    flatten=""
                    role="button"
                    tabindex="0"
                  >
                    <gr-icon icon="info"></gr-icon>
                  </gr-button>
                </gr-tooltip-content>
              </div>
              <span class="action-subtext">Test subtext</span>
            </div>
          </div>
        </md-assist-chip>
      `
    );
  });

  test('handles click', async () => {
    const startNewChatSpy = sinon.spy(chatModel, 'startNewChatWithUserInput');
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      initial_user_prompt: 'Initial prompt',
    };
    element.action = action;
    await element.updateComplete;

    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    assert.isOk(chip);
    chip!.click();

    assert.isTrue(startNewChatSpy.calledOnce);
    assert.isTrue(
      startNewChatSpy.calledWith('Initial prompt', 'test-action', [])
    );
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'splash-page-action-test-wrapper': SplashPageActionTestWrapper;
  }
}