/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {combineLatest, Observable} from 'rxjs';

import {
  Action,
  Actions,
  AiCodeReviewProvider,
  ChatRequest,
  ChatResponse,
  ChatResponseListener,
  ChatResponsePart,
  ContextItem,
  ContextItemType,
  Conversation,
  ConversationTurn,
  CreateCommentAction,
  ModelInfo,
  Models,
  Reference,
} from '../../api/ai-code-review';
import {ChangeInfo, CommentInfo, FileInfoStatus} from '../../api/rest-api';
import {isDefined} from '../../types/types';
import {assert, assertIsDefined, cryptoUuid} from '../../utils/common-util';
import {select} from '../../utils/observable-util';
import {Model} from '../base/model';
import {ChangeModel} from '../change/change-model';
import {define} from '../dependency';
import {PluginsModel} from '../plugins/plugins-model';

import {contextItemEquals} from './context-item-util';
import {FilesModel, NormalizedFileInfo} from '../change/files-model';
import {isMagicPath} from '../../utils/path-list-util';

/** The available display modes in the chat panel. */
export enum ChatPanelMode {
  HISTORY,
  CONVERSATION,
}

/**
 * The type of user sending or receiving a message.
 */
export enum UserType {
  USER,
  GEMINI,
}

/** The type of a response part. */
export enum ResponsePartType {
  TEXT,
  CREATE_COMMENT,
}

/** A message from the user. */
export declare interface UserMessage {
  readonly userType: UserType.USER;
  readonly content: string;
  readonly actionId?: string;
  // A list of additional context items included in the chat request.
  readonly contextItems: readonly ContextItem[];
  // Whether the user message was triggered in the background (e.g. when
  // Summarize this CL is trigger when clicking the Help me review button). This
  // may affect the UI layout of the turn.
  readonly isBackgroundRequest?: boolean;
}

/**
 * This is the model internal equivalent to the API interface ChatResponsePart.
 */
export declare interface ResponsePartBase {
  // The part ID. Together with a conversation ID and turn ID, this uniquely
  // identifies a response part.
  readonly id: number;
  readonly type: ResponsePartType;
  readonly content: string;
}

/** A response part from Gemini suggesting to create a comment. */
export declare interface CreateCommentPart extends ResponsePartBase {
  // A unique ID used to identify the comment to be created by this action.
  // This is derived from the conversation ID, turn ID, and part ID.
  readonly commentCreationId: string;
  readonly type: ResponsePartType.CREATE_COMMENT;
  readonly comment: Partial<CommentInfo>;
}

/** The text part of a Gemini response. */
export declare interface TextPart extends ResponsePartBase {
  readonly type: ResponsePartType.TEXT;
}

/** A part of a Gemini response. */
export type GeminiResponsePart = TextPart | CreateCommentPart;

/** A message from Gemini. */
export declare interface GeminiMessage {
  readonly userType: UserType.GEMINI;
  readonly responseParts: readonly GeminiResponsePart[];
  // An index that increments whenever the user regenerates the Gemini response
  // for the same turn, i.e. by clicking the refresh button.
  // The default first value is 0.
  readonly regenerationIndex: number;
  readonly responseComplete?: boolean;
  readonly errorMessage?: string;
  readonly references: readonly Reference[];
  readonly citations: readonly string[];
  readonly timestamp?: Date;
}

/**
 * A turn within a Conversation. Consists of a user message and the Gemini
 * response. The Gemini response is optional, as it may not have been received
 * yet. Turns have an implicit turn index, which is the index of the turn within
 * the conversation.
 */
export declare interface Turn {
  readonly userMessage: UserMessage;
  readonly geminiMessage: GeminiMessage;
}

/**
 * A unique identifier for a turn in a conversation, accounting for turn
 * regeneration.
 */
export declare interface UniqueTurnId {
  turnIndex: number;
  regenerationIndex: number;
}

/** Fields that are required to restore the chat history in the UI. */
export declare interface ClientData {
  /**
   * When false, the FE should re-use the ClientData from the previous turn
   * instead of using the fields in this message. In this case, none of the
   * other fields in this message should be set.
   */
  overridesPreviousTurn?: boolean;

