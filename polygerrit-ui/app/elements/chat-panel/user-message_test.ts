/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {UserMessage} from './user-message';
import {
  UserMessage as UserMessageState,
  UserType,
  chatModelToken,
} from '../../models/chat/chat-model';
import {AccountDetailInfo} from '../../types/common';
import {ContextItem} from '../../api/ai-code-review';
import {customElement} from 'lit/decorators.js';
import {of} from 'rxjs';
import {provide} from '../../models/dependency';
import {MdFilterChip} from '@material/web/chips/filter-chip';

@customElement('test-user-message')
class TestUserMessage extends UserMessage {
  constructor() {
    super();
    const fakeUserModel = {
      account$: of({
        _account_id: 123,
        name: 'Test User',
        email: 'test@example.com',
      } as AccountDetailInfo),
    };
    (this as any).getUserModel = () => fakeUserModel;
  }
}

suite('user-message tests', () => {
  let element: TestUserMessage;

  const message: UserMessageState = {
    userType: UserType.USER,
    content: 'Hello, world!',
    contextItems: [],
  };

  setup(async () => {
    element = await fixture(
      html`<test-user-message .message=${message}></test-user-message>`
    );
    const fakeChatModel = {
      contextItemToType: () => ({icon: 'test-icon'}),
    };
    provide(element, chatModelToken, () => fakeChatModel as any);
    await element.updateComplete;
  });

  test('renders with content', async () => {
    const content = element.shadowRoot?.querySelector('.text-content');
    assert.equal(content?.textContent?.trim(), 'Hello, world!');
  });

  test('renders with account', async () => {
    const avatar = element.shadowRoot?.querySelector('gr-avatar');
    assert.isOk(avatar);
    assert.equal((avatar as any).account.name, 'Test User');
  });

  test('renders context items', async () => {
    const contextItems: ContextItem[] = [
      {type_id: 'file', title: 'file1.ts', link: 'link1'},
      {type_id: 'file', title: 'file2.ts', link: 'link2'},
    ];
    element.message = {...message, contextItems};
    await element.updateComplete;

    const chips = element.shadowRoot?.querySelectorAll('context-chip');
    assert.equal(chips?.length, 2);
    assert.equal((chips![0] as any).text, 'file1.ts');
    assert.equal((chips![1] as any).text, 'file2.ts');
  });

  test('toggles context items', async () => {
    const contextItems: ContextItem[] = [
      {type_id: 'file', title: 'file1.ts', link: 'link1'},
      {type_id: 'file', title: 'file2.ts', link: 'link2'},
      {type_id: 'file', title: 'file3.ts', link: 'link3'},
      {type_id: 'file', title: 'file4.ts', link: 'link4'},
    ];
    element.message = {...message, contextItems};
    await element.updateComplete;

    let chips = element.shadowRoot?.querySelectorAll('context-chip');
    assert.equal(chips?.length, 3);

    const toggleChip = element.shadowRoot?.querySelector(
      '.context-toggle-chip'
    ) as MdFilterChip;
    assert.isOk(toggleChip);
    assert.equal(toggleChip.label, '+1');

    toggleChip.click();
    await element.updateComplete;

    chips = element.shadowRoot?.querySelectorAll('context-chip');
    assert.equal(chips?.length, 4);
    assert.equal(toggleChip.label, '▲');
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-user-message': TestUserMessage;
  }
}
