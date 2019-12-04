// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview This file is a backwards-compatibility shim. Before Polymer
 *     converted to ES Modules, it wrote its API out onto the global Polymer
 *     object. The *_bridge.js files (like this one) maintain compatibility
 *     with that API.
 */
 
import '../polymer/lib/utils/boot_bridge.js';
import {IronFitBehavior} from '@polymer/iron-fit-behavior/iron-fit-behavior.js';

/** @const */
Polymer.IronFitBehavior = IronFitBehavior;

goog.declareModuleId('HtmlImportsNamespace.IronFitBehavior.IronFitBehavior');