  /** The action the user selected in the chat. */
  actionId?: string;

  contextItems?: ContextItem[];

  /**
   * Whether the turn was triggered in the background (e.g. when Summarize this
   * CL is trigger when clicking the Help me review button). This affects the UI
   * layout of the turn.
   */
  isBackgroundRequest?: boolean;
}

export declare interface ConvTurnId {
  conversationId: string;
  turnId: UniqueTurnId;
}

export declare interface ConvTurnPartId extends ConvTurnId {
  partId: number;
}

/** State for the view state of an AI conversation. */
export declare interface ConversationState {
  /** Information if the request failed. */
  readonly errorMessage?: string;
  /** messages in the chat so far. */
  readonly turns: readonly Turn[];

  /**
   * The user message that is currently being drafted, and will be issued in
   * the next chat turn.
   */
  readonly draftUserMessage: UserMessage;

  /**
   * True iff context (or contextItems) was updated since the last request. Used
   * to persist new client data during the next chat turn.
   */
  readonly contextUpdated?: boolean;

  /*
   * The conversation ID which uniquely identifies a conversation. May be empty
   * if the conversation has not been started yet.
   */
  readonly id: string;
}

export declare interface ChatState extends ConversationState {
  readonly mode: ChatPanelMode;

  /** The list of conversations for the current CL. */
  readonly conversations?: readonly Conversation[];

  // Chat models for the current CL.
  readonly models?: Models;
  // Chat models for the current CL.
  readonly selectedModelId?: string;
  // Error message if the chat models failed to load.
  readonly modelsLoadingError?: string;

  // Chat actions for the current CL.
  readonly actions?: Actions;
  // Error message if the actions failed to load.
  readonly actionsLoadingError?: string;

  // The list of context item types supported by the provider.
  readonly contextItemTypes?: readonly ContextItemType[];
  // Error message if the context item types failed to load.
  readonly contextItemTypesLoadingError?: string;
}

export const initialConversationState: ConversationState = {
  turns: [],
  id: '',
  draftUserMessage: {
    userType: UserType.USER,
    content: '',
    actionId: undefined,
    contextItems: [],
  },
};

export const chatModelToken = define<ChatModel>('chat-model');

export class ChatModel extends Model<ChatState> {
  readonly models$: Observable<Models | undefined> = select(
    this.state$,
    chatState => chatState.models
  );

  readonly selectedModelId$: Observable<string | undefined> = select(
    this.state$,
    chatState => chatState.selectedModelId ?? chatState.models?.default_model_id
  );

  readonly availableModelsMap$: Observable<ReadonlyMap<string, ModelInfo>> =
    select(
      this.models$,
      models =>
        new Map(
          (models?.models ?? []).map(model => [model.model_id, model])
        ) as ReadonlyMap<string, ModelInfo>
    );

  readonly selectedModel$: Observable<ModelInfo | undefined> = select(
    combineLatest([this.availableModelsMap$, this.selectedModelId$]),
    ([availableModelsMap, selectedModelId]) => {
      if (!selectedModelId) return undefined;
      return availableModelsMap.get(selectedModelId);
    }
  );

  readonly modelsLoadingError$: Observable<string | undefined> = select(
    this.state$,
    chatState => chatState.modelsLoadingError
  );

  readonly actions$: Observable<readonly Action[]> = select(
    this.state$,
    chatState => chatState.actions?.actions ?? []
  );

  readonly defaultActionId$: Observable<string | undefined> = select(
    this.state$,
    chatState => chatState.actions?.default_action_id
  );

  readonly defaultAction$: Observable<Action | undefined> = select(
    combineLatest([this.actions$, this.defaultActionId$]),
    ([actions, defaultActionId]) =>
      actions.find(action => action.id === defaultActionId)
  );

  readonly contextItemTypes$: Observable<readonly ContextItemType[]> = select(
    this.state$,
    chatState => chatState.contextItemTypes ?? []
  );

  readonly turns$: Observable<readonly Turn[] | undefined> = select(
    this.state$,
    chatState => chatState.turns
  );

  readonly nextTurnIndex$: Observable<number> = select(
    this.turns$,
    turns => turns?.length ?? 0
  );

