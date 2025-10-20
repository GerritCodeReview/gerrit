/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo} from './rest-api';

export declare interface AiCodeReviewPluginApi {
  /**
   * Must only be called once. You cannot register twice (throws an error).
   * You cannot unregister.
   */
  register(provider: AiCodeReviewProvider): void;
}

export declare interface ChatRequest {
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
  conversationId: string;
  /**
   * Plugins can choose what context they want to derive from the change and
   * send along to their backends. `changeInfo` contains broadly all the
   * information about the change, and the plugin can also make additional
   * requests to the REST API (e.g. getting patch content) by using properties
   * from the change info.
   */
  changeInfo: ChangeInfo;
}

/**
 * The chat response may come in as a stream, so instead of just one response
 * object this listener will get multiple calls until the response is completed.
 */
export declare interface ChatResponseListener {
  /**
   * Emits one piece of a streaming text response from the backend, to be
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

export declare interface AiCodeReviewProvider {
  /**
   * If a AiCodeReviewProvider provider is registered that implements this
   * method, then Gerrit will offer a side panel for the user to have an AI
   * Chat conversation. Each chat() call is one turn of such a conversation.
   */
  chat?(req: ChatRequest, listener: ChatResponseListener): void;
}
