/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo, CommentInfo, FileInfoStatus} from './rest-api';

export declare interface AiCodeReviewPluginApi {
  /**
   * Must only be called once. You cannot register twice (throws an error).
   * You cannot unregister.
   */
  register(provider: AiCodeReviewProvider): void;
}

export declare interface Action {
  id: string;
  display_text: string;
  hover_text?: string;
  // The subtext for this action. This is displayed below the label.
  subtext?: string;
  icon?: string;
  // Whether to show the splash page card for this action.
  enable_splash_page_card?: boolean;
  // Whether to send the request without user input.
  enable_send_without_input?: boolean;
  // The prompt that is fired by this action.
  initial_user_prompt?: string;
  // The links to the context items that are implicitly added.
  context_item_links?: string[];
}

export declare interface ChatRequest {
  /** The predefined action the user selected in the chat. */
  action: Action;
  /**
   * The prompt to be sent to the LLM.
   */
  prompt: string;
  /**
   * UUID of the conversation. To start a new conversation, the caller should
   * generate a new UUID.
   * To continue an existing conversation, the caller should provide the UUID of
   * the conversation.
   */
  conversation_id: string;
  /**
   * Plugins can choose what context they want to derive from the change and
   * send along to their backends. `changeInfo` contains broadly all the
   * information about the change, and the plugin can also make additional
   * requests to the REST API (e.g. getting patch content) by using properties
   * from the change info.
   */
  change: ChangeInfo;
  /**
   * The list of files in the change is vital information that is missing from
   * `changeInfo`. So we are passing this along, as well.
   */
  files: {path: string; status: FileInfoStatus}[];
  /**
   * The 0-based turn index of the request. The caller should set it based on
   * the history of the conversation. It should be one more than the last known
   * turn. For new conversations, it should be 0.
   */
  turn_index: number;
  /**
   * The 0-based index of the turn regeneration. 0 - original turn, and it is
   * incremented by FE every time the user clicks on the regenerate button.
   */
  regeneration_index: number;
  /**
   * A payload containing FE-specific data that is used to restore the chat
   * history in the UI. The BE should not use the data, only store it and
   * return it as part of GetConversationResponse.
   * This is simply encoded/decoded by the chat-model using JSON.stringify()
   * and JSON.parse().
   */
  client_data: string;
  /**
   * The name of the model to use. If not set, the default model will
   * be used. If invalid, an error will be returned.
   */
  model_name?: string;
  /**
   * The external contexts that should be used in the request.
   */
  external_contexts: ContextItem[];
}

/**
 * The chat response may come in as a stream, so instead of just one response
 * object this listener will get multiple calls until the response is completed.
 */
export declare interface ChatResponseListener {
  /**
   * Emits one piece of a streaming response from the backend. All responses
   * must be merged into one response object by the listener, but every
   * intermediate state can be shown to the user.
   */
  emitResponse(response: ChatResponse): void;
  /**
   * Emits an error message, indicating that the turn has failed. Will be
   * immediately followed by a done() call.
   */
  emitError(error: string): void;
  /**
   * The turn is completed. All response parts have been emitted. The listener
   * can be discarded.
   */
  done(): void;
}

export declare interface ChatResponse {
  response_parts: ChatResponsePart[];
  /**
   * References that were used to generate the response. Corresponds to tool
   * usage calls by the model.
   */
  references: Reference[];
  /** The timestamp when the request was processed */
  timestamp_millis?: number;
  /**
   * The citations that were used to generate the response. Citations are
   * passages that are "recited" from potentially copyrighted material.
   */
  citations: string[];
}

export declare interface ChatResponsePart {
  /** The unique ID of the response part within the turn */
  id: number;
  /** A text part of the response, to be rendered as markdown */
  text?: string;
  /** A suggested comment that can be shown to the user */
  comment?: Partial<CommentInfo>;
  /** A text that can be copied to the clipboard */
  copyable_text?: CopyableText;
}