  readonly conversations$: Observable<readonly Conversation[]> = select(
    this.state$,
    chatState => chatState.conversations ?? []
  );

  readonly conversationId$: Observable<string | undefined> = select(
    this.state$,
    chatState => chatState.id
  );

  readonly mode$: Observable<ChatPanelMode> = select(
    this.state$,
    chatState => chatState.mode
  );

  readonly errorMessage$: Observable<string | undefined> = select(
    this.state$,
    chatState => chatState.errorMessage
  );

  readonly userInput$: Observable<string> = select(
    this.state$,
    chatState => chatState.draftUserMessage.content
  );

  readonly userContextItems$: Observable<readonly ContextItem[]> = select(
    this.state$,
    chatState => chatState.draftUserMessage.contextItems
  );

  private plugin?: AiCodeReviewProvider;

  private change?: ChangeInfo;

  private files: NormalizedFileInfo[] = [];

  constructor(
    private readonly pluginsModel: PluginsModel,
    private readonly changeModel: ChangeModel,
    private readonly filesModel: FilesModel
  ) {
    super({
      mode: ChatPanelMode.CONVERSATION,
      ...initialConversationState,
    });

    this.pluginsModel.aiCodeReviewPlugins$.subscribe(
      plugins => (this.plugin = plugins[0].provider)
    );
    this.filesModel.files$.subscribe(files => (this.files = files ?? []));
    this.changeModel.change$.subscribe(change => {
      this.change = change as ChangeInfo;
      this.getModels();
      this.getActions();
      this.getContextItemTypes();
      this.listConversations();
    });
  }

  contextItemToType(contextItem?: ContextItem): ContextItemType | undefined {
    if (!contextItem) return undefined;
    const state = this.getState();
    const contextItemTypes = state.contextItemTypes;
    if (!contextItemTypes) return undefined;
    return contextItemTypes.find(
      contextItemType => contextItemType.id === contextItem.type_id
    );
  }

  regenerateMessage(turnId: UniqueTurnId) {
    const nextMessage = thinkingGeminiMessage(turnId.regenerationIndex + 1);
    const state = this.getState();
    let turns = state.turns;
    const turnIndex = turnId.turnIndex;
    assert(turnIndex < turns.length, 'turnIndex out of bounds');
    turns = [
      ...turns.slice(0, turnIndex),
      {
        ...turns[turnIndex],
        geminiMessage: nextMessage,
      },
      ...turns.slice(turnIndex + 1),
    ];
    this.updateState({
      ...state,
      turns,
      // It's possible that the context changed between message n-1 and n,
      // but at this point we've forgotten. An easy workaround is to just
      // assume it did and persist new client data.
      contextUpdated: true,
    });

    this.sendChatRequest(turnId.turnIndex);
  }

  updateUserInput(content: string) {
    const state = this.getState();
    this.updateState({
      ...state,
      draftUserMessage: {
        ...state.draftUserMessage,
        content,
      },
    });
  }

  chat(
    userInputFreeForm: string,
    actionId: string | undefined,
    turnIndex: number
  ) {
    const action = this.getAction(actionId);
    assertIsDefined(action, 'action');
    const userQuestion = userInputFreeForm || action.initial_user_prompt;
    assertIsDefined(userQuestion, 'userQuestion');

    const state = this.getState();
    const userMessage: UserMessage = {
      ...state.draftUserMessage,
      content: userQuestion,
      actionId: action.id,
      isBackgroundRequest: false,
    };
    const nextTurn = {
      userMessage,
      geminiMessage: thinkingGeminiMessage(),
    };

    this.updateState({
      ...state,
      id: state.id || cryptoUuid(),
      errorMessage: undefined,
      turns: [...state.turns, nextTurn],
      draftUserMessage: draftFromUserMessage(userMessage),
    });

    this.sendChatRequest(turnIndex);
  }

  getAction(id?: string) {
    const state = this.getState();
    const actions = state.actions?.actions ?? [];
    const defaultActionId = state.actions?.default_action_id;
    return (
      actions.find(action => action.id === id) ??
      actions.find(action => action.id === defaultActionId)
    );
  }

