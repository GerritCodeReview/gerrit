/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import '../shared/gr-avatar/gr-avatar';
import './user-message';
import {assert, fixture, html} from '@open-wc/testing';
import {UserMessage} from './user-message';
import {
  ChatModel,
  chatModelToken,
  UserMessage as UserMessageState,
  UserType,
} from '../../models/chat/chat-model';
import {ContextItem} from '../../api/ai-code-review';
import {MdFilterChip} from '@material/web/chips/filter-chip';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {
  chatContextItemTypes,
  chatProvider,
  createAccountDetailWithIdNameAndEmail,
  createChange,
} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';
import {userModelToken} from '../../models/user/user-model';
import {GrAvatar} from '../shared/gr-avatar/gr-avatar';

suite('user-message tests', () => {
  let element: UserMessage;
  let chatModel: ChatModel;

  const message: UserMessageState = {
    userType: UserType.USER,
    content: 'Hello, world!',
    contextItems: [],
  };

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

    // Set context items for chatModel before initial render
    chatModel.updateState({
      ...chatModel.getState(),
      contextItemTypes: chatContextItemTypes,
    });

    element = await fixture(
      html`<user-message .message=${message}></user-message>`
    );
    await element.updateComplete;
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="user-info"><gr-avatar hidden=""></gr-avatar></div>
        <div class="user-input-container">
          <p class="text-content">Hello, world!</p>
          <div class="context-chip-set"></div>
        </div>
      `
    );
  });

  test('renders with content', async () => {
    const content = element.shadowRoot?.querySelector('.text-content');
    assert.equal(content?.textContent?.trim(), 'Hello, world!');
  });

  test('renders with account', async () => {
    const userModel = testResolver(userModelToken);
    userModel.updateState({
      account: createAccountDetailWithIdNameAndEmail(123),
    });
    await element.updateComplete;

    const avatar = element.shadowRoot?.querySelector('gr-avatar') as GrAvatar;
    assert.isOk(avatar);
    assert.isOk(avatar.account);
    assert.equal(avatar.account?.name, 'User-123');
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