export declare interface CopyableText {
  text?: string;
  copyable_text?: string;
}

/** A reference that was used by Gemini to generate a response. */
export declare interface Reference {
  /** May match the type id of ContextItemType. */
  type: string;
  displayText: string;
  secondaryText?: string;
  externalUrl: string;
  errorMsg?: string;
  tooltip?: string;
}

export declare interface Conversation {
  /** UUID of the conversation */
  id: string;
  /** Title of the conversation */
  title: string;
  /** Timestamp of the last turn in the conversation */
  timestamp_millis: number;
}

export declare interface ConversationTurn {
  user_input: UserInput;
  response: ChatResponse;
  regeneration_index?: number;
  timestamp_millis?: number;
}

/**
 * The data sent by the client to the backend. It is stored in the database and
 * returned in the response. It is used to restore the history of the
 * conversation in the UI. The message should contain all data required to make
 * a turn.
 */
export declare interface UserInput {
  /** The text the user typed in the chat. Can be empty for some actions. */
  user_question?: string;
  /**
   * The data required by the UI to restore the history of the conversation.
   * The server only stores the data and doesn't care about the content.
   * This is simply encoded/decoded by the chat-model using JSON.stringify()
   * and JSON.parse().
   */
  client_data?: string;
}

export declare interface Models {
  /**
   * The models available to the user. Should be displayed in the UI in the
   * order of appearance in this list.
   */
  models: ModelInfo[];
  /**
   * The default model to use when the user hasn't selected any model yet or
   * when the selected model is not available anymore.
   */
  default_model_id: string;

  documentation_url?: string;

  citation_url?: string;

  privacy_url?: string;
}

export declare interface Actions {
  /**
   * The actions available to the user. Should be displayed in the UI in the
   * order of appearance in this list.
   */
  actions: Action[];
  /**
   * The default action to use when the user hasn't made an explicit choice.
   */
  default_action_id: string;
}

export declare interface ModelInfo {
  /** The model id which is used to identify the model. */
  model_id: string;
  /** The short text to be displayed in the UI. */
  short_text: string;
  /** The full text to be displayed in the UI. */
  full_display_text: string;
}

export declare interface ContextItemType {
  id: string;
  name: string;
  icon: string;
  /**
   * The regex to match the context item type. Will be applied to input
   * strings, but can also be used to find context items in longer texts such as
   * the user prompt.
   */
  regex: RegExp;
  /**
   * The placeholder text to be displayed in input fields. Tells the user what
   * kind of input is expected and can be parsed.
   */
  placeholder: string;
  /** Parses the input string into a context item of this type. */
  parse(input: string): ContextItem | undefined;
}

export declare interface ContextItem {
  type_id: string;
  link: string;
  title: string;
  identifier?: string;
  tooltip?: string;
  error_message?: string;
}

export declare interface AiCodeReviewProvider {
  /**
   * If a AiCodeReviewProvider provider is registered that implements this
   * method, then Gerrit will offer a side panel for the user to have an AI
   * Chat conversation. Each chat() call is one turn of such a conversation.
   */
  chat?(req: ChatRequest, listener: ChatResponseListener): void;

  /**
   * List all chat conversations for the current user and the given change.
   */
  listChatConversations?(change: ChangeInfo): Promise<Conversation[]>;

  /**
   * Retrieve the details of a single conversation.
   */
  getChatConversation?(
    change: ChangeInfo,
    conversation_id: string
  ): Promise<ConversationTurn[]>;

  /**
   * Get available models for the given change.
   */
  getModels?(change: ChangeInfo): Promise<Models>;

  /**
   * Get available actions for the given change.
   */
  getActions?(change: ChangeInfo): Promise<Actions>;

  /**
   * Get the list of context item types that the provider supports.
   */
  getContextItemTypes?(): Promise<ContextItemType[]>;
}