  sendChatRequest(turnIndex: number) {
    assertIsDefined(this.change, 'change');
    const change = this.change;
    const files = this.files
      .map(file => {
        return {
          path: file.__path,
          status: file.status ?? FileInfoStatus.MODIFIED,
        };
      })
      .filter(file => !isMagicPath(file.path));
    const state = this.getState();
    assertIsDefined(state.models, 'state.models');

    const turn = state.turns[turnIndex];
    assertIsDefined(turn, 'turn');
    const previousTurn = turnIndex > 0 ? state.turns[turnIndex - 1] : undefined;
    const userMessage = turn.userMessage;
    const turnId: UniqueTurnId = {
      turnIndex,
      regenerationIndex: turn.geminiMessage.regenerationIndex,
    };
    const contextItems = [...userMessage.contextItems];
    const actionId = userMessage.actionId;
    const action = this.getAction(actionId);
    assertIsDefined(action, 'action');
    const contextUpdated = !!state.contextUpdated;
    const isBackgroundRequest = !!turn.userMessage.isBackgroundRequest;
    const previousTurnIsBackgroundRequest =
      !!previousTurn?.userMessage.isBackgroundRequest;
    const conversationId = state.id;

    const clientData: ClientData = {};
    if (
      turnIndex === 0 ||
      contextUpdated ||
      isBackgroundRequest !== previousTurnIsBackgroundRequest
    ) {
      clientData.overridesPreviousTurn = true;
      clientData.actionId = actionId;
      clientData.contextItems = contextItems;
      clientData.isBackgroundRequest = isBackgroundRequest;
    }

    const request: ChatRequest = {
      action,
      prompt: userMessage.content,
      conversation_id: conversationId,
      change,
      files,
      turn_index: turnIndex,
      regeneration_index: turn.geminiMessage.regenerationIndex,
      client_data: JSON.stringify(clientData),
      model_name: state.models.default_model_id,
      external_contexts: contextItems,
    };
    const listener: ChatResponseListener = {
      emitResponse: (response: ChatResponse) => {
        const state = this.getState();
        if (state.id !== conversationId) return;
        if (turnIndex >= state.turns.length) return;
        const geminiMessage: Partial<GeminiMessage> = {
          responseParts: extractResponseParts(response, {
            turnId,
            conversationId: state.id,
          }),
          references: response.references ?? [],
          citations: response.citations ?? [],
          timestamp: new Date(response.timestamp_millis ?? 0),
        };
        this.updateState({
          ...mergeIntoTurn(state, turnId, geminiMessage),
          errorMessage: undefined,
          contextUpdated: false,
        });
      },
      emitError: (errorMessage: string) => {
        const state = this.getState();
        if (state.id !== conversationId) return;
        const turns: readonly Turn[] = state.turns;
        const lastTurn: Turn | undefined = turns[turns.length - 1];
        if (!lastTurn?.geminiMessage) {
          this.updateState({errorMessage});
          return;
        }
        this.updateState({
          ...mergeIntoTurn(state, turnId, {errorMessage}),
          errorMessage,
        });
      },
      done: () => {
        const state = this.getState();
        if (state.id !== conversationId) return;
        assert(turnIndex < state.turns.length, 'turn index out of bounds');
        const geminiMessage: Partial<GeminiMessage> = {
          responseComplete: true,
        };
        this.updateState({
          ...mergeIntoTurn(state, turnId, geminiMessage),
          contextUpdated: false,
        });
      },
    };
    this.plugin?.chat?.(request, listener);
  }

  startNewChatWithPredefinedPrompt(
    actionId: string | undefined,
    contextItems: ContextItem[] = [],
    isBackgroundRequest = false
  ) {
    const action = this.getAction(actionId);
    assertIsDefined(action, 'action');
    const userQuestion = action.initial_user_prompt;
    if (!userQuestion) return;
    const message: UserMessage = {
      userType: UserType.USER,
      content: userQuestion ?? '',
      actionId: action.id,
      contextItems,
      isBackgroundRequest,
    };
    const turns: Turn[] = [userTurn(message)];

    this.updateState({
      ...initialConversationState,
      id: cryptoUuid(),
      turns,
      draftUserMessage: draftFromUserMessage(message),
    });

    this.sendChatRequest(0);
  }

