/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * This file defines the API of syntax-worker, which is a web worker for syntax
 * highlighting based on the HighlightJS library.
 *
 * Workers communicate via `postMessage(e)` and `onMessage(e)` where `e` is a
 * MessageEvent.
 *
 * SyntaxWorker expects incoming messages to be of type
 * `MessageEvent<SyntaxWorkerMessage>`. And outgoing messages will be of type
 * `MessageEvent<SyntaxWorkerResult>`.
 */

/** Type of incoming messages for SyntaxWorker. */
export enum SyntaxWorkerMessageType {
  INIT,
  REQUEST,
}

/** Incoming message for SyntaxWorker. */
export interface SyntaxWorkerMessage {
  type: SyntaxWorkerMessageType;
}

/**
 * Requests the worker to import the HighlightJS lib from the given URL and
 * initializes and configures it. Has to be called once before you can send
 * a SyntaxWorkerRequest message.
 */
export interface SyntaxWorkerInit extends SyntaxWorkerMessage {
  type: SyntaxWorkerMessageType.INIT;
  url: string;
}

export function isInit(
  x: SyntaxWorkerMessage | undefined
): x is SyntaxWorkerInit {
  return !!x && x.type === SyntaxWorkerMessageType.INIT;
}

/**
 * Requests the worker to highlight the given code. The worker must have been
 * initialized before.
 */
export interface SyntaxWorkerRequest extends SyntaxWorkerMessage {
  type: SyntaxWorkerMessageType.REQUEST;
  language: string;
  code: string;
}

export function isRequest(
  x: SyntaxWorkerMessage | undefined
): x is SyntaxWorkerRequest {
  return !!x && x.type === SyntaxWorkerMessageType.REQUEST;
}

/** Type of outgoing messages of SyntaxWorker. */
export interface SyntaxWorkerResult {
  /** Unset or undefined means "success". */
  error?: string;
  /**
   * Returned by SyntaxWorkerRequest calls. Every line gets its own array of
   * ranges. `ranges[0]` are the ranges for line 1. Every line has an array,
   * which may be empty. All ranges are guaranteed to be closed.
   */
  ranges?: SyntaxLayerLine[];
}

/** Ranges for one line. */
export interface SyntaxLayerLine {
  ranges: SyntaxLayerRange[];
}

/** Can be used for `length` in SyntaxLayerRange. */
export const UNCLOSED = -1;

/** Range of characters in a line to be syntax highlighted. */
export interface SyntaxLayerRange {
  /** 1-based inclusive. */
  start: number;
  /** Can only be UNCLOSED during processing. */
  length: number;
  /** HighlightJS specific names, e.g. 'literal'. */
  className: string;
}
