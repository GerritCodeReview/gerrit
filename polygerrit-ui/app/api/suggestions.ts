/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  ChangeInfo,
  CommentRange,
  FixSuggestionInfo,
  RevisionPatchSetNum,
} from './rest-api';

export declare interface SuggestionsPluginApi {
  /**
   * Must only be called once. You cannot register twice. You cannot unregister.
   */
  register(provider: SuggestionsProvider): void;
}

export declare interface ChatRequest {
  /**
   * The prompt to be sent to the LLM.
   */
  prompt: string;
  /**
   * UUID of the conversation. To start a new conversation, the caller should
   * generate a new UUID and set turn_index to 0.
   * To continue an existing conversation, the caller should provide the UUID of
   * the conversation.
   */
  conversationId: string;
  /**
   * The 0-based turn index of the request. The caller should set it based on
   * the history of the conversation. It should be one more than the last known
   * turn. For new conversations, it should be 0.
   */
  turnIndex: number;
  /**
   * Plugins can choose what context they want to derive from the change and
   * send along to their backends. `changeInfo` contains broadly all the
   * information about the change, and the plugin can also make additional
   * requests to the REST API (e.g. getting patch content) by using properties
   * from the change info.
   */
  changeInfo: ChangeInfo;
}

export declare interface ChatResponseListener {
  /**
   * Emits one piece a streaming text response from the backend, to be
   * interpreted as markdown by the web app and to be shown as is to the user.
   */
  emitText(text: string): void;
  /**
   * Emits an error message, indicating that the turn has failed. Will be
   * immediately followed by a done() call.
   */
  emitError(error: string): void;
  /**
   * The turn is completed. The listener can be discarded.
   */
  done(): void;
}

export declare interface SuggestCodeRequest {
  prompt: string;
  changeInfo: ChangeInfo;
  patchsetNumber: RevisionPatchSetNum;
  filePath: string;
  range?: CommentRange;
  lineNumber?: number;
}

export declare interface AutocompleteCommentRequest {
  id: string;
  commentText: string;
  changeInfo: ChangeInfo;
  patchsetNumber: RevisionPatchSetNum;
  filePath: string;
  range?: CommentRange;
  lineNumber?: number;
}

export declare interface SuggestionsProvider {
  autocompleteComment?(
    req: AutocompleteCommentRequest
  ): Promise<AutocompleteCommentResponse>;

  /**
   * If a suggestion provider is registered that implements this method, then
   * Gerrit will offer a side panel for the user to have an AI Chat
   * conversation. Each chat() call is one turn of such a conversation.
   */
  chat?(req: AutocompleteCommentRequest, listener: ChatResponseListener): void;

  /**
   * Gerrit calls these methods when ...
   * - ... user types a comment draft
   */
  suggestCode?(commentData: SuggestCodeRequest): Promise<SuggestCodeResponse>;
  suggestFix?(commentData: SuggestCodeRequest): Promise<SuggestedFixResponse>;
  /**
   * Gets the title to display on the fix suggestion preview.
   *
   * @param fix_suggestions A list of suggested fixes.
   * @return The title string or empty to use the default title.
   */
  getFixSuggestionTitle?(fix_suggestions?: FixSuggestionInfo[]): string;
  /**
   * Gets a link to documentation for icon help next to title
   *
   * @param fix_suggestions A list of suggested fixes.
   * @return The documentation URL string or empty to use the default link to
   * gerrit documentation about fix suggestions.
   */
  getDocumentationLink?(fix_suggestions?: FixSuggestionInfo[]): string;
  /**
   * List of supported file extensions. If undefined, all file extensions supported.
   */
  supportedFileExtensions?: string[];
  /**
   * Sends feedback on autocompletion suggestions.
   * This method allows the plugin to report which suggestions were accepted or
   * rejected by the user, which can be used for improving future suggestions.
   */
  sendAutocompleteFeedback?(feedbackEntries: SuggestionFeedbackEntry[]): void;
  /**
   * Sends feedback on 'suggest fix' (code repair) suggestions.
   * This method allows the plugin to report which suggestions were accepted or
   * rejected by the user, which can be used for improving future suggestions.
   *
   */
  sendSuggestFixFeedback?(feedbackEntries: SuggestionFeedbackEntry[]): void;
}

/**
 * Represents a 128-bit unique identifier for a suggestion, used for tracking feedback.
 * The ID is split into two 64-bit BigInt components.
 */
export declare interface Feedback {
  /** The least significant 64 bits of the 128-bit identifier. */
  id1: bigint;
  /** The most significant 64 bits of the 128-bit identifier. */
  id2: bigint;
}

export declare interface SuggestionFeedbackEntry {
  /** The unique identifier for the suggestion. */
  feedbackId: Feedback;
  /** True if the suggestion was accepted by the user, false otherwise. */
  accepted: boolean;
}

export declare interface AutocompleteCommentResponse {
  responseCode: ResponseCode;
  completion?: string;
  modelVersion?: string;
  outcome?: number;
  feedback?: Feedback;
}

export declare interface SuggestCodeResponse {
  responseCode: ResponseCode;
  suggestions: Suggestion[];
}

export declare interface SuggestedFixResponse {
  responseCode: ResponseCode;
  fix_suggestions: FixSuggestionInfo[];
}

export declare interface Suggestion {
  replacement: string;
  newRange?: CommentRange;
}

export enum ResponseCode {
  OK = 'OK',
  NO_SUGGESTION = 'NO_SUGGESTION',
  OUT_OF_RANGE = 'OUT_OF_RANGE',
  ERROR = 'ERROR',
  FILE_TOO_LARGE = 'FILE_TOO_LARGE',
  INVALID_RANGE = 'INVALID_RANGE',
  UNSUPPORTED_FILE_TYPE = 'UNSUPPORTED_FILE_TYPE',
  USER_QUOTA_EXCEEDED = 'USER_QUOTA_EXCEEDED',
  OK_LOW_CONFIDENCE = 'OK_LOW_CONFIDENCE',
}
