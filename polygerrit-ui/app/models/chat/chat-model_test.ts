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
import {Side} from '../../api/diff';
import {
  BasePatchSetNum,
  CommentRange,
  RevisionPatchSetNum,
} from '../../api/rest-api';

import sinon from 'sinon';
import {ParsedChangeInfo} from '../../types/types';
import {getAppContext} from '../../services/app-context';
import {Interaction, Timing} from '../../constants/reporting';

suite('chat-model tests', () => {
  let model: ChatModel;
  let pluginsModel: PluginsModel;
  let changeModel: ChangeModel;
  let filesModel: FilesModel;
  let userModel: UserModel;
  let updatePreferencesStub: sinon.SinonStub;
  let provider: AiCodeReviewProvider;
  let patchNum$: BehaviorSubject<RevisionPatchSetNum | undefined>;
  let basePatchNum$: BehaviorSubject<BasePatchSetNum | undefined>;

  setup(() => {
    pluginsModel = new PluginsModel();
    patchNum$ = new BehaviorSubject<RevisionPatchSetNum | undefined>(undefined);
    basePatchNum$ = new BehaviorSubject<BasePatchSetNum | undefined>(undefined);
    changeModel = {
      change$: new BehaviorSubject(undefined),
      patchNum$,
      basePatchNum$,
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

  test('processChatRequest delegates to startNewChatWithUserInput', () => {
    const startNewChatStub = sinon.stub(model, 'startNewChatWithUserInput');
    model.processChatRequest({prompt: 'Explain this code'});
    assert.isTrue(
      startNewChatStub.calledOnceWith('Explain this code', undefined, [], false)
    );
  });

  test('processChatRequest delegates to startNewChatWithSelectionContext when selection params are present', () => {
    const startNewChatWithSelectionStub = sinon.stub(
      model,
      'startNewChatWithSelectionContext'
    );
    const range: CommentRange = {
      start_line: 1,
      start_character: 2,
      end_line: 3,
      end_character: 4,
    };
    model.processChatRequest({
      prompt: 'fallback prompt',
      path: 'file.txt',
      side: Side.RIGHT,
      range,
    });
    assert.isTrue(
      startNewChatWithSelectionStub.calledOnceWith(
        'file.txt',
        Side.RIGHT,
        range,
        'fallback prompt',
        'explain_this_code'
      )
    );
  });

  test('processChatRequest falls back to startNewChatWithUserInput when selection params are incomplete', () => {
    const startNewChatWithSelectionStub = sinon.stub(
      model,
      'startNewChatWithSelectionContext'
    );
    const startNewChatStub = sinon.stub(model, 'startNewChatWithUserInput');
    model.processChatRequest({
      prompt: 'Explain this code',
      path: 'file.txt',
      side: Side.RIGHT,
      // missing range
    });
    assert.isTrue(startNewChatWithSelectionStub.notCalled);
    assert.isTrue(
      startNewChatStub.calledOnceWith('Explain this code', undefined, [], false)
    );
  });

  test('startNewChatWithSelectionContext initializes state and triggers request', () => {
    const sendChatRequestStub = sinon.stub(model, 'sendChatRequest');
    const range: CommentRange = {
      start_line: 1,
      start_character: 2,
      end_line: 3,
      end_character: 4,
    };

    model.updateState({
      ...model.getState(),
      actions: {
        actions: [
          {
            id: 'explain_this_code',
            display_text: 'Explain this code',
            initial_user_prompt: 'Explain this code in detail',
          },
        ],
        default_action_id: 'default-action',
      },
    });

    model.startNewChatWithSelectionContext(
      'file.txt',
      Side.RIGHT,
      range,
      'fallback prompt',
      'explain_this_code'
    );

    const state = model.getState();
    assert.lengthOf(state.turns, 1);
    const userMessage = state.turns[0].userMessage;
    assert.equal(userMessage.content, 'Explain this code in detail');
    assert.equal(userMessage.actionId, 'explain_this_code');
    assert.equal(userMessage.prompt, 'fallback prompt');
    assert.equal(userMessage.path, 'file.txt');
    assert.equal(userMessage.side, Side.RIGHT);
    assert.deepEqual(userMessage.range, range);

    assert.isTrue(sendChatRequestStub.calledOnceWith(0));
  });

  test('sendChatRequest populates selection fields and maps patchsets', async () => {
    const change = createParsedChange();
    changeModel.updateStateChange(change);
    model.updateState({
      ...model.getState(),
      models: {models: [], default_model_id: ''},
      actions: {
        actions: [{id: 'explain_this_code', display_text: 'Explain'}],
        default_action_id: 'default-action',
      },
    });

    const range: CommentRange = {
      start_line: 1,
      start_character: 2,
      end_line: 3,
      end_character: 4,
    };

    const chatStub = provider.chat as sinon.SinonStub;

    patchNum$.next(2 as RevisionPatchSetNum);
    basePatchNum$.next('PARENT' as BasePatchSetNum);

    model.startNewChatWithSelectionContext(
      'file.txt',
      Side.RIGHT,
      range,
      'fallback prompt',
      'explain_this_code'
    );

    assert.isTrue(chatStub.calledOnce);
    const request = chatStub.lastCall.args[0] as ChatRequest;
    assert.equal(request.prompt, 'fallback prompt');
    assert.equal(request.path, 'file.txt');
    assert.equal(request.side, Side.RIGHT);
    assert.deepEqual(request.range, range);
    assert.equal(request.lhsPatchset, 0);
    assert.equal(request.rhsPatchset, 2);

    // Test mapping of numeric base patch set
    chatStub.resetHistory();
    basePatchNum$.next(1 as BasePatchSetNum);
    model.sendChatRequest(0);
    assert.isTrue(chatStub.calledOnce);
    const request2 = chatStub.lastCall.args[0] as ChatRequest;
    assert.equal(request2.lhsPatchset, 1);

    // Test mapping of 'edit' patch set
    chatStub.resetHistory();
    patchNum$.next('edit' as RevisionPatchSetNum);
    model.sendChatRequest(0);
    assert.isTrue(chatStub.calledOnce);
    const request3 = chatStub.lastCall.args[0] as ChatRequest;
    assert.isUndefined(request3.rhsPatchset);
  });

  test('subsequent follow-up turns do not carry selection properties or fallback prompt', async () => {
    const change = createParsedChange();
    changeModel.updateStateChange(change);
    model.updateState({
      ...model.getState(),
      models: {models: [], default_model_id: ''},
      actions: {
        actions: [
          {id: 'default-action', display_text: 'Default'},
          {id: 'explain_this_code', display_text: 'Explain'},
        ],
        default_action_id: 'default-action',
      },
    });

    const range: CommentRange = {
      start_line: 1,
      start_character: 2,
      end_line: 3,
      end_character: 4,
    };

    const chatStub = provider.chat as sinon.SinonStub;

    patchNum$.next(2 as RevisionPatchSetNum);
    basePatchNum$.next('PARENT' as BasePatchSetNum);

    // 1st turn: selection-based chat
    model.startNewChatWithSelectionContext(
      'file.txt',
      Side.RIGHT,
      range,
      'fallback prompt',
      'explain_this_code'
    );

    assert.isTrue(chatStub.calledOnce);
    const firstRequest = chatStub.lastCall.args[0] as ChatRequest;
    assert.equal(firstRequest.prompt, 'fallback prompt');
    assert.equal(firstRequest.path, 'file.txt');
    assert.deepEqual(firstRequest.range, range);

    // Mock Gemini response done
    chatStub.lastCall.args[1].emitResponse({
      response_parts: [{id: 1, text: 'gemini response'}],
      references: [],
      citations: [],
    });
    chatStub.lastCall.args[1].done();

    // Verify draft user message has been cleared of selection properties
    const state = model.getState();
    assert.isUndefined(state.draftUserMessage.prompt);
    assert.isUndefined(state.draftUserMessage.path);
    assert.isUndefined(state.draftUserMessage.side);
    assert.isUndefined(state.draftUserMessage.range);

    // 2nd turn: follow-up chat
    chatStub.resetHistory();
    model.updateUserInput('follow-up question');
    model.chat('follow-up question', undefined, 1);

    assert.isTrue(chatStub.calledOnce);
    const secondRequest = chatStub.lastCall.args[0] as ChatRequest;
    assert.equal(secondRequest.prompt, 'follow-up question');
    assert.isUndefined(secondRequest.path);
    assert.isUndefined(secondRequest.side);
    assert.isUndefined(secondRequest.range);
  });

  suite('telemetry reporting', () => {
    let timeStub: sinon.SinonStub;
    let timeEndStub: sinon.SinonStub;
    let reportInteractionStub: sinon.SinonStub;

    setup(() => {
      timeStub = sinon.stub(getAppContext().reportingService, 'time');
      timeEndStub = sinon.stub(getAppContext().reportingService, 'timeEnd');
      reportInteractionStub = sinon.stub(
        getAppContext().reportingService,
        'reportInteraction'
      );

      // Set up a change, models, and actions
      const models = {
        models: [
          {
            model_id: 'test-model',
            full_display_text: 'Test Model',
            short_text: 'Test',
          },
        ],
        default_model_id: 'test-model',
      };
      const actions = {
        actions: [
          {
            id: 'test-action',
            display_text: 'Test Action',
            initial_user_prompt: 'Test Prompt',
          },
        ],
        default_action_id: 'test-action',
      };
      (provider.getActions as sinon.SinonStub).resolves(actions);
      (provider.getModels as sinon.SinonStub).resolves(models);

      changeModel.updateStateChange(createParsedChange());
    });

    test('chat request starts a timer', async () => {
      await new Promise(resolve => setTimeout(resolve, 0));

      model.updateUserInput('hello');
      model.chat('hello', 'test-action', 0);

      assert.isTrue(timeStub.calledOnceWith(Timing.AI_CHAT_REQUEST));
    });

    test('chat request success stops the timer', async () => {
      await new Promise(resolve => setTimeout(resolve, 0));

      (provider.chat as sinon.SinonStub).callsFake((_, listener) => {
        listener.done();
      });

      model.updateUserInput('hello');
      model.chat('hello', 'test-action', 0);

      assert.isTrue(
        timeEndStub.calledOnceWith(Timing.AI_CHAT_REQUEST, {
          modelName: 'test-model',
          actionId: 'test-action',
        })
      );
    });

    test('chat request failure stops the timer and logs interaction', async () => {
      await new Promise(resolve => setTimeout(resolve, 0));

      (provider.chat as sinon.SinonStub).callsFake((_, listener) => {
        listener.emitError('some error');
      });

      model.updateUserInput('hello');
      model.chat('hello', 'test-action', 0);

      assert.isTrue(
        timeEndStub.calledOnceWith(Timing.AI_CHAT_REQUEST, {
          modelName: 'test-model',
          actionId: 'test-action',
          error: 'some error',
        })
      );

      assert.isTrue(
        reportInteractionStub.calledOnceWith(Interaction.AI_CHAT_FAILURE, {
          modelName: 'test-model',
          actionId: 'test-action',
          error: 'some error',
        })
      );
    });
  });
});
