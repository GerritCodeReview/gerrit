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

import {DataWithComments, LegacyPolymerComponent, LegacyReservedDeclarations, OrdinaryGetAccessors, OrdinaryMethods, OrdinaryPropertyAssignments, OrdinaryShorthandProperties} from './polymerComponentParser';
import * as ts from 'typescript';
import * as codeUtils from '../utils/codeUtils';
import {LifecycleMethod} from './lifecycleMethodsBuilder';
import {PolymerClassBuilder} from '../utils/polymerClassBuilder';
import {SyntaxKind} from 'typescript';

export interface ClassBasedPolymerElement {
  classDeclaration: ts.ClassDeclaration;
  componentRegistration: ts.ExpressionStatement;
  eventsComments: string[];
  generatedComments: string[];
}

export class PolymerElementBuilder {
  private readonly reservedDeclarations: LegacyReservedDeclarations;
  private readonly classBuilder: PolymerClassBuilder;
  private mixins: ts.ExpressionWithTypeArguments | null;

  public constructor(private readonly legacyComponent: LegacyPolymerComponent, className: string) {
    this.reservedDeclarations = legacyComponent.componentSettings.reservedDeclarations;
    this.classBuilder = new PolymerClassBuilder(className);
    this.mixins = null;
  }

  public addIsAccessor(tagName: string) {
    this.classBuilder.addIsAccessor(this.createIsAccessor(tagName));
  }

  public addPolymerPropertiesAccessor(legacyProperties: DataWithComments<ts.ObjectLiteralExpression>) {
    const returnStatement = ts.createReturn(legacyProperties.data);
    const block = ts.createBlock([returnStatement]);
    let propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(ts.SyntaxKind.StaticKeyword)], "properties", [], undefined, block);
    if(legacyProperties.leadingComments.length > 0) {
      propertiesAccessor = codeUtils.restoreLeadingComments(propertiesAccessor, legacyProperties.leadingComments);
    }
    this.classBuilder.addPolymerPropertiesAccessor(legacyProperties.data.pos, propertiesAccessor);
  }

  public addPolymerPropertiesObservers(legacyObservers: ts.ArrayLiteralExpression) {
    const returnStatement = ts.createReturn(legacyObservers);
    const block = ts.createBlock([returnStatement]);
    const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(ts.SyntaxKind.StaticKeyword)], "observers", [], undefined, block);

    this.classBuilder.addPolymerObserversAccessor(legacyObservers.pos, propertiesAccessor);
  }

  public addKeyBindings(keyBindings: ts.ObjectLiteralExpression) {
    //In Polymer 2 keyBindings must be a property with get accessor
    const returnStatement = ts.createReturn(keyBindings);
    const block = ts.createBlock([returnStatement]);
    const keyBindingsAccessor = ts.createGetAccessor(undefined, [], "keyBindings", [], undefined, block);

    this.classBuilder.addGetAccessor(keyBindings.pos, keyBindingsAccessor);
  }
  public addOrdinaryMethods(ordinaryMethods: OrdinaryMethods) {
    for(const [name, method] of ordinaryMethods) {
      this.classBuilder.addMethod(method.pos, method);
    }
  }

  public addOrdinaryGetAccessors(ordinaryGetAccessors: OrdinaryGetAccessors) {
    for(const [name, accessor] of ordinaryGetAccessors) {
      this.classBuilder.addGetAccessor(accessor.pos, accessor);
    }
  }

  public addOrdinaryShorthandProperties(ordinaryShorthandProperties: OrdinaryShorthandProperties) {
    for (const [name, property] of ordinaryShorthandProperties) {
      this.classBuilder.addClassFieldInitializer(property.name, property.name);
    }
  }

  public addOrdinaryPropertyAssignments(ordinaryPropertyAssignments: OrdinaryPropertyAssignments) {
    for (const [name, property] of ordinaryPropertyAssignments) {
      const propertyName = codeUtils.assertNodeKind(property.name, ts.SyntaxKind.Identifier) as ts.Identifier;
      this.classBuilder.addClassFieldInitializer(propertyName, property.initializer);
    }
  }

  public addMixin(name: string, mixinArguments?: ts.Expression[]) {
    let fullMixinArguments: ts.Expression[] = [];
    if(mixinArguments) {
      fullMixinArguments.push(...mixinArguments);
    }
    if(this.mixins) {
      fullMixinArguments.push(this.mixins.expression);
    }
    if(fullMixinArguments.length > 0) {
      this.mixins = ts.createExpressionWithTypeArguments([], ts.createCall(codeUtils.createNameExpression(name), [], fullMixinArguments.length > 0 ? fullMixinArguments : undefined));
    }
    else {
      this.mixins = ts.createExpressionWithTypeArguments([], codeUtils.createNameExpression(name));
    }
  }

  public addClassJSDocComments(lines: string[]) {
    this.classBuilder.addClassJSDocComments(lines);
  }

  public build(): ClassBasedPolymerElement {
    if(this.mixins) {
      this.classBuilder.setBaseType(this.mixins);
    }
    const className = this.classBuilder.className;
    const callExpression = ts.createCall(ts.createPropertyAccess(ts.createIdentifier("customElements"), "define"), undefined, [ts.createPropertyAccess(ts.createIdentifier(className), "is"), ts.createIdentifier(className)]);
    const classBuilderResult = this.classBuilder.build();
    return {
      classDeclaration: classBuilderResult.classDeclaration,
      generatedComments: classBuilderResult.generatedComments,
      componentRegistration: ts.createExpressionStatement(callExpression),
      eventsComments: this.legacyComponent.componentSettings.eventsComments,
    };
  }

  private createIsAccessor(tagName: string): ts.GetAccessorDeclaration {
    const returnStatement = ts.createReturn(ts.createStringLiteral(tagName));
    const block = ts.createBlock([returnStatement]);
    const accessor = ts.createGetAccessor([], [ts.createModifier(ts.SyntaxKind.StaticKeyword)], "is", [], undefined, block);
    return codeUtils.addReplacableCommentAfterNode(accessor, "eventsComments");
  }

  public addLifecycleMethods(newLifecycleMethods: LifecycleMethod[]) {
    for(const lifecycleMethod of newLifecycleMethods) {
      this.classBuilder.addLifecycleMethod(lifecycleMethod.name, lifecycleMethod.originalPos, lifecycleMethod.method);
    }
  }
}
