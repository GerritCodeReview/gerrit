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

import {IronFitBehavior} from '@polymer/iron-fit-behavior/iron-fit-behavior';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {Constructor} from '../../utils/common-util';

// https://github.com/microsoft/TypeScript/issues/15870
// https://github.com/microsoft/TypeScript/issues/9944

// The mixinBehaviors clears all type information about superClass.
// As a workaround, we define IronFitMixin with correct type.
export const IronFitMixin = <T extends Constructor<PolymerElement>>(
  superClass: T,
  _: IronFitBehavior
): T & Constructor<IronFitBehavior> =>
  // TODO(TS): mixinBehaviors in some lib is returning: `new () => T` instead
  // which will fail the type check due to missing IronFitBehavior interface
  mixinBehaviors([IronFitBehavior], superClass) as any;
