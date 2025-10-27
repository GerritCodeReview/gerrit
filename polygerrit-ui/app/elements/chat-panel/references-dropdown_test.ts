/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './references-dropdown';
import {ReferencesDropdown} from './references-dropdown';
import {
  ChatModel,
  chatModelToken,
  ChatState,
  Turn,
  UserType,
} from '../../models/chat/chat-model';
import {BehaviorSubject, of} from 'rxjs';
import {Reference} from '../../api/ai-code-review';
import {provide} from '../../models/dependency';
import {customElement, state} from 'lit/decorators.js';
import {LitElement} from 'lit';
import {SinonSpy} from 'sinon';
import {navigationToken} from '../core/gr-navigation/gr-navigation';

const chatModel = {
  selectedModel$: new BehaviorSubject<any | undefined>(undefined),
  modelsLoadingError$: new BehaviorSubject<string | undefined>(undefined),
  turns$: new BehaviorSubject<Turn[]>([]),
  errorMessage$: new BehaviorSubject<string | undefined>(undefined),
  contextItemTypes$: new BehaviorSubject<any[]>([]),
  userContextItems$: new BehaviorSubject<any[]>([]),
  addContextItem: (() => {}) as SinonSpy,
  removeContextItem: (() => {}) as SinonSpy,
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
  contextItemToType: (item: any) => {
    return {icon: `${item.type_id}-icon`};
  },
  get a() {
    return this as unknown as ChatModel;
  },
} as unknown as ChatModel;

@customElement('references-dropdown-test-wrapper')
class ReferencesDropdownTestWrapper extends LitElement {
  @state()
  _chatModel = chatModel;

  constructor() {
    super();
    provide(this, chatModelToken, () => this._chatModel);
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
    return html`<references-dropdown></references-dropdown>`;
  }
}

suite('references-dropdown tests', () => {
  let element: ReferencesDropdown;
  let wrapper: ReferencesDropdownTestWrapper;

  setup(async () => {
    wrapper = await fixture<ReferencesDropdownTestWrapper>(
      html`<references-dropdown-test-wrapper></references-dropdown-test-wrapper>`
    );
    element = wrapper.shadowRoot!.querySelector<ReferencesDropdown>(
      'references-dropdown'
    )!;
    await element.updateComplete;
    (chatModel.turns$ as BehaviorSubject<Turn[]>).next([]);
  });

  test('render', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(element, /* HTML */ '');
  });

  test('renders references', async () => {
    const references: Reference[] = [
      {
        type: 'FILE',
        displayText: 'file1.txt',
        externalUrl: 'http://example.com/file1',
      },
      {
        type: 'FILE',
        displayText: 'file2.txt',
        externalUrl: 'http://example.com/file2',
      },
    ];
    (chatModel.turns$ as BehaviorSubject<Turn[]>).next([
      {
        userMessage: {
          userType: UserType.USER,
          content: 'test',
          contextItems: [],
        },
        geminiMessage: {
          userType: UserType.GEMINI,
          responseParts: [],
          references,
          regenerationIndex: 0,
          citations: [],
        },
      },
    ]);
    await element.updateComplete;

    const button = element.shadowRoot?.querySelector(
      '.references-dropdown-button'
    );
    assert.isOk(button);
    (button as HTMLElement).click();
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="references-dropdown-container">
          <md-text-button class="references-dropdown-button" value="">
            <md-icon aria-hidden="true">expand_less</md-icon>
            Context used (2)
          </md-text-button>
        </div>
        <div class="references-dropdown-content">
          <div class="button-outer-wrapper">
            <a
              class="reference-button"
              href="http://example.com/file1"
              target="_blank"
              title=""
            >
              <div class="reference-wrapper">
                <span class="display-text">file1.txt</span>
              </div>
            </a>
          </div>
          <br />
          <div class="button-outer-wrapper">
            <a
              class="reference-button"
              href="http://example.com/file2"
              target="_blank"
              title=""
            >
              <div class="reference-wrapper">
                <span class="display-text">file2.txt</span>
              </div>
            </a>
          </div>
          <br />
        </div>
      `
    );
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'references-dropdown-test-wrapper': ReferencesDropdownTestWrapper;
  }
}
