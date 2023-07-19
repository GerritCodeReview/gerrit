/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// A finalizable object has a single method `finalize` that is called when
// the object is no longer needed and should clean itself up.
export interface Finalizable {
  finalize(): void;
}
