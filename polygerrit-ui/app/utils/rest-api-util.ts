/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Error callback that throws an error.
 *
 * Pass into REST API methods as errFn to make the returned Promises reject on
 * error.
 *
 * If error is provided, it's thrown.
 * Otherwise if response with error is provided the promise that will throw an
 * error is returned.
 */
export function throwingErrorCallback(
  response?: Response | null,
  err?: Error
): void | Promise<void> {
  if (err) throw err;
  if (!response) return;

  return response.text().then(errorText => {
    let message = `Error ${response.status}`;
    if (response.statusText) {
      message += ` (${response.statusText})`;
    }
    if (errorText) {
      message += `: ${errorText}`;
    }
    throw new Error(message);
  });
}
