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

import {LegacyLifecycleMethodsArray, LegacyPolymerComponent} from './polymerComponentParser';
import {LifecycleMethodsBuilder} from './lifecycleMethodsBuilder';
import {ClassBasedPolymerElement, PolymerElementBuilder} from './polymerElementBuilder';
import * as codeUtils from '../utils/codeUtils';
import * as ts from 'typescript';

export class PolymerFuncToClassBasedConverter {
  public static convert(component: LegacyPolymerComponent): ClassBasedPolymerElement {
    const legacySettings = component.componentSettings;
    const reservedDeclarations = legacySettings.reservedDeclarations;

    if(!reservedDeclarations.is) {
      throw new Error("Legacy component doesn't have 'is' property");
    }
    const className = this.generateClassNameFromTagName(reservedDeclarations.is.data);
    const updater = new PolymerElementBuilder(component, className);
    updater.addIsAccessor(reservedDeclarations.is.data);

    if(reservedDeclarations.properties) {
      updater.addPolymerPropertiesAccessor(reservedDeclarations.properties);
    }

    updater.addMixin("Polymer.Element");
    updater.addMixin("Polymer.LegacyElementMixin");
    updater.addMixin("Polymer.GestureEventListeners");

    if(reservedDeclarations._legacyUndefinedCheck) {
      updater.addMixin("Polymer.LegacyDataMixin");
    }

    if(reservedDeclarations.behaviors) {
      updater.addMixin("Polymer.mixinBehaviors", [reservedDeclarations.behaviors.data]);
      const mixinNames = this.getMixinNamesFromBehaviors(reservedDeclarations.behaviors.data);
      const jsDocLines = mixinNames.map(mixinName => {
        return `@appliesMixin ${mixinName}`;
      });
      updater.addClassJSDocComments(jsDocLines);
    }

    if(reservedDeclarations.observers) {
      updater.addPolymerPropertiesObservers(reservedDeclarations.observers.data);
    }

    if(reservedDeclarations.keyBindings) {
      updater.addKeyBindings(reservedDeclarations.keyBindings.data);
    }


    const lifecycleBuilder = new LifecycleMethodsBuilder();
    if (reservedDeclarations.listeners) {
      lifecycleBuilder.addListeners(reservedDeclarations.listeners.data, legacySettings.ordinaryMethods);
    }

    if (reservedDeclarations.hostAttributes) {
      lifecycleBuilder.addHostAttributes(reservedDeclarations.hostAttributes.data);
    }

    for(const name of LegacyLifecycleMethodsArray) {
      const existingMethod = legacySettings.lifecycleMethods.get(name);
      if(existingMethod) {
        lifecycleBuilder.addLegacyLifecycleMethod(name, existingMethod)
      }
    }

    const newLifecycleMethods = lifecycleBuilder.buildNewMethods();
    updater.addLifecycleMethods(newLifecycleMethods);


    updater.addOrdinaryMethods(legacySettings.ordinaryMethods);
    updater.addOrdinaryGetAccessors(legacySettings.ordinaryGetAccessors);
    updater.addOrdinaryShorthandProperties(legacySettings.ordinaryShorthandProperties);
    updater.addOrdinaryPropertyAssignments(legacySettings.ordinaryPropertyAssignments);

    return updater.build();
  }

  private static generateClassNameFromTagName(tagName: string) {
    let result = "";
    let nextUppercase = true;
    for(const ch of tagName) {
      if (ch === '-') {
        nextUppercase = true;
        continue;
      }
      result += nextUppercase ? ch.toUpperCase() : ch;
      nextUppercase = false;
    }
    return result;
  }

  private static getMixinNamesFromBehaviors(behaviors: ts.ArrayLiteralExpression): string[] {
    return behaviors.elements.map((expression) => {
      const propertyAccessExpression = codeUtils.assertNodeKind(expression, ts.SyntaxKind.PropertyAccessExpression) as ts.PropertyAccessExpression;
      const namespaceName = codeUtils.assertNodeKind(propertyAccessExpression.expression, ts.SyntaxKind.Identifier) as ts.Identifier;
      const behaviorName = propertyAccessExpression.name;
      if(namespaceName.text === 'Gerrit') {
        let behaviorNameText = behaviorName.text;
        const suffix = 'Behavior';
        if(behaviorNameText.endsWith(suffix)) {
          behaviorNameText =
              behaviorNameText.substr(0, behaviorNameText.length - suffix.length);
        }
        const mixinName = behaviorNameText + 'Mixin';
        return `${namespaceName.text}.${mixinName}`
      } else if(namespaceName.text === 'Polymer') {
        let behaviorNameText = behaviorName.text;
        if(behaviorNameText === "IronFitBehavior") {
          return "Polymer.IronFitMixin";
        } else if(behaviorNameText === "IronOverlayBehavior") {
          return "";
        }
        throw new Error(`Unsupported behavior: ${propertyAccessExpression.getText()}`);
      }
      throw new Error(`Unsupported behavior name ${expression.getFullText()}`)
    }).filter(name => name.length > 0);
  }
}
