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
export type HljsWorkerMessage = HljsInit | HljsHighlight;

/**
 * Requests the worker to import the HighlightJS lib from the given URL and
 * initializes and configures it. Has to be called once before you can send
 * a HljsHighlight message.
 */
export interface HljsInit {
  url: string;
}

export function isInit(x: HljsWorkerMessage | undefined): x is HljsInit {
  return !!x && !!(x as HljsInit).url;
}

/**
 * Requests the worker to highlight the given code. The worker must have been
 * initialized before.
 */
export interface HljsHighlight {
  language: string;
  code: string;
}

export function isHighlight(
  x: HljsWorkerMessage | undefined
): x is HljsHighlight {
  return !!x && !!(x as HljsHighlight).code;
}

/** Type of outgoing messages of HljsWorker. */
export interface HljsWorkerResult {
  /** Unset or undefined means "success". */
  error?: string;
  /**
   * Returned by HljsHighlight calls. Every line gets its own array of ranges.
   * `ranges[0]` are the ranges for line 1. Every line has an array, which may
   * be empty. All ranges are guaranteed to be closed (i.e. length >= 0).
   */
  ranges?: SyntaxLayerRange[][];
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