  startNewChatWithUserInput(
    userInput: string,
    actionId: string | undefined,
    contextItems: ContextItem[] = [],
    useCurrentContext = true
  ) {
    const state = this.getState();
    const message: UserMessage = {
      userType: UserType.USER,
      content: userInput,
      actionId,
      contextItems: useCurrentContext
        ? state.draftUserMessage.contextItems
        : contextItems,
    };
    const turns: Turn[] = userInput ? [userTurn(message)] : [];

    this.updateState({
      ...initialConversationState,
      id: cryptoUuid(),
      turns,
      draftUserMessage: draftFromUserMessage(message),
    });

    if (userInput) this.sendChatRequest(0);
  }

  addContextItem(contextItem: ContextItem) {
    const state = this.getState();
    const currentItems = state.draftUserMessage.contextItems;
    if (currentItems.some(item => contextItemEquals(item, contextItem))) {
      return;
    }
    this.updateState({
      ...state,
      draftUserMessage: {
        ...state.draftUserMessage,
        contextItems: [...currentItems, contextItem],
      },
      contextUpdated: true,
    });
  }

  removeContextItem(contextItem: ContextItem) {
    const state = this.getState();
    const currentItems = state.draftUserMessage.contextItems;
    this.updateState({
      ...state,
      draftUserMessage: {
        ...state.draftUserMessage,
        contextItems: currentItems.filter(
          item => !contextItemEquals(item, contextItem)
        ),
      },
      contextUpdated: true,
    });
  }

  startEmptyNewChat(useCurrentContext: boolean) {
    const state = this.getState();
    const currentDraftUserMessage = state.draftUserMessage;
    const draftUserMessage = {
      ...initialConversationState.draftUserMessage,
      contextItems: useCurrentContext
        ? currentDraftUserMessage.contextItems
        : [],
    };

    this.updateState({
      ...initialConversationState,
      id: cryptoUuid(),
      draftUserMessage,
      turns: [],
    });
  }

  setMode(mode: ChatPanelMode) {
    this.updateState({mode});
    if (mode === ChatPanelMode.HISTORY) {
      this.listConversations();
    }
  }

  listConversations() {
    if (!this.change) return;
    return this.plugin
      ?.listChatConversations?.(this.change)
      .then((conversations: Conversation[]) => {
        this.updateState({conversations});
      })
      .catch((error: Error) => {
        this.updateState({errorMessage: error.message});
        console.error('Failed to list chat conversations', error);
      });
  }

  loadConversation(conversationId: string) {
    if (!this.change) return;
    return this.plugin
      ?.getChatConversation?.(this.change, conversationId)
      .then((turns: ConversationTurn[]) => {
        const conversationState = stateFromConversationResponse(
          turns,
          conversationId
        );
        this.updateState({
          mode: ChatPanelMode.CONVERSATION,
          ...conversationState,
        });
      })
      .catch((error: Error) => {
        this.updateState({errorMessage: error.message});
        console.error('Failed to load chat conversation', error);
      });
  }

  selectModel(selectedModelId: string) {
    this.updateState({selectedModelId});
  }

  getModels() {
    if (!this.change) return;
    return this.plugin
      ?.getModels?.(this.change)
      .then((models: Models) => {
        this.updateState({
          models,
          modelsLoadingError: undefined,
        });
      })
      .catch((error: Error) => {
        this.updateState({
          models: undefined,
          modelsLoadingError: error.message,
        });
        console.error('Failed to get chat models', error);
      });
  }

  getActions() {
    if (!this.change) return;
    return this.plugin
      ?.getActions?.(this.change)
      .then((actions: Actions) => {
        this.updateState({
          actions,
          actionsLoadingError: undefined,
        });
      })
      .catch((error: Error) => {
        this.updateState({
          actions: undefined,
          actionsLoadingError: error.message,
        });
        console.error('Failed to get chat actions', error);
      });
  }

  getContextItemTypes() {
    return this.plugin
      ?.getContextItemTypes?.()
      .then((contextItemTypes: ContextItemType[]) => {
        this.updateState({
          contextItemTypes,
          contextItemTypesLoadingError: undefined,
        });
      })
      .catch((error: Error) => {
        this.updateState({
          contextItemTypes: undefined,
          contextItemTypesLoadingError: error.message,
        });
        console.error('Failed to get chat context types', error);
      });
  }
}

