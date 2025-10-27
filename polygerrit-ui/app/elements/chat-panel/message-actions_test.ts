/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {customElement} from 'lit/decorators.js';
import {BehaviorSubject} from 'rxjs';
import sinon from 'sinon';
import {
  GeminiMessage,
  ResponsePartType,
  Turn,
  UniqueTurnId,
  UserType,
} from '../../models/chat/chat-model';
import './message-actions';
import {MessageActions} from './message-actions';

function createFakeChatModel() {
  const turns$ = new BehaviorSubject<Turn[]>([]);
  const conversationId$ = new BehaviorSubject<string | undefined>(undefined);
  const regenerateMessage = sinon.stub();

  return {
    turns$,
    conversationId$,
    regenerateMessage,
    setTurns(turns: Turn[]) {
      turns$.next(turns);
    },
    setConversationId(id: string) {
      conversationId$.next(id);
    },
  };
}

type FakeChatModel = ReturnType<typeof createFakeChatModel>;

@customElement('test-message-actions')
class TestMessageActions extends MessageActions {
  public fakeChatModel: FakeChatModel;

  constructor() {
    super();
    this.fakeChatModel = createFakeChatModel();
    (this as any).getChatModel = () => this.fakeChatModel;
  }
}

suite('message-actions tests', () => {
  let element: TestMessageActions;
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
    element = await fixture(
      html`<test-message-actions
        .turnId=${turnId}
        .isLatest=${true}
      ></test-message-actions>`
    );
    element.fakeChatModel.setTurns([createTurn('test message')]);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-copy-clipboard> </gr-copy-clipboard>
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
        <gr-copy-clipboard hidden=""> </gr-copy-clipboard>
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
    const button = element.shadowRoot?.querySelector('.regenerate-button');
    assert.isOk(button);
    (button as HTMLElement).click();
    assert.isTrue(element.fakeChatModel.regenerateMessage.calledWith(turnId));
  });

  test('copy clipboard has correct text', async () => {
    const turn = createTurn('another message');
    element.fakeChatModel.setTurns([turn]);
    await element.updateComplete;
    const copy = element.shadowRoot?.querySelector('gr-copy-clipboard');
    assert.isOk(copy);
    assert.equal(copy?.text, 'another message');
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-message-actions': TestMessageActions;
  }
}
