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

import {IronOverlayBehavior} from '@polymer/iron-overlay-behavior/iron-overlay-behavior';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {Constructor} from '../../utils/common-util';

// The mixinBehaviors clears all type information about superClass.
// As a workaround, we define IronOverlayMixin with correct type.
// Due to the following issues:
// https://github.com/microsoft/TypeScript/issues/15870
// https://github.com/microsoft/TypeScript/issues/9944
// we have to import IronOverlayBehavior in the same file where IronOverlayMixin
// is used. To ensure that this import can't be avoided, the second parameter
// is added. Usage example:
// class Element extends IronOverlayMixin(PolymerElement, IronOverlayBehavior as IronOverlayBehavior)
// The code 'IronOverlayBehavior as IronOverlayBehavior' required, because
// IronOverlayBehavior defined as an object, not as IronOverlayBehavior instance.
export const IronOverlayMixin = <T extends Constructor<PolymerElement>>(
  superClass: T,
  _: IronOverlayBehavior
): T & Constructor<IronOverlayBehavior> =>
  // TODO(TS): mixinBehaviors in some lib is returning: `new () => T`
  // instead which will fail the type check due to missing
  // IronOverlayBehavior interface
  mixinBehaviors([IronOverlayBehavior], superClass) as any;
