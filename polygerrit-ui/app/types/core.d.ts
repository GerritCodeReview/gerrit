/**
 * @fileoverview Core types for Gerrit.
 *
 * This is a more focused set than common.ts and should only contain types used
 * by both gr-diff and parts of Gerrit that do not yet depend on gr-diff. 
 * 
 * This API is used by other apps embedding gr-diff and any breaking changes
 * should be discussed with the Gerrit core team and properly versioned.
 *
 * Should only contain types, no values, so that other apps using gr-diff can
 * use this solely to type check and generate externs for their separate ts
 * bundles.
 *
 * Should declare all types, to avoid renaming breaking multi-bundle setups.
 *
 * Enums should be converted to union types to avoid values in this file.
 *
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

export declare type PatchSetNum = BrandType<'PARENT' | 'edit' | number, '_patchSet'>;

/**
 * Defines a patch ranges. Used as input for gr-rest-api-interface methods,
 * doesn't exist in Rest API
 */
export declare interface PatchRange {
    patchNum: PatchSetNum;
    basePatchNum: PatchSetNum;
  }


  