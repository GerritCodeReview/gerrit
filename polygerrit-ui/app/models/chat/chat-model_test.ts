/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {ChatModel, Turn, UserType} from './chat-model';
import {PluginsModel} from '../plugins/plugins-model';
import {ChangeModel} from '../change/change-model';
import {FilesModel} from '../change/files-model';
import {UserModel} from '../user/user-model';
import {BehaviorSubject} from 'rxjs';
import {createParsedChange} from '../../test/test-data-generators';
import {AiCodeReviewProvider, ChatRequest} from '../../api/ai-code-review';

import sinon from 'sinon';
import {ParsedChangeInfo} from '../../types/types';
import {CommitId, NumericChangeId} from '../../types/common';

suite('chat-model tests', () => {
  let model: ChatModel;
  let pluginsModel: PluginsModel;
  let changeModel: ChangeModel;
  let filesModel: FilesModel;
  let userModel: UserModel;
  let updatePreferencesStub: sinon.SinonStub;
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
    updatePreferencesStub = sinon.stub();
    userModel = {
      getState: () => {
        return {preferences: {}};
      },
      preferences$: new BehaviorSubject({}),
      updatePreferences: updatePreferencesStub,
    } as unknown as UserModel;
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

    model = new ChatModel(pluginsModel, changeModel, filesModel, userModel);
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

  test('getModels with custom_actions updates actions', async () => {
    const customActions = [{id: 'custom', display_text: 'Custom'}];
    (provider.getModels as sinon.SinonStub).resolves({
      models: [],
      default_model_id: '',
      custom_actions: customActions,
    });

    changeModel.updateStateChange(createParsedChange());
    // Wait for the promise to resolve
    await new Promise(resolve => setTimeout(resolve, 0));

    const state = model.getState();
    assert.isDefined(state.customActions);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    assert.deepEqual(state.customActions, customActions as any);
  });

  test('chat uses selected model', async () => {
    // Mock getModels to return multiple models
    const models = {
      models: [
        {
          model_id: 'default-model',
          full_display_text: 'Default Model',
          short_text: 'Default',
        },
        {
          model_id: 'advanced-model',
          full_display_text: 'Advanced Model',
          short_text: 'Advanced',
        },
      ],
      default_model_id: 'default-model',
    };
    const actions = {
      actions: [],
      default_action_id: 'default-action',
    };
    (provider.getActions as sinon.SinonStub).resolves(actions);
    (provider.getModels as sinon.SinonStub).resolves(models);

    changeModel.updateStateChange(createParsedChange());
    await new Promise(resolve => setTimeout(resolve, 0));

    // Select the non-default model
    model.selectModel('advanced-model');

    assert.isTrue(
      updatePreferencesStub.calledWith({
        ai_chat_selected_model: 'advanced-model',
      })
    );

    // Trigger a chat
    model.updateUserInput('hello');
    // We need an action to be defined. Since we defined default_action_id above,
    // getAction will fallback to it if we assume it exists in actions list.
    // However, our mocked actions list is empty. Let's add the default action.
    actions.actions = [
      {
        id: 'default-action',
        display_text: 'Default Action',
        initial_user_prompt: 'Hello',
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ] as any;

    model.chat('hello', undefined, 0);

    // Verify provider.chat was called with correct model_name
    assert.isTrue((provider.chat as sinon.SinonStub).called);
    const request = (provider.chat as sinon.SinonStub).lastCall
      .args[0] as ChatRequest;
    assert.equal(request.model_name, 'advanced-model');
  });

  test('selectedModelId$ falls back when preferred model is unavailable', async () => {
    const models = {
      models: [
        {
          model_id: 'default-model',
        },
      ],
      default_model_id: 'default-model',
    };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (provider.getModels as sinon.SinonStub).resolves(models as any);

    changeModel.updateStateChange(createParsedChange());
    await new Promise(resolve => setTimeout(resolve, 0));

    model.selectModel('removed-model');

    let selectedModelId;
    const sub = model.selectedModelId$.subscribe(id => (selectedModelId = id));
    assert.equal(selectedModelId, 'default-model');
    sub.unsubscribe();
  });

  test('chat falls back to default model when selected model is unavailable', async () => {
    const models = {
      models: [
        {
          model_id: 'default-model',
        },
      ],
      default_model_id: 'default-model',
    };
    const actions = {
      actions: [
        {
          id: 'default-action',
          display_text: 'Default Action',
          initial_user_prompt: 'Hello',
        },
      ],
      default_action_id: 'default-action',
    };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (provider.getActions as sinon.SinonStub).resolves(actions as any);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (provider.getModels as sinon.SinonStub).resolves(models as any);

    changeModel.updateStateChange(createParsedChange());
    await new Promise(resolve => setTimeout(resolve, 0));

    model.selectModel('removed-model');

    model.updateUserInput('hello');
    model.chat('hello', undefined, 0);

    const request = (provider.chat as sinon.SinonStub).lastCall
      .args[0] as ChatRequest;
    assert.equal(request.model_name, 'default-model');
  });

  test('change navigation resets state', () => {
    model.updateUserInput('some input');
    model.selectModel('some-model');
    let state = model.getState();
    assert.equal(state.draftUserMessage.content, 'some input');
    assert.equal(state.selectedModelId, 'some-model');

    changeModel.updateStateChange(createParsedChange());
    state = model.getState();
    assert.equal(state.draftUserMessage.content, '');
    assert.isUndefined(state.selectedModelId);
    assert.isEmpty(state.turns);
  });

  test('change property update does not trigger API calls', () => {
    const change = {
      ...createParsedChange(),
      _number: 123,
    } as unknown as ParsedChangeInfo;
    changeModel.updateStateChange(change);
    assert.isTrue((provider.getModels as sinon.SinonStub).calledOnce);

    // Update some property but keep _number the same
    const updatedChange = {
      ...change,
      subject: 'updated subject',
    } as unknown as ParsedChangeInfo;
    changeModel.updateStateChange(updatedChange);

    // API calls should not be triggered again
    assert.isTrue((provider.getModels as sinon.SinonStub).calledOnce);
    assert.isTrue((provider.getActions as sinon.SinonStub).calledOnce);
    assert.isTrue((provider.getContextItemTypes as sinon.SinonStub).calledOnce);
  });

  test('regenerateMessage increments regenerationIndex when no error', () => {
    const turn: Turn = {
      userMessage: {
        content: 'hello',
        userType: UserType.USER,
        contextItems: [],
      },
      geminiMessage: {
        userType: UserType.GEMINI,
        responseParts: [],
        regenerationIndex: 0,
        references: [],
        citations: [],
      },
    };
    model.updateState({
      ...model.getState(),
      turns: [turn],
    });

    sinon.stub(model, 'sendChatRequest');

    model.regenerateMessage({turnIndex: 0, regenerationIndex: 0});

    const state = model.getState();
    assert.equal(state.turns[0].geminiMessage.regenerationIndex, 1);
  });

  test('regenerateMessage does not increment regenerationIndex when error exists', () => {
    const turn: Turn = {
      userMessage: {
        content: 'hello',
        userType: UserType.USER,
        contextItems: [],
      },
      geminiMessage: {
        userType: UserType.GEMINI,
        responseParts: [],
        regenerationIndex: 0,
        references: [],
        citations: [],
        errorMessage: 'error',
      },
    };
    model.updateState({
      ...model.getState(),
      turns: [turn],
    });

    sinon.stub(model, 'sendChatRequest');

    model.regenerateMessage({turnIndex: 0, regenerationIndex: 0});

    const state = model.getState();
    assert.equal(state.turns[0].geminiMessage.regenerationIndex, 0);
  });

  suite('commit message suggestion tests', () => {
    test('fetchCommitMessageSuggestion successfully gets suggestion', async () => {
      const change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        current_revision: 'rev123' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change);

      (provider.getActions as sinon.SinonStub).resolves({
        actions: [
          {id: 'Improve_Commit_Message', initial_user_prompt: 'suggestion'},
        ],
        default_action_id: 'Improve_Commit_Message',
      });
      (provider.getModels as sinon.SinonStub).resolves({
        models: [{model_id: 'default-model'}],
        default_model_id: 'default-model',
      });

      await new Promise(resolve => setTimeout(resolve, 0));
      (provider.chat as sinon.SinonStub).resetHistory();

      let suggestion: string | undefined;
      const sub = model.commitMessageSuggestion$.subscribe(
        s => (suggestion = s)
      );

      let isFetching = false;
      const isFetchingSub = model.isFetchingCommitMessageSuggestion$.subscribe(
        f => (isFetching = f)
      );

      model.fetchCommitMessageSuggestion();

      assert.isTrue(isFetching);

      const listener = (provider.chat as sinon.SinonStub).lastCall.args[1];

      listener.emitResponse({
        response_parts: [{id: 1, text: 'Suggestion result'}],
      });
      listener.done();

      assert.isFalse(isFetching);
      assert.equal(suggestion, 'Suggestion result');

      sub.unsubscribe();
      isFetchingSub.unsubscribe();
    });

    test('fetchCommitMessageSuggestion returns early when already fetching', async () => {
      const change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        current_revision: 'rev123' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change);

      model.updateState({
        isFetchingCommitMessageSuggestion: true,
      });

      (provider.chat as sinon.SinonStub).resetHistory();
      model.fetchCommitMessageSuggestion();

      assert.isFalse((provider.chat as sinon.SinonStub).called);
    });

    test('fetchCommitMessageSuggestion returns early when already loaded for revision', async () => {
      const change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        current_revision: 'rev123' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change);

      model.updateState({
        isFetchingCommitMessageSuggestion: false,
        suggestionLoadedForRevision: 'rev123',
      });

      (provider.chat as sinon.SinonStub).resetHistory();
      model.fetchCommitMessageSuggestion();

      assert.isFalse((provider.chat as sinon.SinonStub).called);
    });

    test('fetchCommitMessageSuggestion defers fetching when dependencies not ready', async () => {
      const change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        current_revision: 'rev123' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change);

      await new Promise(resolve => setTimeout(resolve, 0));

      // Dependency actions/models missing
      model.updateState({
        actions: undefined,
        models: undefined,
      });

      await new Promise(resolve => setTimeout(resolve, 0));
      (provider.chat as sinon.SinonStub).resetHistory();
      model.fetchCommitMessageSuggestion();

      assert.isFalse((provider.chat as sinon.SinonStub).called);

      await new Promise(resolve => setTimeout(resolve, 0));

      // Verify setting deferred state
      const state = model.getState();
      assert.isFalse(state.isFetchingCommitMessageSuggestion);
    });

    test('fetchCommitMessageSuggestion handles emission of errors', async () => {
      const change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        current_revision: 'rev123' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change);

      (provider.getActions as sinon.SinonStub).resolves({
        actions: [{id: 'Improve_Commit_Message'}],
        default_action_id: 'Improve_Commit_Message',
      });
      (provider.getModels as sinon.SinonStub).resolves({
        models: [{model_id: 'default-model'}],
        default_model_id: 'default-model',
      });

      await new Promise(resolve => setTimeout(resolve, 0));

      let errorMsg: string | undefined;
      const errorSub = model.commitMessageSuggestionError$.subscribe(
        e => (errorMsg = e)
      );

      (provider.chat as sinon.SinonStub).resetHistory();
      model.fetchCommitMessageSuggestion();

      const listener = (provider.chat as sinon.SinonStub).lastCall.args[1];

      listener.emitError('fetch failed error');

      assert.equal(errorMsg, 'fetch failed error');
      assert.isFalse(model.getState().isFetchingCommitMessageSuggestion);

      errorSub.unsubscribe();
    });

    test('fetchCommitMessageSuggestion sets undefined on empty suggestion message', async () => {
      const change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        current_revision: 'rev123' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change);

      (provider.getActions as sinon.SinonStub).resolves({
        actions: [{id: 'Improve_Commit_Message'}],
        default_action_id: 'Improve_Commit_Message',
      });
      (provider.getModels as sinon.SinonStub).resolves({
        models: [{model_id: 'default-model'}],
        default_model_id: 'default-model',
      });

      await new Promise(resolve => setTimeout(resolve, 0));

      (provider.chat as sinon.SinonStub).resetHistory();
      model.fetchCommitMessageSuggestion();

      const listener = (provider.chat as sinon.SinonStub).lastCall.args[1];

      listener.emitResponse({
        response_parts: [{id: 1, text: 'No improvements suggested.'}],
      });
      listener.done();

      assert.isUndefined(model.getState().commitMessageSuggestion);
    });

    test('change subscription clearing state on different revision', async () => {
      const change1 = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        current_revision: 'rev123' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change1);

      model.updateState({
        commitMessageSuggestion: 'Good suggestion',
        isFetchingCommitMessageSuggestion: false,
        commitMessageSuggestionError: undefined,
        suggestionLoadedForRevision: 'rev123',
      });

      const change2 = {
        ...change1,
        current_revision: 'rev124' as CommitId,
      } as ParsedChangeInfo;
      changeModel.updateStateChange(change2);

      const state = model.getState();
      assert.isUndefined(state.commitMessageSuggestion);
      assert.isUndefined(state.suggestionLoadedForRevision);
      assert.isFalse(state.isFetchingCommitMessageSuggestion);
    });
  });
});
