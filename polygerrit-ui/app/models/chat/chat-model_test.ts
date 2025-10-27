/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {ChatModel} from './chat-model';
import {PluginsModel} from '../plugins/plugins-model';
import {ChangeModel} from '../change/change-model';
import {FilesModel} from '../change/files-model';
import {BehaviorSubject} from 'rxjs';
import {createParsedChange} from '../../test/test-data-generators';
import {AiCodeReviewProvider} from '../../api/ai-code-review';
import sinon from 'sinon';
import {ParsedChangeInfo} from '../../types/types';

suite('chat-model tests', () => {
  let model: ChatModel;
  let pluginsModel: PluginsModel;
  let changeModel: ChangeModel;
  let filesModel: FilesModel;
  let provider: AiCodeReviewProvider;

  setup(() => {
    pluginsModel = new PluginsModel();
    changeModel = {
      change$: new BehaviorSubject(undefined),
    } as unknown as ChangeModel;
    changeModel.updateStateChange = (change?: ParsedChangeInfo) => {
      (
        changeModel.change$ as BehaviorSubject<ParsedChangeInfo | undefined>
      ).next(change);
    };

    filesModel = {
      files$: new BehaviorSubject([]),
    } as unknown as FilesModel;
    provider = {
      chat: sinon.stub(),
      listChatConversations: sinon.stub().resolves([]),
      getChatConversation: sinon.stub().resolves([]),
      getModels: sinon.stub().resolves({models: [], default_model_id: ''}),
      getActions: sinon.stub().resolves({actions: [], default_action_id: ''}),
      getContextItemTypes: sinon.stub().resolves([]),
    };
    sinon
      .stub(pluginsModel, 'aiCodeReviewPlugins$')
      .get(() => new BehaviorSubject([{pluginName: 'test-plugin', provider}]));

    model = new ChatModel(pluginsModel, changeModel, filesModel);
  });

  test('initial state', () => {
    const state = model.getState();
    assert.isObject(state);
    assert.isEmpty(state.turns);
  });

  test('change subscription triggers API calls', () => {
    changeModel.updateStateChange(createParsedChange());
    assert.isTrue((provider.getModels as sinon.SinonStub).called);
    assert.isTrue((provider.getActions as sinon.SinonStub).called);
    assert.isTrue((provider.getContextItemTypes as sinon.SinonStub).called);
    assert.isTrue((provider.listChatConversations as sinon.SinonStub).called);
  });

  test('updateUserInput', () => {
    model.updateUserInput('test input');
    const state = model.getState();
    assert.equal(state.draftUserMessage.content, 'test input');
  });

  test('addContextItem', () => {
    const item = {
      type_id: 'file',
      link: 'link',
      title: 'title',
      identifier: 'id',
    };
    model.addContextItem(item);
    let state = model.getState();
    assert.lengthOf(state.draftUserMessage.contextItems, 1);
    assert.deepEqual(state.draftUserMessage.contextItems[0], item);

    // Adding the same item again should not change the state.
    model.addContextItem(item);
    state = model.getState();
    assert.lengthOf(state.draftUserMessage.contextItems, 1);
  });

  test('removeContextItem', () => {
    const item = {
      type_id: 'file',
      link: 'link',
      title: 'title',
      identifier: 'id',
    };
    model.addContextItem(item);
    let state = model.getState();
    assert.lengthOf(state.draftUserMessage.contextItems, 1);

    model.removeContextItem(item);
    state = model.getState();
    assert.isEmpty(state.draftUserMessage.contextItems);
  });
});
