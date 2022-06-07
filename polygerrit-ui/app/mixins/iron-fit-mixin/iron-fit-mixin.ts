/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {IronFitBehavior} from '@polymer/iron-fit-behavior/iron-fit-behavior';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {Constructor} from '../../utils/common-util';

// The mixinBehaviors clears all type information about superClass.
// As a workaround, we define IronFitMixin with correct type.
// Due to the following issues:
// https://github.com/microsoft/TypeScript/issues/15870
// https://github.com/microsoft/TypeScript/issues/9944
// we have to import IronFitBehavior in the same file where IronFitMixin
// is used. To ensure that this import can't be avoided, the second parameter
// is added. Usage example:
// class Element extends IronFitMixin(PolymerElement, IronFitBehavior as IronFitBehavior)
// The code 'IronFitBehavior as IronFitBehavior' required, because IronFitBehavior
// defined as an object, not as IronFitBehavior instance.

export const IronFitMixin = <T extends Constructor<PolymerElement>>(
  superClass: T,
  _: IronFitBehavior
): T & Constructor<IronFitBehavior> =>
  // TODO(TS): mixinBehaviors in some lib is returning: `new () => T` instead
  // which will fail the type check due to missing IronFitBehavior interface
  // eslint-disable-next-line
  mixinBehaviors([IronFitBehavior], superClass) as any;
