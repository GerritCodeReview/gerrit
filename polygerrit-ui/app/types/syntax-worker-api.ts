/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * This file defines the API of hljs-worker, which is a web worker for syntax
 * highlighting based on the HighlightJS library.
 *
 * Workers communicate via `postMessage(e)` and `onMessage(e)` where `e` is a
 * MessageEvent.
 *
 * HljsWorker expects incoming messages to be of type
 * `MessageEvent<HljsWorkerMessage>`. And outgoing messages will be of type
 * `MessageEvent<HljsWorkerResult>`.
 */

/** Type of incoming messages for HljsWorker. */
export enum SyntaxWorkerMessageType {
  INIT,
  REQUEST,
}

/** Incoming message for HljsWorker. */
export interface SyntaxWorkerMessage {
  type: SyntaxWorkerMessageType;
}

/**
 * Requests the worker to import the HighlightJS lib from the given URL and
 * initializes and configures it. Has to be called once before you can send
 * a HljsHighlight message.
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

/** Type of outgoing messages of HljsWorker. */
export interface SyntaxWorkerResult {
  /** Unset or undefined means "success". */
  error?: string;
  /**
   * Returned by HljsHighlight calls. Every line gets its own array of ranges.
   * `ranges[0]` are the ranges for line 1. Every line has an array, which may
   * be empty. All ranges are guaranteed to be closed (i.e. length >= 0).
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
