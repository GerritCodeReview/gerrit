/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// DO NOT EXPORT ANYTHING FROM THIS FILE!
// The rollupjs re-exports everything from the entry-point file. So, we
// can't use gr-app.ts as an entry point, because it has some exports.
// This file is used as an entry point for the whole application; as a result,
// the generated bundle doesn't contain any exports.
import './gr-app';
