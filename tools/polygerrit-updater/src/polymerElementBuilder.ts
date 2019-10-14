import {LegacyPolymerComponent, LegacyReservedDeclarations, OrdinaryGetAccessors, OrdinaryMethods, OrdinaryPropertyAssignments, OrdinaryShorthandProperties} from './polymerComponentParser';
import {PolymerClassBuilder} from './polymerClassBuilder';
import {ExpressionWithTypeArguments, SyntaxKind} from 'typescript';
import * as ts from 'typescript';
import * as tsUtils from './tsUtils';
import {LifecycleMethod} from './lifecycleMethodsBuilder';

export interface ClassBasedPolymerElement {
  classDeclaration: ts.ClassDeclaration;
  componentRegistration: ts.ExpressionStatement;
  eventsComments: string[];
}

export class PolymerElementBuilder {
  private readonly reservedDeclarations: LegacyReservedDeclarations;
  private readonly classBuilder: PolymerClassBuilder;
  private mixins: ExpressionWithTypeArguments | null;

  public constructor(private readonly legacyComponent: LegacyPolymerComponent, className: string) {
    this.reservedDeclarations = legacyComponent.componentSettings.reservedDeclarations;
    this.classBuilder = new PolymerClassBuilder(className);
    this.mixins = null;
  }

  public addIsAccessor(tagName: string) {
    this.classBuilder.addIsAccessor(this.createIsAccessor(tagName));
  }

  public addPolymerPropertiesAccessor(legacyProperties: ts.ObjectLiteralExpression) {
    const returnStatement = ts.createReturn(legacyProperties);
    const block = ts.createBlock([returnStatement]);
    const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(SyntaxKind.StaticKeyword)], "properties", [], undefined, block);

    this.classBuilder.addPolymerPropertiesAccessor(legacyProperties.pos, propertiesAccessor)
  }

  public addPolymerPropertiesObservers(legacyObservers: ts.ArrayLiteralExpression) {
    const returnStatement = ts.createReturn(legacyObservers);
    const block = ts.createBlock([returnStatement]);
    const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(SyntaxKind.StaticKeyword)], "observers", [], undefined, block);

    this.classBuilder.addPolymerObserversAccessor(legacyObservers.pos, propertiesAccessor)
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
      const propertyName = tsUtils.assertNodeKind(property.name, ts.SyntaxKind.Identifier) as ts.Identifier;
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
    this.mixins = ts.createExpressionWithTypeArguments([], ts.createCall(tsUtils.createNameExpression(name), [], fullMixinArguments.length > 0 ? fullMixinArguments : undefined));
  }

  public build(): ClassBasedPolymerElement {
    if(this.mixins) {
      this.classBuilder.setBaseType(this.mixins);
    }
    const className = this.classBuilder.className;
    const callExpression = ts.createCall(ts.createPropertyAccess(ts.createIdentifier("customElements"), "define"), undefined, [ts.createPropertyAccess(ts.createIdentifier(className), "is"), ts.createIdentifier(className)]);
    return {
      classDeclaration: this.classBuilder.build()/* ts.createClassDeclaration(undefined, undefined, className, undefined, heritageClauses, members.map(m => m.member))*/,
      componentRegistration: ts.createExpressionStatement(callExpression),
      eventsComments: this.legacyComponent.componentSettings.eventsComments,
    };
  }

  private createIsAccessor(tagName: string): ts.GetAccessorDeclaration {
    const returnStatement = ts.createReturn(ts.createStringLiteral(tagName));
    const block = ts.createBlock([returnStatement]);
    const accessor = ts.createGetAccessor([], [ts.createModifier(SyntaxKind.StaticKeyword)], "is", [], undefined, block);
    return tsUtils.addReplacableCommentAfterNode(accessor, "eventsComments");
  }

  public addLifecycleMethods(newLifecycleMethods: LifecycleMethod[]) {
    for(const lifecycleMethod of newLifecycleMethods) {
      this.classBuilder.addLifecycleMethod(lifecycleMethod.name, -1, lifecycleMethod.method);
    }
  }
}