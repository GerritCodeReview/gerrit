/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {customElement} from 'lit/decorators.js';
import {BehaviorSubject} from 'rxjs';
import {Models} from '../../api/ai-code-review';
import {GeminiMessage, Turn, UserType} from '../../models/chat/chat-model';
import './citations-box';
import {CitationsBox} from './citations-box';

const FAKE_CITATION_URL = 'http://google.com/citations';

function createFakeChatModel() {
  const turns$ = new BehaviorSubject<Turn[]>([]);
  const models$ = new BehaviorSubject<Models | undefined>(undefined);

  return {
    turns$,
    models$,
    setTurns(turns: Turn[]) {
      turns$.next(turns);
    },
    setModels(models: Models | undefined) {
      models$.next(models);
    },
  };
}

type FakeChatModel = ReturnType<typeof createFakeChatModel>;

@customElement('test-citations-box')
class TestCitationsBox extends CitationsBox {
  public fakeChatModel: FakeChatModel;

  constructor() {
    super();
    this.fakeChatModel = createFakeChatModel();
    (this as any).getChatModel = () => this.fakeChatModel;
  }
}

suite('citations-box tests', () => {
  let element: TestCitationsBox;

  function createTurn(citations: string[]): Turn {
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
        citations,
      } as GeminiMessage,
    };
  }

  setup(async () => {
    element = await fixture(html`<test-citations-box></test-citations-box>`);
    element.fakeChatModel.setModels({
      models: [],
      default_model_id: '',
      citation_url: FAKE_CITATION_URL,
    });
    await element.updateComplete;
  });

  test('renders nothing when no citations', async () => {
    element.fakeChatModel.setTurns([createTurn([])]);
    await element.updateComplete;

    assert.shadowDom.equal(element, '');
  });

  test('renders nothing when no citation_url', async () => {
    element.fakeChatModel.setModels(undefined);
    element.fakeChatModel.setTurns([createTurn(['http://example.com/1'])]);
    await element.updateComplete;

    assert.shadowDom.equal(element, '');
  });

  test('renders with one citation', async () => {
    const citations = ['http://example.com/1'];
    element.fakeChatModel.setTurns([createTurn(citations)]);
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="citations-display-box">
          <p class="citations-summary-message">
            Use
            <a
              href="http://google.com/citations"
              target="_blank"
              rel="noopener noreferrer"
            >
              with caution</a
            >
            . The model answer includes 1 citation
          from other sources:
          </p>
          <ul class="citation-entry-list">
            <li class="citation-item">
              <a
                href="http://example.com/1"
                target="_blank"
                rel="noopener noreferrer"
                >http://example.com/1</a
              >
            </li>
          </ul>
        </div>
      `
    );
  });

  test('renders with multiple citations', async () => {
    const citations = ['http://example.com/1', 'http://example.com/2'];
    element.fakeChatModel.setTurns([createTurn(citations)]);
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="citations-display-box">
          <p class="citations-summary-message">
            Use
            <a
              href="http://google.com/citations"
              target="_blank"
              rel="noopener noreferrer"
            >
              with caution</a
            >
            . The model answer includes 2 citations
          from other sources:
          </p>
          <ul class="citation-entry-list">
            <li class="citation-item">
              <a
                href="http://example.com/1"
                target="_blank"
                rel="noopener noreferrer"
                >http://example.com/1</a
              >
            </li>
            <li class="citation-item">
              <a
                href="http://example.com/2"
                target="_blank"
                rel="noopener noreferrer"
                >http://example.com/2</a
              >
            </li>
          </ul>
        </div>
      `
    );
  });

  test('renders citations for the correct turnIndex', async () => {
    const turn0 = createTurn(['http://example.com/0']);
    const turn1 = createTurn(['http://example.com/1', 'http://example.com/2']);
    element.fakeChatModel.setTurns([turn0, turn1]);
    element.turnIndex = 1;
    await element.updateComplete;

    const summary = element.shadowRoot?.querySelector(
      '.citations-summary-message'
    );
    assert.isOk(summary);
    assert.include(summary!.textContent, '2 citations');

    const items = element.shadowRoot?.querySelectorAll('.citation-item');
    assert.isOk(items);
    assert.equal(items!.length, 2);
    assert.equal(
      items![0].querySelector('a')?.href,
      'http://example.com/1'
    );
    assert.equal(
      items![1].querySelector('a')?.href,
      'http://example.com/2'
    );
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-citations-box': TestCitationsBox;
  }
}
