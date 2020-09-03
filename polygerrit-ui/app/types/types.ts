/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import {Side} from '../constants/constants';

export function notUndefined<T>(x: T | undefined): x is T {
  return x !== undefined;
}

export interface CoverageRange {
  type: CoverageType;
  side: Side;
  code_range: {end_line: number; start_line: number};
}

export enum CoverageType {
  /**
   * start_character and end_character of the range will be ignored for this
   * type.
   */
  COVERED = 'COVERED',
  /**
   * start_character and end_character of the range will be ignored for this
   * type.
   */
  NOT_COVERED = 'NOT_COVERED',
  PARTIALLY_COVERED = 'PARTIALLY_COVERED',
  /**
   * You don't have to use this. If there is no coverage information for a
   * range, then it implicitly means NOT_INSTRUMENTED. start_character and
   * end_character of the range will be ignored for this type.
   */
  NOT_INSTRUMENTED = 'NOT_INSTRUMENTED',
}

/**
 * If Polymer would have exported DomApiNative from its dom.js utility, then we
 * would probably not need this type. We just use it for casting the return
 * value of dom(element).
 */
export interface PolymerDomWrapper {
  getOwnerRoot(): Node & OwnerRoot;
}
export interface OwnerRoot {
  host?: HTMLElement;
}

/**
 * Event type for an event fired by Polymer for an element generated from a
 * dom-repeat template.
 */
export interface PolymerDomRepeatEvent<TModel = unknown> extends Event {
  model: PolymerDomRepeatEventModel<TModel>;
}

/**
 * Event type for an event fired by Polymer for an element generated from a
 * dom-repeat template.
 */
export interface PolymerDomRepeatCustomEvent<
  TModel = unknown,
  TDetail = unknown
> extends CustomEvent<TDetail> {
  model: PolymerDomRepeatEventModel<TModel>;
}

/**
 * Model containing additional information about the dom-repeat element
 * that fired an event.
 *
 * Note: This interface is valid only if both dom-repeat properties 'as' and
 * 'indexAs' have default values ('item' and 'index' correspondingly)
 */
export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
}

export interface HighlightJS {
  configure: (options: {classPrefix: string}) => void;
}
