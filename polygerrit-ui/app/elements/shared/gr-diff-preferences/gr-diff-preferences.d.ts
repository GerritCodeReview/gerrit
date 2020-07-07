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

import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';

export interface GrDiffPreferences {
  $: {
    contextSelect: HTMLElement;
  };
}

export class GrDiffPreferences extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  save(): Promise<void>;
}
