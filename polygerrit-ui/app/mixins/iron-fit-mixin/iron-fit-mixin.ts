/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {IronFitBehavior} from '@polymer/iron-fit-behavior/iron-fit-behavior.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';

// In .d.ts, the mixinBehaviors clears all type information about superClass.
// As a workaround, we define IronFitMixin manually here and after conversion
// to typescript we can define correct typing here as well.
export const IronFitMixin = superClass => mixinBehaviors(
    [IronFitBehavior], superClass);
