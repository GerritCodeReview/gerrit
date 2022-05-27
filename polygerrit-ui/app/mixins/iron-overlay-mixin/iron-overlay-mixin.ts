/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
  // eslint-disable-next-line
  mixinBehaviors([IronOverlayBehavior], superClass) as any;