function buildCommentCreationId({
  conversationId,
  turnId,
  partId,
}: ConvTurnPartId) {
  return `chat-panel-generated-comment:${conversationId}:${turnId.turnIndex}:${turnId.regenerationIndex}:${partId}`;
}

function userTurn(userMessage: UserMessage): Turn {
  return {
    userMessage,
    geminiMessage: thinkingGeminiMessage(),
  };
}

/**
 * Creates a Gemini message in the thinking state. The message has no response
 * parts. Visible for testing.
 */
function thinkingGeminiMessage(regenerationIndex = 0): GeminiMessage {
  return {
    userType: UserType.GEMINI,
    responseParts: [],
    regenerationIndex,
    references: [],
    citations: [],
  };
}

/**
 * Merges the given Gemini message into the existing Gemini message of the turn
 * at the given turn index.
 */
function mergeIntoTurn(
  state: ConversationState,
  turnId: UniqueTurnId,
  geminiMessage: Partial<Omit<GeminiMessage, 'regenerationIndex'>>
): ConversationState {
  const turnIndex = turnId.turnIndex;
  assert(turnIndex < state.turns.length, 'turnIndex out of bounds');

  // This merges the potentially already existing (partial) GeminiMessage of
  // this turn into turnUpdate, otherwise it would be overwritten below.
  const mergedMessage = mergeGeminiMessages(
    turnId.regenerationIndex,
    state.turns[turnIndex].geminiMessage,
    geminiMessage
  );

  const turns = [
    ...state.turns.slice(0, turnIndex),
    {...state.turns[turnIndex], geminiMessage: mergedMessage},
    ...state.turns.slice(turnIndex + 1),
  ];
  return {...state, turns};
}

/**
 * Merges the update into the existing message.
 *
 * For most GeminiMessage fields, the update will overwrite the existing
 * message. However, for responseParts, this appends the parts that are not
 * already present in the existing message.
 */
function mergeGeminiMessages(
  regenerationIndex: number,
  existingMessage?: GeminiMessage,
  update?: Partial<Omit<GeminiMessage, 'regenerationIndex'>>
): GeminiMessage {
  if (!existingMessage) {
    existingMessage = {
      userType: UserType.GEMINI,
      responseParts: [],
      references: [],
      citations: [],
      regenerationIndex,
    };
  }
  if (!update) return existingMessage;
  // We should never merge messages with different regeneration indices.
  // If this happens, it could indicate that 2 regenerations were fired in
  // parallel, or that the old message was not cleared before sending a new
  // request.
  if (existingMessage.regenerationIndex !== regenerationIndex) {
    console.error(
      `Attempted to merge messages with different regeneration indices:
        ${existingMessage.regenerationIndex} vs ${regenerationIndex}`
    );
    return existingMessage;
  }

  return {
    ...existingMessage,
    ...update,
    responseParts: mergeResponseParts(existingMessage, update),
    references: [...existingMessage.references, ...(update.references || [])],
    citations: [...existingMessage.citations, ...(update.citations || [])],
  };
}

function mergeResponseParts(
  existingMessage: GeminiMessage,
  update: Partial<Omit<GeminiMessage, 'regenerationIndex'>>
): GeminiResponsePart[] {
  const existingParts = [...(existingMessage.responseParts ?? [])];
  const updateParts = [...(update.responseParts ?? [])];
  const mergedParts: GeminiResponsePart[] = [];

  let existingPart = existingParts.shift();
  if (!existingPart) existingPart = updateParts.shift();
  let updatePart = updateParts.shift();
  while (existingPart && updatePart) {
    if (existingPart.id === updatePart.id) {
      assert(existingPart.type === updatePart.type, 'part type mismatch');
      existingPart = {
        ...existingPart,
        content: existingPart.content + updatePart.content,
      };
      updatePart = updateParts.shift();
    } else if (existingPart.id < updatePart.id) {
      mergedParts.push(existingPart);
      existingPart = existingParts.shift();
    } else {
      // Case where existingPart.id > updatePart.id.
      mergedParts.push(updatePart);
      updatePart = updateParts.shift();
    }
  }
  // Either existing parts or update parts are exhausted.
  // Append the remaining parts.
  if (existingPart) mergedParts.push(existingPart, ...existingParts);
  if (updatePart) mergedParts.push(updatePart, ...updateParts);

  return mergedParts;
}

