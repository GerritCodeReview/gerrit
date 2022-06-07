/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Returns a random integer between `from` and `to`, both included.
 * So getRandomInt(0, 2) returns 0, 1, or 2 each with probability 1/3.
 */
export function getRandomInt(from: number, to: number) {
  return Math.floor(Math.random() * (to + 1 - from) + from);
}
