/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// DO NOT EXPORT ANYTHING FROM THIS FILE!
// The rollupjs re-exports everything from the entry-point file. So, we
// can't use gr-app.ts as an entry point, because it has some exports.
// This file is used as an entry point for the whole application; as a result,
// the generated bundle doesn't contain any exports.
import './gr-app';
