/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// This file adds some simple checks to match internal Google rules.
// Internally at Google it has different a implementation.

import {safeLocation} from 'safevalues/dom';

export function setHref(loc: Location, url: string) {
  safeLocation.setHref(loc, url);
}

export function replace(loc: Location, url: string) {
  safeLocation.replace(loc, url);
}

export function assign(loc: Location, url: string) {
  safeLocation.assign(loc, url);
}