function draftFromUserMessage(userMessage: UserMessage): UserMessage {
  return {
    ...userMessage,
    content: '',
    isBackgroundRequest: false,
  };
}

function extractResponseParts(
  response: ChatResponse,
  turnIdentifier: ConvTurnId
): GeminiResponsePart[] {
  return response.response_parts
    .map(part => asGeminiResponsePart(part, turnIdentifier))
    .filter(isDefined);
}

function asGeminiResponsePart(
  part: ChatResponsePart,
  turnIdentifier: ConvTurnId
): GeminiResponsePart | undefined {
  if (part.text) {
    return {
      id: part.id,
      type: ResponsePartType.TEXT,
      content: part.text,
    };
  } else if (part.create_comment_action) {
    return convertCreateCommentAction({
      create_comment_action: part.create_comment_action,
      partId: part.id,
      commentCreationId: buildCommentCreationId({
        ...turnIdentifier,
        partId: part.id,
      }),
    });
  } else {
    return undefined;
  }
}

function convertCreateCommentAction(kwargs: {
  create_comment_action: CreateCommentAction;
  partId: number;
  commentCreationId: string;
}): CreateCommentPart | undefined {
  return {
    type: ResponsePartType.CREATE_COMMENT,
    id: kwargs.partId,
    commentCreationId: kwargs.commentCreationId,
    content: kwargs.create_comment_action?.comment_text ?? '',
    comment: {
      ...kwargs.create_comment_action,
      message: kwargs.create_comment_action.comment_text,
    },
  };
}

function stateFromConversationResponse(
  responseTurns: ConversationTurn[],
  conversationId: string
): ConversationState {
  // The BE returns the conversation ID as a lowercase string, even though we
  // only generate uppercase strings.
  conversationId = conversationId.toUpperCase();
  let latestContextItems: readonly ContextItem[] = [];
  let latestIsBackgroundRequest = false;
  let latestActionId: string | undefined = undefined;

  const turns: Turn[] = [];
  for (let index = 0; index < responseTurns.length; index++) {
    const turn = responseTurns[index];
    const userInput = turn.user_input;
    const turnResponse = turn.response || turn.chat_response;
    const regenerationIndex = turn.regeneration_index ?? 0;
    if (!userInput || !turnResponse) {
      continue;
    }

    const clientData: ClientData = JSON.parse(
      userInput.client_data ?? '{}'
    ) as ClientData;
    if (clientData.overridesPreviousTurn) {
      latestContextItems = clientData.contextItems ?? [];
      latestActionId = clientData.actionId;
      latestIsBackgroundRequest = clientData.isBackgroundRequest ?? false;
    }

    const userMessage: UserMessage = {
      userType: UserType.USER,
      content: userInput.user_question ?? '',
      contextItems: latestContextItems,
      isBackgroundRequest: latestIsBackgroundRequest,
      actionId: latestActionId,
    };
    const geminiMessage: GeminiMessage = {
      userType: UserType.GEMINI,
      responseComplete: true,
      regenerationIndex,
      responseParts: extractResponseParts(turnResponse, {
        turnId: {turnIndex: index, regenerationIndex},
        conversationId,
      }),
      references: turnResponse.references,
      citations: turnResponse.citations ?? [],
      timestamp: turn.timestamp_millis
        ? new Date(turn.timestamp_millis)
        : undefined,
    };
    turns.push({userMessage, geminiMessage});
  }

  const draftUserMessage: UserMessage = {
    userType: UserType.USER,
    actionId: undefined,
    contextItems: [],
    ...(turns.length > 0 ? turns[turns.length - 1].userMessage : {}),
    content: '',
    isBackgroundRequest: false,
  };

  return {
    errorMessage: undefined,
    contextUpdated: undefined,
    turns,
    draftUserMessage,
    id: conversationId,
  };
}
