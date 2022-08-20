/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.codehaus.groovy.transform.stc;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.IntRange;
import groovy.lang.Tuple2;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import groovy.transform.TypeChecked;
import groovy.transform.TypeCheckingMode;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.ClosureSignatureConflictResolver;
import groovy.transform.stc.ClosureSignatureHint;
import org.apache.groovy.util.SystemUtil;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.GenericsType.GenericsTypeName;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.AttributeExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ClosureListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.FieldExpression;
import org.codehaus.groovy.ast.expr.LambdaExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCall;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.MethodReferenceExpression;
import org.codehaus.groovy.ast.expr.NotExpression;
import org.codehaus.groovy.ast.expr.PostfixExpression;
import org.codehaus.groovy.ast.expr.PrefixExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.RangeExpression;
import org.codehaus.groovy.ast.expr.SpreadExpression;
import org.codehaus.groovy.ast.expr.SpreadMapExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.UnaryMinusExpression;
import org.codehaus.groovy.ast.expr.UnaryPlusExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.ast.tools.WideningCategories;
import org.codehaus.groovy.classgen.ReturnAdder;
import org.codehaus.groovy.classgen.asm.InvocationWriter;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.ResolveVisitor;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.TokenUtil;
import org.codehaus.groovy.transform.StaticTypesTransformation;
import org.codehaus.groovy.transform.trait.Traits;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static org.apache.groovy.util.BeanUtils.capitalize;
import static org.apache.groovy.util.BeanUtils.decapitalize;
import static org.codehaus.groovy.ast.ClassHelper.AUTOCLOSEABLE_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.BigDecimal_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.BigInteger_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Byte_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.ClassHelper.CLOSURE_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Character_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Double_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Float_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Integer_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Iterator_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.LIST_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Long_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Number_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.PATTERN_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.RANGE_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.SET_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.STREAM_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.Short_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.VOID_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.boolean_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.byte_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.char_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.double_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.dynamicType;
import static org.codehaus.groovy.ast.ClassHelper.findSAM;
import static org.codehaus.groovy.ast.ClassHelper.float_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.getNextSuperClass;
import static org.codehaus.groovy.ast.ClassHelper.getUnwrapper;
import static org.codehaus.groovy.ast.ClassHelper.getWrapper;
import static org.codehaus.groovy.ast.ClassHelper.int_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.isBigDecimalType;
import static org.codehaus.groovy.ast.ClassHelper.isBigIntegerType;
import static org.codehaus.groovy.ast.ClassHelper.isClassType;
import static org.codehaus.groovy.ast.ClassHelper.isDynamicTyped;
import static org.codehaus.groovy.ast.ClassHelper.isFunctionalInterface;
import static org.codehaus.groovy.ast.ClassHelper.isGStringType;
import static org.codehaus.groovy.ast.ClassHelper.isNumberType;
import static org.codehaus.groovy.ast.ClassHelper.isObjectType;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveBoolean;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveByte;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveChar;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveDouble;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveFloat;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveInt;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveLong;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveShort;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveType;
import static org.codehaus.groovy.ast.ClassHelper.isPrimitiveVoid;
import static org.codehaus.groovy.ast.ClassHelper.isSAMType;
import static org.codehaus.groovy.ast.ClassHelper.isStringType;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperByte;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperCharacter;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperDouble;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperFloat;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperInteger;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperLong;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperShort;
import static org.codehaus.groovy.ast.ClassHelper.long_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.short_TYPE;
import static org.codehaus.groovy.ast.tools.ClosureUtils.getParametersSafe;
import static org.codehaus.groovy.ast.tools.ClosureUtils.getResolveStrategyName;
import static org.codehaus.groovy.ast.tools.ClosureUtils.hasImplicitParameter;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.binX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.elvisX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.getGetterName;
import static org.codehaus.groovy.ast.tools.GeneralUtils.getSetterName;
import static org.codehaus.groovy.ast.tools.GeneralUtils.isOrImplements;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.thisPropX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafe0;
import static org.codehaus.groovy.ast.tools.WideningCategories.isBigDecCategory;
import static org.codehaus.groovy.ast.tools.WideningCategories.isBigIntCategory;
import static org.codehaus.groovy.ast.tools.WideningCategories.isDouble;
import static org.codehaus.groovy.ast.tools.WideningCategories.isDoubleCategory;
import static org.codehaus.groovy.ast.tools.WideningCategories.isFloat;
import static org.codehaus.groovy.ast.tools.WideningCategories.isFloatingCategory;
import static org.codehaus.groovy.ast.tools.WideningCategories.isIntCategory;
import static org.codehaus.groovy.ast.tools.WideningCategories.isLongCategory;
import static org.codehaus.groovy.ast.tools.WideningCategories.isNumberCategory;
import static org.codehaus.groovy.ast.tools.WideningCategories.lowestUpperBound;
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean;
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.init;
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.last;
import static org.codehaus.groovy.syntax.Types.ASSIGN;
import static org.codehaus.groovy.syntax.Types.COMPARE_EQUAL;
import static org.codehaus.groovy.syntax.Types.COMPARE_NOT_EQUAL;
import static org.codehaus.groovy.syntax.Types.COMPARE_NOT_IN;
import static org.codehaus.groovy.syntax.Types.COMPARE_NOT_INSTANCEOF;
import static org.codehaus.groovy.syntax.Types.COMPARE_TO;
import static org.codehaus.groovy.syntax.Types.DIVIDE;
import static org.codehaus.groovy.syntax.Types.DIVIDE_EQUAL;
import static org.codehaus.groovy.syntax.Types.ELVIS_EQUAL;
import static org.codehaus.groovy.syntax.Types.EQUAL;
import static org.codehaus.groovy.syntax.Types.FIND_REGEX;
import static org.codehaus.groovy.syntax.Types.INTDIV;
import static org.codehaus.groovy.syntax.Types.INTDIV_EQUAL;
import static org.codehaus.groovy.syntax.Types.KEYWORD_IN;
import static org.codehaus.groovy.syntax.Types.KEYWORD_INSTANCEOF;
import static org.codehaus.groovy.syntax.Types.MINUS_MINUS;
import static org.codehaus.groovy.syntax.Types.MOD;
import static org.codehaus.groovy.syntax.Types.MOD_EQUAL;
import static org.codehaus.groovy.syntax.Types.PLUS_PLUS;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.ArrayList_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.Collection_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.LinkedHashMap_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.LinkedHashSet_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.Matcher_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.NUMBER_OPS;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.UNKNOWN_PARAMETER_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.allParametersAndArgumentsMatchWithDefaultParams;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.applyGenericsConnections;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.applyGenericsContext;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.boundUnboundedWildcards;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.checkCompatibleAssignmentTypes;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.checkPossibleLossOfPrecision;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.chooseBestMethod;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.evaluateExpression;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.extractGenericsConnections;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.extractGenericsParameterMapOfThis;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.filterMethodsByVisibility;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.findDGMMethodsByNameAndArguments;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.findDGMMethodsForClassNode;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.findSetters;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.findTargetVariable;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.fullyResolve;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.fullyResolveType;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.getCombinedBoundType;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.getCombinedGenericsType;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.getGenericsWithoutArray;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.getOperationName;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.implementsInterfaceOrIsSubclassOf;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isArrayOp;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isAssignableTo;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isAssignment;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isBeingCompiled;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isBitOperator;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isBoolIntrinsicOp;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isClassClassNodeWrappingConcreteType;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isCompareToBoolean;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isGStringOrGStringStringLUB;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isOperationInGroup;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isParameterizedWithGStringOrGStringString;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isParameterizedWithString;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isPowerOperator;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isShiftOperation;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isTraitSelf;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isUsingGenericsOrIsArrayUsingGenerics;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isVargs;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isWildcardLeftHandSide;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.lastArgMatchesVarg;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.missesGenericsTypes;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.prettyPrintType;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.prettyPrintTypeName;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.resolveClassNodeGenerics;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.toMethodParametersString;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.typeCheckMethodArgumentWithGenerics;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.typeCheckMethodsWithGenerics;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.CLOSURE_ARGUMENTS;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.CONSTRUCTED_LAMBDA_EXPRESSION;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.DECLARATION_INFERRED_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.DELEGATION_METADATA;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.DIRECT_METHOD_CALL_TARGET;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.DYNAMIC_RESOLUTION;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.IMPLICIT_RECEIVER;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.INFERRED_RETURN_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.INFERRED_TYPE;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.PV_FIELDS_ACCESS;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.PV_FIELDS_MUTATION;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.PV_METHODS_ACCESS;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.READONLY_PROPERTY;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.SUPER_MOP_METHOD_REQUIRED;
import static org.codehaus.groovy.transform.stc.StaticTypesMarker.TYPE;

/**
 * The main class code visitor responsible for static type checking. It will perform various inspections like checking
 * assignment types, type inference, ... Eventually, class nodes may be annotated with inferred type information.
 */
public class StaticTypeCheckingVisitor extends ClassCodeVisitorSupport {

    private static final boolean DEBUG_GENERATED_CODE = SystemUtil.getBooleanSafe("groovy.stc.debug");
    private static final AtomicLong UNIQUE_LONG = new AtomicLong();

    protected static final Object ERROR_COLLECTOR = ErrorCollector.class;
    protected static final List<MethodNode> EMPTY_METHODNODE_LIST = Collections.emptyList();
    protected static final ClassNode TYPECHECKED_CLASSNODE = ClassHelper.make(TypeChecked.class);
    protected static final ClassNode[] TYPECHECKING_ANNOTATIONS = new ClassNode[]{TYPECHECKED_CLASSNODE};
    protected static final ClassNode TYPECHECKING_INFO_NODE = ClassHelper.make(TypeChecked.TypeCheckingInfo.class);
    protected static final ClassNode DGM_CLASSNODE = ClassHelper.make(DefaultGroovyMethods.class);
    protected static final int CURRENT_SIGNATURE_PROTOCOL_VERSION = 1;
    protected static final Expression CURRENT_SIGNATURE_PROTOCOL = new ConstantExpression(CURRENT_SIGNATURE_PROTOCOL_VERSION, true);
    protected static final MethodNode GET_DELEGATE = CLOSURE_TYPE.getGetterMethod("getDelegate");
    protected static final MethodNode GET_OWNER = CLOSURE_TYPE.getGetterMethod("getOwner");
    protected static final MethodNode GET_THISOBJECT = CLOSURE_TYPE.getGetterMethod("getThisObject");
    protected static final ClassNode DELEGATES_TO = ClassHelper.make(DelegatesTo.class);
    protected static final ClassNode DELEGATES_TO_TARGET = ClassHelper.make(DelegatesTo.Target.class);
    protected static final ClassNode CLOSUREPARAMS_CLASSNODE = ClassHelper.make(ClosureParams.class);
    protected static final ClassNode NAMED_PARAMS_CLASSNODE = ClassHelper.make(NamedParams.class);
    protected static final ClassNode NAMED_PARAM_CLASSNODE = ClassHelper.make(NamedParam.class);
    @Deprecated protected static final ClassNode LINKEDHASHMAP_CLASSNODE = LinkedHashMap_TYPE;
    protected static final ClassNode ENUMERATION_TYPE = ClassHelper.make(Enumeration.class);
    protected static final ClassNode MAP_ENTRY_TYPE = ClassHelper.make(Map.Entry.class);
    protected static final ClassNode ITERABLE_TYPE = ClassHelper.ITERABLE_TYPE;

    private static List<ClassNode> TUPLE_TYPES = Arrays.stream(ClassHelper.TUPLE_CLASSES).map(ClassHelper::makeWithoutCaching).collect(Collectors.toList());

    public static final MethodNode CLOSURE_CALL_NO_ARG  = CLOSURE_TYPE.getDeclaredMethod("call", Parameter.EMPTY_ARRAY);
    public static final MethodNode CLOSURE_CALL_ONE_ARG = CLOSURE_TYPE.getDeclaredMethod("call", new Parameter[]{new Parameter(OBJECT_TYPE, "arg")});
    public static final MethodNode CLOSURE_CALL_VARGS   = CLOSURE_TYPE.getDeclaredMethod("call", new Parameter[]{new Parameter(OBJECT_TYPE.makeArray(), "args")});

    public static final Statement GENERATED_EMPTY_STATEMENT = EmptyStatement.INSTANCE;

    protected final ReturnAdder.ReturnStatementListener returnListener = returnStatement -> {
        if (returnStatement.isReturningNullOrVoid()) return;
        ClassNode returnType = checkReturnType(returnStatement);
        if (this.typeCheckingContext.getEnclosingClosure() != null) {
            addClosureReturnType(returnType);
        } else if (this.typeCheckingContext.getEnclosingMethod() == null) {
            throw new GroovyBugError("Unexpected return statement at " + returnStatement.getLineNumber() + ":" + returnStatement.getColumnNumber() + " " + returnStatement.getText());
        }
    };

    protected final ReturnAdder returnAdder = new ReturnAdder(returnListener);

    protected FieldNode currentField;
    protected PropertyNode currentProperty;
    protected DefaultTypeCheckingExtension extension;
    protected TypeCheckingContext typeCheckingContext;

    public StaticTypeCheckingVisitor(final SourceUnit source, final ClassNode classNode) {
        this.typeCheckingContext = new TypeCheckingContext(this);
        this.typeCheckingContext.pushEnclosingClassNode(classNode);
        this.typeCheckingContext.pushTemporaryTypeInfo();
        this.typeCheckingContext.pushErrorCollector(
                        source.getErrorCollector());
        this.typeCheckingContext.source = source;

        this.extension = new DefaultTypeCheckingExtension(this);
        this.extension.addHandler(new EnumTypeCheckingExtension(this));
        this.extension.addHandler(new TraitTypeCheckingExtension(this));
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return typeCheckingContext.getSource();
    }

    public void initialize() {
        extension.setup();
    }

    /**
     * Returns the current type checking context. The context is used internally by the type
     * checker during type checking to store various state data.
     *
     * @return the type checking context
     */
    public TypeCheckingContext getTypeCheckingContext() {
        return typeCheckingContext;
    }

    public void addTypeCheckingExtension(final TypeCheckingExtension extension) {
        this.extension.addHandler(extension);
    }

    public void setCompilationUnit(final CompilationUnit compilationUnit) {
        typeCheckingContext.setCompilationUnit(compilationUnit);
    }

    @Override
    public void visitClass(final ClassNode node) {
        if (shouldSkipClassNode(node)) return;
        if (!extension.beforeVisitClass(node)) {
            Object type = node.getNodeMetaData(INFERRED_TYPE);
            if (type != null) {
                // transformation has already been run on this class node
                // so use a silent collector in order not to duplicate errors
                typeCheckingContext.pushErrorCollector();
            }
            typeCheckingContext.pushEnclosingClassNode(node);
            Set<MethodNode> oldSet = typeCheckingContext.alreadyVisitedMethods;
            typeCheckingContext.alreadyVisitedMethods = new LinkedHashSet<>();

            super.visitClass(node);
            node.getInnerClasses().forEachRemaining(this::visitClass);

            typeCheckingContext.alreadyVisitedMethods = oldSet;
            typeCheckingContext.popEnclosingClassNode();
            if (type != null) {
                typeCheckingContext.popErrorCollector();
            }

            node.putNodeMetaData(INFERRED_TYPE, node);
            node.putNodeMetaData(StaticTypeCheckingVisitor.class, Boolean.TRUE);
            // mark all methods as visited. We can't do this in visitMethod because the type checker
            // works in a two pass sequence and we don't want to skip the second pass
            node.getMethods().forEach(n -> n.putNodeMetaData(StaticTypeCheckingVisitor.class, Boolean.TRUE));
            node.getDeclaredConstructors().forEach(n -> n.putNodeMetaData(StaticTypeCheckingVisitor.class, Boolean.TRUE));
        }
        extension.afterVisitClass(node);
    }

    /**
     * Returns array of type checking annotations. Subclasses may override this
     * method in order to provide additional types which must be looked up when
     * checking if a method or a class node should be skipped.
     * <p>
     * The default implementation returns {@link TypeChecked}.
     */
    protected ClassNode[] getTypeCheckingAnnotations() {
        return TYPECHECKING_ANNOTATIONS;
    }

    protected boolean shouldSkipClassNode(final ClassNode node) {
        return Boolean.TRUE.equals(node.getNodeMetaData(StaticTypeCheckingVisitor.class)) || isSkipMode(node);
    }

    protected boolean shouldSkipMethodNode(final MethodNode node) {
        return Boolean.TRUE.equals(node.getNodeMetaData(StaticTypeCheckingVisitor.class)) || isSkipMode(node);
    }

    public boolean isSkipMode(final AnnotatedNode node) {
        if (node == null) return false;
        for (ClassNode tca : getTypeCheckingAnnotations()) {
            List<AnnotationNode> annotations = node.getAnnotations(tca);
            if (annotations != null) {
                for (AnnotationNode annotation : annotations) {
                    Expression value = annotation.getMember("value");
                    if (value != null) {
                        if (value instanceof ConstantExpression) {
                            ConstantExpression ce = (ConstantExpression) value;
                            if (TypeCheckingMode.SKIP.toString().equals(ce.getValue().toString())) return true;
                        } else if (value instanceof PropertyExpression) {
                            PropertyExpression pe = (PropertyExpression) value;
                            if (TypeCheckingMode.SKIP.toString().equals(pe.getPropertyAsString())) return true;
                        }
                    }
                }
            }
        }
        if (node instanceof MethodNode) {
            return isSkipMode(node.getDeclaringClass());
        }
        return isSkippedInnerClass(node);
    }

    /**
     * Tests if a node is an inner class node, and if it is, then checks if the enclosing method is skipped.
     *
     * @return true if the inner class node should be skipped
     */
    protected boolean isSkippedInnerClass(final AnnotatedNode node) {
        if (node instanceof ClassNode) {
            ClassNode type = (ClassNode) node;
            if (type.getOuterClass() != null) {
                MethodNode enclosingMethod = type.getEnclosingMethod();
                if (enclosingMethod != null && isSkipMode(enclosingMethod)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void visitClassExpression(final ClassExpression expression) {
        super.visitClassExpression(expression);
        ClassNode cn = expression.getNodeMetaData(INFERRED_TYPE);
        if (cn == null) {
            storeType(expression, getType(expression));
        }
    }

    private static ClassNode getOutermost(ClassNode cn) {
        while (cn.getOuterClass() != null) {
            cn = cn.getOuterClass();
        }
        return cn;
    }

    private static void addPrivateFieldOrMethodAccess(final Expression source, final ClassNode cn, final StaticTypesMarker key, final ASTNode accessedMember) {
        cn.getNodeMetaData(key, x -> new LinkedHashSet<>()).add(accessedMember);
        source.putNodeMetaData(key, accessedMember);
    }

    /**
     * Checks for private field access from inner or outer class.
     */
    private void checkOrMarkPrivateAccess(final Expression source, final FieldNode fn, final boolean lhsOfAssignment) {
        if (fn == null || !fn.isPrivate()) return;
        ClassNode declaringClass = fn.getDeclaringClass();
        ClassNode enclosingClass = typeCheckingContext.getEnclosingClassNode();
        if (declaringClass == enclosingClass && typeCheckingContext.getEnclosingClosure() == null) return;

        if (declaringClass == enclosingClass || getOutermost(declaringClass) == getOutermost(enclosingClass)) {
            StaticTypesMarker accessKind = lhsOfAssignment ? PV_FIELDS_MUTATION : PV_FIELDS_ACCESS;
            addPrivateFieldOrMethodAccess(source, declaringClass, accessKind, fn);
        }
    }

    /**
     * Checks for private method call from inner or outer class.
     */
    private void checkOrMarkPrivateAccess(final Expression source, final MethodNode mn) {
        ClassNode declaringClass = mn.getDeclaringClass();
        ClassNode enclosingClassNode = typeCheckingContext.getEnclosingClassNode();
        if (declaringClass != enclosingClassNode || typeCheckingContext.getEnclosingClosure() != null) {
            int mods = mn.getModifiers();
            boolean sameModule = declaringClass.getModule() == enclosingClassNode.getModule();
            String packageName = declaringClass.getPackageName();
            if (packageName == null) {
                packageName = "";
            }
            if (Modifier.isPrivate(mods) && sameModule) {
                addPrivateFieldOrMethodAccess(source, declaringClass, PV_METHODS_ACCESS, mn);
            } else if (Modifier.isProtected(mods) && !packageName.equals(enclosingClassNode.getPackageName())
                    && !implementsInterfaceOrIsSubclassOf(enclosingClassNode, declaringClass)) {
                ClassNode cn = enclosingClassNode;
                while ((cn = cn.getOuterClass()) != null) {
                    if (implementsInterfaceOrIsSubclassOf(cn, declaringClass)) {
                        addPrivateFieldOrMethodAccess(source, cn, PV_METHODS_ACCESS, mn);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void visitVariableExpression(final VariableExpression vexp) {
        super.visitVariableExpression(vexp);
        if (storeTypeForSuper(vexp)) return;
        if (storeTypeForThis(vexp)) return;

        final String name = vexp.getName();
        final Variable accessedVariable = vexp.getAccessedVariable();
        final TypeCheckingContext.EnclosingClosure enclosingClosure = typeCheckingContext.getEnclosingClosure();

        if (accessedVariable instanceof DynamicVariable) {
            // a dynamic variable is either a closure property, a class member referenced from a closure, or an undeclared variable

            if (enclosingClosure != null) {
                switch (name) {
                  case "delegate":
                    DelegationMetadata dm = getDelegationMetadata(enclosingClosure.getClosureExpression());
                    if (dm != null) {
                        storeType(vexp, dm.getType());
                        return;
                    }
                    // falls through
                  case "owner":
                    if (typeCheckingContext.getEnclosingClosureStack().size() > 1) {
                        storeType(vexp, CLOSURE_TYPE);
                        return;
                    }
                    // falls through
                  case "thisObject":
                    storeType(vexp, typeCheckingContext.getEnclosingClassNode());
                    return;
                  case "parameterTypes":
                    storeType(vexp, CLASS_Type.makeArray());
                    return;
                  case "maximumNumberOfParameters":
                  case "resolveStrategy":
                  case "directive":
                    storeType(vexp, int_TYPE);
                    return;
                }
            }

            if (tryVariableExpressionAsProperty(vexp, name)) return;

            if (!extension.handleUnresolvedVariableExpression(vexp)) {
                addStaticTypeError("The variable [" + name + "] is undeclared.", vexp);
            }
        } else if (accessedVariable instanceof FieldNode) {
            if (enclosingClosure != null) {
                tryVariableExpressionAsProperty(vexp, name);
            } else {
                checkOrMarkPrivateAccess(vexp, (FieldNode) accessedVariable, typeCheckingContext.isTargetOfEnclosingAssignment(vexp));

                // GROOVY-9454
                ClassNode inferredType = getInferredTypeFromTempInfo(vexp, null);
                if (inferredType != null && !isObjectType(inferredType)) {
                    vexp.putNodeMetaData(INFERRED_TYPE, inferredType);
                } else {
                    storeType(vexp, getType(vexp));
                }
            }
        } else if (accessedVariable instanceof PropertyNode) {
            // we must be careful, because the property node may be of a wrong type:
            // if a class contains a getter and a setter of different types or
            // overloaded setters, the type of the property node is arbitrary!
            if (tryVariableExpressionAsProperty(vexp, name)) {
                BinaryExpression enclosingBinaryExpression = typeCheckingContext.getEnclosingBinaryExpression();
                if (enclosingBinaryExpression != null) {
                    Expression leftExpression = enclosingBinaryExpression.getLeftExpression();
                    SetterInfo setterInfo = removeSetterInfo(leftExpression);
                    if (setterInfo != null) {
                        Expression rightExpression = enclosingBinaryExpression.getRightExpression();
                        ensureValidSetter(vexp, leftExpression, rightExpression, setterInfo);
                    }
                }
            }
        } else if (accessedVariable != null) {
            VariableExpression localVariable;
            if (accessedVariable instanceof Parameter) {
                Parameter prm = (Parameter) accessedVariable;
                localVariable = new ParameterVariableExpression(prm);
            } else {
                localVariable = (VariableExpression) accessedVariable;
            }

            ClassNode inferredType = localVariable.getNodeMetaData(INFERRED_TYPE);
            inferredType = getInferredTypeFromTempInfo(localVariable, inferredType);
            if (inferredType != null && !isObjectType(inferredType) && !inferredType.equals(accessedVariable.getType())) {
                vexp.putNodeMetaData(INFERRED_TYPE, inferredType);
            }
        }
    }

    private boolean storeTypeForSuper(final VariableExpression vexp) {
        if (vexp == VariableExpression.SUPER_EXPRESSION) return true;
        if (!vexp.isSuperExpression()) return false;
        storeType(vexp, makeSuper());
        return true;
    }

    private boolean storeTypeForThis(final VariableExpression vexp) {
        if (vexp == VariableExpression.THIS_EXPRESSION) return true;
        if (!vexp.isThisExpression()) return false;
        // GROOVY-6904, GROOVY-9422: non-static inner class constructor call sets type
        storeType(vexp, !isObjectType(vexp.getType()) ? vexp.getType() : makeThis());
        return true;
    }

    private boolean tryVariableExpressionAsProperty(final VariableExpression vexp, final String dynName) {
        PropertyExpression pexp = thisPropX(true, dynName);
        if (existsProperty(pexp, !typeCheckingContext.isTargetOfEnclosingAssignment(vexp))) {
            vexp.copyNodeMetaData(pexp.getObjectExpression());
            for (Object key : new Object[]{IMPLICIT_RECEIVER, READONLY_PROPERTY, PV_FIELDS_ACCESS, PV_FIELDS_MUTATION, DECLARATION_INFERRED_TYPE, DIRECT_METHOD_CALL_TARGET}) {
                Object val = pexp.getNodeMetaData(key);
                if (val != null) vexp.putNodeMetaData(key, val);
            }
            vexp.removeNodeMetaData(INFERRED_TYPE);
            ClassNode type = pexp.getNodeMetaData(INFERRED_TYPE);
            storeType(vexp, Optional.ofNullable(type).orElseGet(pexp::getType));

            String receiver = vexp.getNodeMetaData(IMPLICIT_RECEIVER);
            Boolean dynamic = pexp.getNodeMetaData(DYNAMIC_RESOLUTION);
            // GROOVY-7701, GROOVY-7996: correct false assumption made by VariableScopeVisitor
            if (((receiver != null && !receiver.endsWith("owner")) || Boolean.TRUE.equals(dynamic))
                    && !(vexp.getAccessedVariable() instanceof DynamicVariable)) {
                vexp.setAccessedVariable(new DynamicVariable(dynName, false));
            }
            return true;
        }
        return false;
    }

    @Override
    public void visitPropertyExpression(final PropertyExpression expression) {
        if (existsProperty(expression, !typeCheckingContext.isTargetOfEnclosingAssignment(expression))) return;

        if (!extension.handleUnresolvedProperty(expression)) {
            Expression objectExpression = expression.getObjectExpression();
            addStaticTypeError("No such property: " + expression.getPropertyAsString() + " for class: " +
                    prettyPrintTypeName(findCurrentInstanceOfClass(objectExpression, getType(objectExpression))), expression);
        }
    }

    @Override
    public void visitAttributeExpression(final AttributeExpression expression) {
        if (existsProperty(expression, true)) return;

        if (!extension.handleUnresolvedAttribute(expression)) {
            Expression objectExpression = expression.getObjectExpression();
            addStaticTypeError("No such attribute: " + expression.getPropertyAsString() + " for class: " +
                    prettyPrintTypeName(findCurrentInstanceOfClass(objectExpression, getType(objectExpression))), expression);
        }
    }

    @Override
    public void visitRangeExpression(final RangeExpression expression) {
        super.visitRangeExpression(expression);
        ClassNode fromType = getWrapper(getType(expression.getFrom()));
        ClassNode toType = getWrapper(getType(expression.getTo()));
        if (isWrapperInteger(fromType) && isWrapperInteger(toType)) {
            storeType(expression, ClassHelper.make(IntRange.class));
        } else {
            ClassNode rangeType = RANGE_TYPE.getPlainNodeReference();
            rangeType.setGenericsTypes(new GenericsType[]{new GenericsType(lowestUpperBound(fromType, toType))});
            storeType(expression, rangeType);
        }
    }

    @Override
    public void visitNotExpression(final NotExpression expression) {
        // GROOVY-9455: !(x instanceof T) shouldn't propagate T as inferred type
        typeCheckingContext.pushTemporaryTypeInfo();
        super.visitNotExpression(expression);
        typeCheckingContext.popTemporaryTypeInfo();
    }

    @Override
    public void visitBinaryExpression(final BinaryExpression expression) {
        BinaryExpression enclosingBinaryExpression = typeCheckingContext.getEnclosingBinaryExpression();
        typeCheckingContext.pushEnclosingBinaryExpression(expression);
        try {
            int op = expression.getOperation().getType();
            Expression leftExpression = expression.getLeftExpression();
            Expression rightExpression = expression.getRightExpression();

            leftExpression.visit(this);
            SetterInfo setterInfo = removeSetterInfo(leftExpression);
            ClassNode lType = null;
            if (setterInfo != null) {
                if (ensureValidSetter(expression, leftExpression, rightExpression, setterInfo)) {
                    return;
                }
                lType = getType(leftExpression);
            } else {
                if (op != EQUAL && op != ELVIS_EQUAL) {
                    lType = getType(leftExpression);
                } else {
                    lType = getOriginalDeclarationType(leftExpression);

                    applyTargetType(lType, rightExpression);
                }
                rightExpression.visit(this);
            }

            ClassNode rType = isNullConstant(rightExpression) && !isPrimitiveType(lType)
                    ? UNKNOWN_PARAMETER_TYPE // null to primitive type is handled elsewhere
                    : getInferredTypeFromTempInfo(rightExpression, getType(rightExpression));
            ClassNode resultType;

            if (op == KEYWORD_IN || op == COMPARE_NOT_IN) {
                // for the "in" or "!in" operator, the receiver and the arguments are reversed
                BinaryExpression reverseExpression = binX(rightExpression, expression.getOperation(), leftExpression);
                resultType = getResultType(rType, op, lType, reverseExpression);
                if (resultType == null) resultType = boolean_TYPE; // GROOVY-10239
                storeTargetMethod(expression, reverseExpression.getNodeMetaData(DIRECT_METHOD_CALL_TARGET));
            } else {
                resultType = getResultType(lType, op, rType, expression);
                if (op == ELVIS_EQUAL) {
                    // TODO: Should this transform and visit be done before left and right are visited above?
                    Expression fullExpression = new ElvisOperatorExpression(leftExpression, rightExpression);
                    fullExpression.setSourcePosition(expression);
                    fullExpression.visit(this);

                    resultType = getType(fullExpression);
                }
            }
            if (resultType == null) {
                resultType = lType;
            }

            if (isArrayOp(op)) {
                if (leftExpression instanceof VariableExpression) {//GROOVY-6782
                    if (leftExpression.getNodeMetaData(INFERRED_TYPE) == null) {
                        leftExpression.removeNodeMetaData(INFERRED_RETURN_TYPE);
                        storeType(leftExpression, lType);
                    }
                }
                if (!lType.isArray()
                        && enclosingBinaryExpression != null
                        && enclosingBinaryExpression.getLeftExpression() == expression
                        && isAssignment(enclosingBinaryExpression.getOperation().getType())) {
                    // left hand side of a subscript assignment: map['foo'] = ...
                    Expression enclosingExpressionRHS = enclosingBinaryExpression.getRightExpression();
                    if (!(enclosingExpressionRHS instanceof ClosureExpression)) {
                        enclosingExpressionRHS.visit(this);
                    }
                    ClassNode[] arguments = {rType, getType(enclosingExpressionRHS)};
                    List<MethodNode> nodes = findMethod(lType.redirect(), "putAt", arguments);
                    if (nodes.size() == 1) {
                        typeCheckMethodsWithGenericsOrFail(lType, arguments, nodes.get(0), enclosingExpressionRHS);
                    } else if (nodes.isEmpty()) {
                        addNoMatchingMethodError(lType, "putAt", arguments, enclosingBinaryExpression);
                    }
                }
            }

            boolean isEmptyDeclaration = (expression instanceof DeclarationExpression
                    && (rightExpression instanceof EmptyExpression || rType == UNKNOWN_PARAMETER_TYPE));
            if (!isEmptyDeclaration && isAssignment(op)) {
                if (rightExpression instanceof ConstructorCallExpression)
                    inferDiamondType((ConstructorCallExpression) rightExpression, lType);

                // handle unchecked assignment: List<Type> list = []
                resultType = adjustForTargetType(resultType, lType);

                ClassNode originType = getOriginalDeclarationType(leftExpression);
                typeCheckAssignment(expression, leftExpression, originType, rightExpression, resultType);
                // check for implicit conversion like "String a = 123", "int[] b = [1,2,3]", "List c = [].stream()", etc.
                if (!implementsInterfaceOrIsSubclassOf(wrapTypeIfNecessary(resultType), wrapTypeIfNecessary(originType))) {
                    resultType = originType;
                } else if (isPrimitiveType(originType) && resultType.equals(getWrapper(originType))) {
                    resultType = originType; // retain primitive semantics
                } else {
                    // GROOVY-7549: RHS type may not be accessible to enclosing class
                    int modifiers = resultType.getModifiers();
                    ClassNode enclosingType = typeCheckingContext.getEnclosingClassNode();
                    if (!Modifier.isPublic(modifiers) && !enclosingType.equals(resultType)
                            && !getOutermost(enclosingType).equals(getOutermost(resultType))
                            && (Modifier.isPrivate(modifiers) || !Objects.equals(enclosingType.getPackageName(), resultType.getPackageName()))) {
                        resultType = originType; // TODO: Find accesible type in hierarchy of resultType?
                    } else if (GenericsUtils.hasUnresolvedGenerics(resultType)) { // GROOVY-9033, GROOVY-10089, et al.
                        Map<GenericsTypeName, GenericsType> enclosing = extractGenericsParameterMapOfThis(typeCheckingContext);
                        resultType = fullyResolveType(resultType, Optional.ofNullable(enclosing).orElseGet(Collections::emptyMap));
                    }
                }

                // track conditional assignment
                if (leftExpression instanceof VariableExpression
                        && typeCheckingContext.ifElseForWhileAssignmentTracker != null) {
                    Variable accessedVariable = ((VariableExpression) leftExpression).getAccessedVariable();
                    if (accessedVariable instanceof Parameter) {
                        accessedVariable = new ParameterVariableExpression((Parameter) accessedVariable);
                    }
                    if (accessedVariable instanceof VariableExpression) {
                        recordAssignment((VariableExpression) accessedVariable, resultType);
                    }
                }

                storeType(leftExpression, resultType);

                // propagate closure parameter type information
                if (leftExpression instanceof VariableExpression) {
                    if (rightExpression instanceof ClosureExpression) {
                        leftExpression.putNodeMetaData(CLOSURE_ARGUMENTS, ((ClosureExpression) rightExpression).getParameters());
                    } else if (rightExpression instanceof VariableExpression
                            && ((VariableExpression) rightExpression).getAccessedVariable() instanceof Expression
                            && ((Expression) ((VariableExpression) rightExpression).getAccessedVariable()).getNodeMetaData(CLOSURE_ARGUMENTS) != null) {
                        Variable targetVariable = findTargetVariable((VariableExpression) leftExpression);
                        if (targetVariable instanceof ASTNode) {
                            ((ASTNode) targetVariable).putNodeMetaData(CLOSURE_ARGUMENTS, ((Expression) ((VariableExpression) rightExpression).getAccessedVariable()).getNodeMetaData(CLOSURE_ARGUMENTS));
                        }
                    }
                }
            } else if (op == KEYWORD_INSTANCEOF /*|| op == COMPARE_NOT_INSTANCEOF*/) {
                pushInstanceOfTypeInfo(leftExpression, rightExpression);
            }
            if (!isEmptyDeclaration) {
                storeType(expression, resultType);
            }

            validateResourceInARM(expression, resultType);

            // GROOVY-5874: if left expression is a closure shared variable, a second pass should be done
            if (leftExpression instanceof VariableExpression && ((VariableExpression) leftExpression).isClosureSharedVariable()) {
                typeCheckingContext.secondPassExpressions.add(new SecondPassExpression<>(expression));
            }
        } finally {
            typeCheckingContext.popEnclosingBinaryExpression();
        }
    }

    private void validateResourceInARM(final BinaryExpression expression, final ClassNode lType) {
        if (expression instanceof DeclarationExpression
                && TryCatchStatement.isResource(expression)
                && !isOrImplements(lType, AUTOCLOSEABLE_TYPE)) {
            addError("Resource[" + lType.getName() + "] in ARM should be of type AutoCloseable", expression);
        }
    }

    private void applyTargetType(final ClassNode target, final Expression source) {
        if (isFunctionalInterface(target)) {
            if (source instanceof ClosureExpression) {
                inferParameterAndReturnTypesOfClosureOnRHS(target, (ClosureExpression) source);
            } else if (source instanceof MethodReferenceExpression) {
                LambdaExpression lambdaExpression = constructLambdaExpressionForMethodReference(target);

                inferParameterAndReturnTypesOfClosureOnRHS(target, lambdaExpression);
                source.putNodeMetaData(CONSTRUCTED_LAMBDA_EXPRESSION, lambdaExpression);
                source.putNodeMetaData(CLOSURE_ARGUMENTS, Arrays.stream(lambdaExpression.getParameters()).map(Parameter::getType).toArray(ClassNode[]::new));
            }
        } else if (isClosureWithType(target)) {
            if (source instanceof ClosureExpression) {
                GenericsType returnType = target.getGenericsTypes()[0];
                storeInferredReturnType(source, getCombinedBoundType(returnType));
            }
        }
    }

    private void inferParameterAndReturnTypesOfClosureOnRHS(final ClassNode lhsType, final ClosureExpression rhsExpression) {
        Tuple2<ClassNode[], ClassNode> typeInfo = GenericsUtils.parameterizeSAM(lhsType);
        Parameter[] closureParameters = getParametersSafe(rhsExpression);
        ClassNode[] samParameterTypes = typeInfo.getV1();

        int n = closureParameters.length, m = samParameterTypes.length;
        if (n == m || (1 == m && hasImplicitParameter(rhsExpression))) {
            for (int i = 0; i < n; i += 1) {
                Parameter parameter = closureParameters[i];
                if (parameter.isDynamicTyped()) {
                    parameter.setType(samParameterTypes[i]);
                    parameter.setOriginType(samParameterTypes[i]);
                } else {
                    checkParamType(parameter, samParameterTypes[i], i == n-1, rhsExpression instanceof LambdaExpression);
                }
            }
        } else {
            addStaticTypeError("Wrong number of parameters for method target " + toMethodParametersString(findSAM(lhsType).getName(), samParameterTypes), rhsExpression);
        }

        storeInferredReturnType(rhsExpression, typeInfo.getV2());
    }

    private void checkParamType(final Parameter source, final ClassNode target, final boolean isLast, final boolean lambda) {
        if (/*lambda ? !source.getOriginType().equals(target) :*/!typeCheckMethodArgumentWithGenerics(source.getOriginType(), target, isLast))
            addStaticTypeError("Expected type " + prettyPrintType(target) + " for " + (lambda ? "lambda" : "closure") + " parameter: " + source.getName(), source);
    }

    /**
     * Given a binary expression corresponding to an assignment, will check that
     * the type of the RHS matches one of the possible setters and if not, throw
     * a type checking error.
     *
     * @param expression      the assignment expression
     * @param leftExpression  left expression of the assignment
     * @param rightExpression right expression of the assignment
     * @param setterInfo      possible setters
     * @return {@code false} if valid setter found or {@code true} if type checking error created
     */
    private boolean ensureValidSetter(final Expression expression, final Expression leftExpression, final Expression rightExpression, final SetterInfo setterInfo) {
        // for expressions like foo = { ... }
        // we know that the RHS type is a closure
        // but we must check if the binary expression is an assignment
        // because we need to check if a setter uses @DelegatesTo
        VariableExpression receiver = varX("%", setterInfo.receiverType);
        // for "x op= y" expression, find type as if it was "x = x op y"
        Expression valueExpression = rightExpression;
        if (isCompoundAssignment(expression)) {
            Token op = ((BinaryExpression) expression).getOperation();
            if (op.getType() == ELVIS_EQUAL) { // GROOVY-10419: "x ?= y"
                valueExpression = elvisX(leftExpression, rightExpression);
            } else {
                op = Token.newSymbol(TokenUtil.removeAssignment(op.getType()), op.getStartLine(), op.getStartColumn());
                valueExpression = binX(leftExpression, op, rightExpression);
            }
        }

        Function<Expression, MethodNode> setterCall = right -> {
            typeCheckingContext.pushEnclosingBinaryExpression(null); // GROOVY-10628: LHS re-purposed
            try {
                MethodCallExpression call = new MethodCallExpression(receiver, setterInfo.name, right);
                call.setImplicitThis(false);
                visitMethodCallExpression(call);
                return call.getNodeMetaData(DIRECT_METHOD_CALL_TARGET);
            } finally {
                typeCheckingContext.popEnclosingBinaryExpression();
            }
        };

        Function<MethodNode, ClassNode> setterType = setter -> {
            ClassNode type = setter.getParameters()[0].getOriginType();
            if (!setter.isStatic() && !(setter instanceof ExtensionMethodNode) && GenericsUtils.hasUnresolvedGenerics(type)) {
                type = applyGenericsContext(extractPlaceHolders(setterInfo.receiverType, setter.getDeclaringClass()), type);
            }
            return type;
        };

        MethodNode methodTarget = setterCall.apply(valueExpression);
        if (methodTarget == null && !isCompoundAssignment(expression)) {
            // if no direct match, try implicit conversion
            for (MethodNode setter : setterInfo.setters) {
                ClassNode lType = setterType.apply(setter);
                ClassNode rType = getDeclaredOrInferredType(valueExpression);
                if (lType.isArray() && valueExpression instanceof ListExpression) {
                    rType = inferLoopElementType(rType).makeArray(); // GROOVY-7506
                }
                if (checkCompatibleAssignmentTypes(lType, rType, valueExpression, false)) {
                    methodTarget = setterCall.apply(castX(lType, valueExpression));
                    if (methodTarget != null) {
                        break;
                    }
                }
            }
        }

        if (methodTarget != null) {
            for (MethodNode setter : setterInfo.setters) {
                if (setter == methodTarget) {
                    leftExpression.putNodeMetaData(DIRECT_METHOD_CALL_TARGET, methodTarget);
                    leftExpression.removeNodeMetaData(INFERRED_TYPE); // clear assumption
                    storeType(leftExpression, setterType.apply(methodTarget));
                    break;
                }
            }
            return false;
        } else {
            ClassNode firstSetterType = setterType.apply(setterInfo.setters.get(0));
            addAssignmentError(firstSetterType, getType(valueExpression), expression);
            return true;
        }
    }

    private static boolean isClosureWithType(final ClassNode type) {
        return CLOSURE_TYPE.equals(type) && Optional.ofNullable(type.getGenericsTypes()).filter(gts -> gts != null && gts.length == 1).isPresent();
    }

    private static boolean isCompoundAssignment(final Expression exp) {
        if (exp instanceof BinaryExpression) {
            Token op = ((BinaryExpression) exp).getOperation();
            return isAssignment(op.getType()) && op.getType() != EQUAL;
        }
        return false;
    }

    protected ClassNode getOriginalDeclarationType(final Expression lhs) {
        Variable var = null;
        if (lhs instanceof FieldExpression) {
            var = ((FieldExpression) lhs).getField();
        } else if (lhs instanceof VariableExpression) {
            var = findTargetVariable((VariableExpression) lhs);
        }
        return var != null && !(var instanceof DynamicVariable) ? var.getOriginType() : getType(lhs);
    }

    protected void inferDiamondType(final ConstructorCallExpression cce, final ClassNode lType) {
        ClassNode cceType = cce.getType(), inferredType = lType;
        // check if constructor call expression makes use of the diamond operator
        if (cceType.getGenericsTypes() != null && cceType.getGenericsTypes().length == 0) {
            ArgumentListExpression argumentList = InvocationWriter.makeArgumentList(cce.getArguments());
            ConstructorNode constructor = cce.getNodeMetaData(DIRECT_METHOD_CALL_TARGET);
            if (!argumentList.getExpressions().isEmpty() && constructor != null) {
                ClassNode type = GenericsUtils.parameterizeType(cceType, cceType);
                type = inferReturnTypeGenerics(type, constructor, argumentList);
                if (lType.getGenericsTypes() != null // GROOVY-10367: nothing to inspect
                        && (type.toString(false).indexOf('#') > 0 // GROOVY-9983, GROOVY-10291, GROOVY-10368: unresolved generic
                        // GROOVY-6232, GROOVY-9956: if cce not assignment compatible, process target as additional type witness
                        || checkCompatibleAssignmentTypes(lType, type, cce) && !GenericsUtils.buildWildcardType(lType).isCompatibleWith(type))) {
                    // allow covariance of each type parameter, but maintain semantics for nested generics

                    ClassNode pType = GenericsUtils.parameterizeType(lType, type);
                    GenericsType[] lhs = pType.getGenericsTypes(), rhs = type.getGenericsTypes();
                    if (lhs == null || rhs == null || lhs.length != rhs.length) throw new GroovyBugError(
                            "Parameterization failed: " + prettyPrintType(pType) + " ~ " + prettyPrintType(type));

                    if (IntStream.range(0, lhs.length).allMatch(i ->
                            GenericsUtils.buildWildcardType(getCombinedBoundType(lhs[i])).isCompatibleWith(rhs[i].getType()))) {
                        type = pType; // lType proved to be a viable type witness
                    }
                }
                inferredType = type;
            }
            if (inferredType.isGenericsPlaceHolder()) // GROOVY-10344: "T t = new C<>()"
                inferredType = getCombinedBoundType(inferredType.getGenericsTypes()[0]);

            adjustGenerics(inferredType, cceType);
            storeType(cce, cceType);
        }
    }

    private void adjustGenerics(final ClassNode source, final ClassNode target) {
        GenericsType[] genericsTypes = source.getGenericsTypes();
        if (genericsTypes == null) { // Map foo = new HashMap<>()
            genericsTypes = target.redirect().getGenericsTypes().clone();
            for (int i = 0, n = genericsTypes.length; i < n; i += 1) {
                GenericsType gt = genericsTypes[i];
                // GROOVY-10055: handle diamond or raw
                ClassNode cn = gt.getUpperBounds() != null
                        ? gt.getUpperBounds()[0] : gt.getType().redirect();
                genericsTypes[i] = cn.getPlainNodeReference().asGenericsType();
            }
        } else {
            genericsTypes = genericsTypes.clone();
            for (int i = 0, n = genericsTypes.length; i < n; i += 1) {
                GenericsType gt = genericsTypes[i];
                genericsTypes[i] = new GenericsType(gt.getType(),
                        gt.getUpperBounds(), gt.getLowerBound());
                genericsTypes[i].setWildcard(gt.isWildcard()); // GROOVY-10310
            }
        }
        target.setGenericsTypes(genericsTypes);
    }

    /**
     * Stores information about types when [objectOfInstanceof instanceof typeExpression] is visited.
     *
     * @param objectOfInstanceOf the expression which must be checked against instanceof
     * @param typeExpression     the expression which represents the target type
     */
    protected void pushInstanceOfTypeInfo(final Expression objectOfInstanceOf, final Expression typeExpression) {
        List<ClassNode> potentialTypes = typeCheckingContext.temporaryIfBranchTypeInformation.peek()
            .computeIfAbsent(extractTemporaryTypeInfoKey(objectOfInstanceOf), key -> new LinkedList<>());
        potentialTypes.add(typeExpression.getType());
    }

    private boolean typeCheckMultipleAssignmentAndContinue(final Expression leftExpression, Expression rightExpression) {
        if (rightExpression instanceof VariableExpression || rightExpression instanceof PropertyExpression || rightExpression instanceof MethodCall) {
            ClassNode inferredType = Optional.ofNullable(getType(rightExpression)).orElseGet(rightExpression::getType);
            GenericsType[] genericsTypes = inferredType.getGenericsTypes();
            ListExpression listExpression = new ListExpression();
            listExpression.setSourcePosition(rightExpression);

            // convert Tuple[1-16] bearing expressions to mock list for checking
            for (int n = TUPLE_TYPES.indexOf(inferredType), i = 0; i < n; i += 1) {
                ClassNode type = (genericsTypes != null ? genericsTypes[i].getType() : OBJECT_TYPE);
                listExpression.addExpression(varX("v" + (i + 1), type));
            }
            if (!listExpression.getExpressions().isEmpty()) {
                rightExpression = listExpression;
            }
        }

        if (!(rightExpression instanceof ListExpression)) {
            addStaticTypeError("Multiple assignments without list or tuple on the right-hand side are unsupported in static type checking mode", rightExpression);
            return false;
        }

        TupleExpression tuple = (TupleExpression) leftExpression;
        ListExpression values = (ListExpression) rightExpression;
        List<Expression> tupleExpressions = tuple.getExpressions();
        List<Expression> valueExpressions = values.getExpressions();

        if (tupleExpressions.size() > valueExpressions.size()) {
            addStaticTypeError("Incorrect number of values. Expected:" + tupleExpressions.size() + " Was:" + valueExpressions.size(), values);
            return false;
        }

        for (int i = 0, n = tupleExpressions.size(); i < n; i += 1) {
            ClassNode valueType = getType(valueExpressions.get(i));
            ClassNode targetType = getType(tupleExpressions.get(i));
            if (!isAssignableTo(valueType, targetType)) {
                addStaticTypeError("Cannot assign value of type " + prettyPrintType(valueType) + " to variable of type " + prettyPrintType(targetType), rightExpression);
                return false;
            }
            storeType(tupleExpressions.get(i), valueType);
        }

        return true;
    }

    private ClassNode adjustTypeForSpreading(final ClassNode rightExpressionType, final Expression leftExpression) {
        // given "list*.foo = 100" or "map*.value = 100", then the assignment must be checked against [100], not 100
        if (leftExpression instanceof PropertyExpression && ((PropertyExpression) leftExpression).isSpreadSafe()) {
            return extension.buildListType(rightExpressionType);
        }
        return rightExpressionType;
    }

    private boolean addedReadOnlyPropertyError(final Expression expr) {
        // if expr is of READONLY_PROPERTY_RETURN type, then it means we are on a missing property
        if (expr.getNodeMetaData(READONLY_PROPERTY) == null) return false;
        String name;
        if (expr instanceof VariableExpression) {
            name = ((VariableExpression) expr).getName();
        } else {
            name = ((PropertyExpression) expr).getPropertyAsString();
        }
        addStaticTypeError("Cannot set read-only property: " + name, expr);
        return true;
    }

    private void addPrecisionErrors(final ClassNode leftRedirect, final ClassNode lhsType, final ClassNode rhsType, final Expression rightExpression) {
        if (isNumberType(leftRedirect)) {
            if (isNumberType(rhsType) && checkPossibleLossOfPrecision(leftRedirect, rhsType, rightExpression)) {
                addStaticTypeError("Possible loss of precision from " + prettyPrintType(rhsType) + " to " + prettyPrintType(lhsType), rightExpression);
            }
            return;
        }
        if (!leftRedirect.isArray()) return;
        // left type is an array, check the right component types
        if (rightExpression instanceof ListExpression) {
            ClassNode leftComponentType = leftRedirect.getComponentType();
            for (Expression expression : ((ListExpression) rightExpression).getExpressions()) {
                ClassNode rightComponentType = getType(expression);
                if (!checkCompatibleAssignmentTypes(leftComponentType, rightComponentType) && !(isNullConstant(expression) && !isPrimitiveType(leftComponentType))) {
                    addStaticTypeError("Cannot assign value of type " + prettyPrintType(rightComponentType) + " into array of type " + prettyPrintType(lhsType), rightExpression);
                }
            }
        } else if (rhsType.redirect().isArray()) {
            ClassNode leftComponentType = leftRedirect.getComponentType();
            ClassNode rightComponentType = rhsType.redirect().getComponentType();
            if (!checkCompatibleAssignmentTypes(leftComponentType, rightComponentType)) {
                addStaticTypeError("Cannot assign value of type " + prettyPrintType(rightComponentType) + " into array of type " + prettyPrintType(lhsType), rightExpression);
            }
        }
    }

    private void addListAssignmentConstructorErrors(final ClassNode leftRedirect, final ClassNode leftExpressionType, final ClassNode inferredRightExpressionType, final Expression rightExpression, final Expression assignmentExpression) {
        if (isWildcardLeftHandSide(leftRedirect) && !isClassType(leftRedirect)) return; // GROOVY-6802, GROOVY-6803
        // if left type is not a list but right type is a list, then we're in the case of a groovy
        // constructor type : Dimension d = [100,200]
        // In that case, more checks can be performed
        if (!implementsInterfaceOrIsSubclassOf(LIST_TYPE, leftRedirect)
                && (!leftRedirect.isAbstract() || leftRedirect.isArray())
                && !ArrayList_TYPE.isDerivedFrom(leftRedirect) && !LinkedHashSet_TYPE.isDerivedFrom(leftRedirect)) {
            ClassNode[] types = getArgumentTypes(args(((ListExpression) rightExpression).getExpressions()));
            MethodNode methodNode = checkGroovyStyleConstructor(leftRedirect, types, assignmentExpression);
            if (methodNode != null) {
                rightExpression.putNodeMetaData(DIRECT_METHOD_CALL_TARGET, methodNode);
            }
        } else if (implementsInterfaceOrIsSubclassOf(inferredRightExpressionType, LIST_TYPE)
                && !implementsInterfaceOrIsSubclassOf(inferredRightExpressionType, leftRedirect)) {
            if (!extension.handleIncompatibleAssignment(leftExpressionType, inferredRightExpressionType, assignmentExpression)) {
                addAssignmentError(leftExpressionType, inferredRightExpressionType, assignmentExpression);
            }
        }
    }

    private void addMapAssignmentConstructorErrors(final ClassNode leftRedirect, final Expression leftExpression, final Expression rightExpression) {
        if ((leftExpression instanceof VariableExpression && ((VariableExpression) leftExpression).isDynamicTyped())
                || (isWildcardLeftHandSide(leftRedirect) && !isClassType(leftRedirect)) // GROOVY-6802, GROOVY-6803
                || implementsInterfaceOrIsSubclassOf(leftRedirect, MAP_TYPE)) {
            return;
        }

        // groovy constructor shorthand: A a = [x:2, y:3]
        ClassNode[] argTypes = getArgumentTypes(args(rightExpression));
        checkGroovyStyleConstructor(leftRedirect, argTypes, rightExpression);
        // perform additional type checking on arguments
        MapExpression mapExpression = (MapExpression) rightExpression;
        checkGroovyConstructorMap(leftExpression, leftRedirect, mapExpression);
    }

    private void checkTypeGenerics(final ClassNode leftExpressionType, final ClassNode wrappedRHS, final Expression rightExpression) {
        if (leftExpressionType.isUsingGenerics()
                && !isNullConstant(rightExpression)
                && !(rightExpression instanceof ClosureExpression) // GROOVY-10277
                && !UNKNOWN_PARAMETER_TYPE.equals(wrappedRHS) && !missesGenericsTypes(wrappedRHS)
                && !GenericsUtils.buildWildcardType(leftExpressionType).isCompatibleWith(wrappedRHS))
            addStaticTypeError("Incompatible generic argument types. Cannot assign " + prettyPrintType(wrappedRHS) + " to: " + prettyPrintType(leftExpressionType), rightExpression);
    }

    private boolean hasGStringStringError(final ClassNode leftExpressionType, final ClassNode wrappedRHS, final Expression rightExpression) {
        if (isParameterizedWithString(leftExpressionType) && isParameterizedWithGStringOrGStringString(wrappedRHS)) {
            addStaticTypeError("You are trying to use a GString in place of a String in a type which explicitly declares accepting String. " +
                    "Make sure to call toString() on all GString values.", rightExpression);
            return true;
        }
        return false;
    }

    private static boolean isConstructorAbbreviation(final ClassNode leftType, final Expression rightExpression) {
        if (rightExpression instanceof ListExpression) {
            return !(ArrayList_TYPE.isDerivedFrom(leftType) || ArrayList_TYPE.implementsInterface(leftType)
                    || LinkedHashSet_TYPE.isDerivedFrom(leftType) || LinkedHashSet_TYPE.implementsInterface(leftType));
        }
        if (rightExpression instanceof MapExpression) {
            return !(LinkedHashMap_TYPE.isDerivedFrom(leftType) || LinkedHashMap_TYPE.implementsInterface(leftType));
        }
        return false;
    }

    protected void typeCheckAssignment(final BinaryExpression assignmentExpression, final Expression leftExpression, final ClassNode leftExpressionType, final Expression rightExpression, final ClassNode rightExpressionType) {
        if (leftExpression instanceof TupleExpression) {
            if (!typeCheckMultipleAssignmentAndContinue(leftExpression, rightExpression)) return;
        }

        // TODO: need errors for write-only too!
        if (addedReadOnlyPropertyError(leftExpression)) return;

        ClassNode rTypeWrapped = adjustTypeForSpreading(rightExpressionType, leftExpression);

        if (!checkCompatibleAssignmentTypes(leftExpressionType, rTypeWrapped, rightExpression)) {
            if (!extension.handleIncompatibleAssignment(leftExpressionType, rightExpressionType, assignmentExpression)) {
                addAssignmentError(leftExpressionType, rightExpressionType, rightExpression);
            }
        } else {
            ClassNode lTypeRedirect = leftExpressionType.redirect();
            addPrecisionErrors(lTypeRedirect, leftExpressionType, rightExpressionType, rightExpression);
            if (rightExpression instanceof ListExpression) {
                addListAssignmentConstructorErrors(lTypeRedirect, leftExpressionType, rightExpressionType, rightExpression, assignmentExpression);
            } else if (rightExpression instanceof MapExpression) {
                addMapAssignmentConstructorErrors(lTypeRedirect, leftExpression, rightExpression);
            }
            if (!hasGStringStringError(leftExpressionType, rTypeWrapped, rightExpression)
                    && !isConstructorAbbreviation(leftExpressionType, rightExpression)) {
                checkTypeGenerics(leftExpressionType, rTypeWrapped, rightExpression);
            }
        }
    }

    protected void checkGroovyConstructorMap(final Expression receiver, final ClassNode receiverType, final MapExpression mapExpression) {
        // workaround for map-style checks putting setter info on wrong AST nodes
        typeCheckingContext.pushEnclosingBinaryExpression(null);
        for (MapEntryExpression entryExpression : mapExpression.getMapEntryExpressions()) {
            Expression keyExpression = entryExpression.getKeyExpression();
            if (!(keyExpression instanceof ConstantExpression)) {
                addStaticTypeError("Dynamic keys in map-style constructors are unsupported in static type checking", keyExpression);
            } else {
                String pName = keyExpression.getText();
                AtomicReference<ClassNode> pType = new AtomicReference<>();
                if (!existsProperty(propX(varX("_", receiverType), pName), false, new PropertyLookupVisitor(pType))) {
                    addStaticTypeError("No such property: " + pName + " for class: " + prettyPrintTypeName(receiverType), receiver);
                } else {
                    ClassNode targetType = Optional.ofNullable(receiverType.getSetterMethod(getSetterName(pName), false))
                            .map(setter -> setter.getParameters()[0].getType()).orElseGet(pType::get);
                    Expression valueExpression = entryExpression.getValueExpression();
                    ClassNode valueType = getType(valueExpression);

                    ClassNode resultType = getResultType(targetType, ASSIGN, valueType,
                                assignX(keyExpression, valueExpression, entryExpression));
                    if (!checkCompatibleAssignmentTypes(targetType, resultType, valueExpression)
                            && !extension.handleIncompatibleAssignment(targetType, valueType, entryExpression)) {
                        addAssignmentError(targetType, valueType, entryExpression);
                    }
                }
            }
        }
        typeCheckingContext.popEnclosingBinaryExpression();
    }

    @Deprecated
    protected static boolean hasRHSIncompleteGenericTypeInfo(final ClassNode inferredRightExpressionType) {
        boolean replaceType = false;
        GenericsType[] genericsTypes = inferredRightExpressionType.getGenericsTypes();
        if (genericsTypes != null) {
            for (GenericsType genericsType : genericsTypes) {
                if (genericsType.isPlaceholder()) {
                    replaceType = true;
                    break;
                }
            }
        }
        return replaceType;
    }

    /**
     * Checks that a constructor style expression is valid regarding the number
     * of arguments and the argument types.
     *
     * @param node      the class node for which we will try to find a matching constructor
     * @param arguments the constructor arguments
     * @deprecated use {@link #checkGroovyStyleConstructor(org.codehaus.groovy.ast.ClassNode, org.codehaus.groovy.ast.ClassNode[], org.codehaus.groovy.ast.ASTNode)} )}
     */
    @Deprecated
    protected void checkGroovyStyleConstructor(final ClassNode node, final ClassNode[] arguments) {
        checkGroovyStyleConstructor(node, arguments, typeCheckingContext.getEnclosingClassNode());
    }

    /**
     * Checks that a constructor style expression is valid regarding the number
     * of arguments and the argument types.
     *
     * @param node      the class node for which we will try to find a matching constructor
     * @param arguments the constructor arguments
     */
    protected MethodNode checkGroovyStyleConstructor(final ClassNode node, final ClassNode[] arguments, final ASTNode source) {
        if (isObjectType(node) || isDynamicTyped(node)) {
            // in that case, we are facing a list constructor assigned to a def or object
            return null;
        }
        List<ConstructorNode> constructors = node.getDeclaredConstructors();
        if (constructors.isEmpty() && arguments.length == 0) {
            return null;
        }
        List<MethodNode> constructorList = findMethod(node, "<init>", arguments);
        if (constructorList.isEmpty()) {
            if (isBeingCompiled(node) && arguments.length == 1 && LinkedHashMap_TYPE.equals(arguments[0])) {
                // there will be a default hash map constructor added later
                ConstructorNode cn = new ConstructorNode(Opcodes.ACC_PUBLIC, new Parameter[]{new Parameter(LinkedHashMap_TYPE, "args")}, ClassNode.EMPTY_ARRAY, EmptyStatement.INSTANCE);
                return cn;
            } else {
                addStaticTypeError("No matching constructor found: " + prettyPrintTypeName(node) + toMethodParametersString("", arguments), source);
                return null;
            }
        } else if (constructorList.size() > 1) {
            addStaticTypeError("Ambiguous constructor call " + prettyPrintTypeName(node) + toMethodParametersString("", arguments), source);
            return null;
        }
        return constructorList.get(0);
    }

    /**
     * When instanceof checks are found in the code, we store temporary type
     * information data in the {@link TypeCheckingContext#temporaryIfBranchTypeInformation}
     * table. This method computes the key which must be used to store this type
     * info.
     *
     * @param expression the expression for which to compute the key
     * @return a key to be used for {@link TypeCheckingContext#temporaryIfBranchTypeInformation}
     */
    protected Object extractTemporaryTypeInfoKey(final Expression expression) {
        return expression instanceof VariableExpression ? findTargetVariable((VariableExpression) expression) : expression.getText();
    }

    /**
     * A helper method which determines which receiver class should be used in error messages when a field or attribute
     * is not found. The returned type class depends on whether we have temporary type information available (due to
     * instanceof checks) and whether there is a single candidate in that case.
     *
     * @param expr the expression for which an unknown field has been found
     * @param type the type of the expression (used as fallback type)
     * @return if temporary information is available and there's only one type, returns the temporary type class
     * otherwise falls back to the provided type class.
     */
    protected ClassNode findCurrentInstanceOfClass(final Expression expr, final ClassNode type) {
        if (!typeCheckingContext.temporaryIfBranchTypeInformation.isEmpty()) {
            List<ClassNode> nodes = getTemporaryTypesForExpression(expr);
            if (nodes != null && nodes.size() == 1) return nodes.get(0);
        }
        return type;
    }

    protected boolean existsProperty(final PropertyExpression pexp, final boolean checkForReadOnly) {
        return existsProperty(pexp, checkForReadOnly, null);
    }

    /**
     * Checks whether a property exists on the receiver, or on any of the possible receiver classes (found in the
     * temporary type information table)
     *
     * @param pexp     a property expression
     * @param readMode if true, look for property read, else for property set
     * @param visitor  if not null, when the property node is found, visit it with the provided visitor
     * @return true if the property is defined in any of the possible receiver classes
     */
    protected boolean existsProperty(final PropertyExpression pexp, final boolean readMode, final ClassCodeVisitorSupport visitor) {
        super.visitPropertyExpression(pexp);

        String propertyName = pexp.getPropertyAsString();
        if (propertyName == null) return false;

        Expression objectExpression = pexp.getObjectExpression();
        ClassNode objectExpressionType = getType(objectExpression);
        if (objectExpression instanceof ConstructorCallExpression) {
            ClassNode rawType = objectExpressionType.getPlainNodeReference();
            inferDiamondType((ConstructorCallExpression) objectExpression, rawType);
        }
        // enclosing excludes classes that skip STC
        Set<ClassNode> enclosingTypes = new LinkedHashSet<>();
        enclosingTypes.add(typeCheckingContext.getEnclosingClassNode());
        enclosingTypes.addAll(enclosingTypes.iterator().next().getOuterClasses());

        boolean staticOnlyAccess = isClassClassNodeWrappingConcreteType(objectExpressionType);
        if (staticOnlyAccess && "this".equals(propertyName)) {
            // handle "Outer.this" for any level of nesting
            ClassNode outer = objectExpressionType.getGenericsTypes()[0].getType();

            ClassNode found = null;
            for (ClassNode enclosingType : enclosingTypes) {
                if (!enclosingType.isStaticClass() && outer.equals(enclosingType.getOuterClass())) {
                    found = enclosingType;
                    break;
                }
            }
            if (found != null) {
                storeType(pexp, outer);
                return true;
            }
        }

        boolean foundGetterOrSetter = false;
        String capName = capitalize(propertyName);
        Set<ClassNode> handledNodes = new HashSet<>();
        List<Receiver<String>> receivers = new ArrayList<>();
        addReceivers(receivers, makeOwnerList(objectExpression), pexp.isImplicitThis());

        for (Receiver<String> receiver : receivers) {
            ClassNode receiverType = receiver.getType();

            if (receiverType.isArray() && "length".equals(propertyName)) {
                pexp.putNodeMetaData(READONLY_PROPERTY, Boolean.TRUE);
                storeType(pexp, int_TYPE);
                if (visitor != null) {
                    FieldNode length = new FieldNode("length", Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, int_TYPE, receiverType, null);
                    length.setDeclaringClass(receiverType);
                    visitor.visitField(length);
                }
                return true;
            }

            LinkedList<ClassNode> queue = new LinkedList<>();
            queue.add(receiverType);
            if (isPrimitiveType(receiverType)) {
                queue.add(getWrapper(receiverType));
            }
            while (!queue.isEmpty()) {
                ClassNode current = queue.remove();
                if (!handledNodes.add(current)) continue;

                FieldNode field = current.getDeclaredField(propertyName);
                if (field == null) {
                    if (current.getSuperClass() != null)
                        queue.addFirst(current.getSuperClass());
                    Collections.addAll(queue, current.getInterfaces());
                }

                boolean staticOnly = (receiver.getData() == null ? staticOnlyAccess : false);
                // in case of a lookup on java.lang.Class, look for instance methods on Class
                // as well; in case of static property access Class<Type> and Type are listed
                if (isClassClassNodeWrappingConcreteType(current)) staticOnly = false;

                field = allowStaticAccessToMember(field, staticOnly);

                // skip property/accessor checks for "x.@field"
                if (pexp instanceof AttributeExpression) {
                    if (field != null && storeField(field, pexp, receiverType, visitor, receiver.getData(), !readMode)) {
                        return true;
                    }
                    continue;
                }

                // skip property/accessor checks for "field", "this.field", "this.with { field }", etc. in declaring class of field
                if (field != null && enclosingTypes.contains(current)) {
                    if (storeField(field, pexp, receiverType, visitor, receiver.getData(), !readMode)) {
                        return true;
                    }
                }

                MethodNode getter = findGetter(current, "is" + capName, pexp.isImplicitThis());
                getter = allowStaticAccessToMember(getter, staticOnly);
                if (getter == null) getter = findGetter(current, getGetterName(propertyName), pexp.isImplicitThis());
                getter = allowStaticAccessToMember(getter, staticOnly);
                List<MethodNode> setters = findSetters(current, getSetterName(propertyName), /*enforce void:*/false);
                setters = allowStaticAccessToMember(setters, staticOnly);

                // need to visit even if we only look for setters for compatibility
                if (visitor != null && getter != null) visitor.visitMethod(getter);

                PropertyNode property = current.getProperty(propertyName);
                property = allowStaticAccessToMember(property, staticOnly);
                // prefer explicit getter or setter over property if receiver is not 'this'
                if (property == null || !enclosingTypes.contains(receiverType)) {
                    if (readMode) {
                        if (getter != null) {
                            ClassNode returnType = inferReturnTypeGenerics(current, getter, ArgumentListExpression.EMPTY_ARGUMENTS);
                            storeInferredTypeForPropertyExpression(pexp, returnType);
                            storeTargetMethod(pexp, getter);
                            String delegationData = receiver.getData();
                            if (delegationData != null) {
                                pexp.putNodeMetaData(IMPLICIT_RECEIVER, delegationData);
                            }
                            return true;
                        }
                    } else {
                        if (!setters.isEmpty()) {
                            if (visitor != null) {
                                if (field != null) {
                                    visitor.visitField(field);
                                } else {
                                    for (MethodNode setter : setters) {
                                        // visiting setter will not infer the property type since return type is void, so visit a dummy field instead
                                        FieldNode virtual = new FieldNode(propertyName, 0, setter.getParameters()[0].getOriginType(), current, null);
                                        virtual.setDeclaringClass(setter.getDeclaringClass());
                                        visitor.visitField(virtual);
                                    }
                                }
                            }
                            SetterInfo info = new SetterInfo(current, getSetterName(propertyName), setters);
                            BinaryExpression enclosingBinaryExpression = typeCheckingContext.getEnclosingBinaryExpression();
                            if (enclosingBinaryExpression != null) {
                                putSetterInfo(enclosingBinaryExpression.getLeftExpression(), info);
                            }
                            String delegationData = receiver.getData();
                            if (delegationData != null) {
                                pexp.putNodeMetaData(IMPLICIT_RECEIVER, delegationData);
                            }
                            pexp.removeNodeMetaData(READONLY_PROPERTY);
                            return true;
                        } else if (getter != null && field == null) {
                            pexp.putNodeMetaData(READONLY_PROPERTY, Boolean.TRUE); // GROOVY-9127
                        }
                    }
                }

                if (property != null && storeProperty(property, pexp, receiverType, visitor, receiver.getData(), !readMode)) return true;

                if (field != null && storeField(field, pexp, receiverType, visitor, receiver.getData(), !readMode)) return true;

                foundGetterOrSetter = (foundGetterOrSetter || !setters.isEmpty() || getter != null);
            }

            // GROOVY-5568: the property may be defined by DGM
            for (ClassNode dgmReceiver : isPrimitiveType(receiverType) ? new ClassNode[]{receiverType, getWrapper(receiverType)} : new ClassNode[]{receiverType}) {
                List<MethodNode> methods = findDGMMethodsByNameAndArguments(getSourceUnit().getClassLoader(), dgmReceiver, "get" + capName, ClassNode.EMPTY_ARRAY);
                for (MethodNode method : findDGMMethodsByNameAndArguments(getSourceUnit().getClassLoader(), dgmReceiver, "is" + capName, ClassNode.EMPTY_ARRAY)) {
                    if (isPrimitiveBoolean(method.getReturnType())) methods.add(method);
                }
                if (isUsingGenericsOrIsArrayUsingGenerics(dgmReceiver)) {
                    methods.removeIf(method -> // GROOVY-10075: "List<Integer>" vs "List<String>"
                        !typeCheckMethodsWithGenerics(dgmReceiver, ClassNode.EMPTY_ARRAY, method)
                    );
                }
                if (!methods.isEmpty()) {
                    List<MethodNode> bestMethods = chooseBestMethod(dgmReceiver, methods, ClassNode.EMPTY_ARRAY);
                    if (bestMethods.size() == 1) {
                        MethodNode getter = bestMethods.get(0);
                        if (visitor != null) {
                            visitor.visitMethod(getter);
                        }
                        ClassNode returnType = inferReturnTypeGenerics(dgmReceiver, getter, ArgumentListExpression.EMPTY_ARGUMENTS);
                        storeInferredTypeForPropertyExpression(pexp, returnType);
                        if (readMode) storeTargetMethod(pexp, getter);
                        return true;
                    }
                }
            }

            // GROOVY-7996: check if receiver implements get(String)/set(String,Object) or propertyMissing(String) or $static_propertyMissing(String)?
            if (!receiverType.isArray() && !isPrimitiveType(getUnwrapper(receiverType))
                    && pexp.isImplicitThis() && typeCheckingContext.getEnclosingClosure() != null) {
                MethodNode mopMethod;
                if (readMode) {
                    mopMethod = receiverType.getMethod("get", new Parameter[]{new Parameter(STRING_TYPE, "name")});
                } else {
                    mopMethod = receiverType.getMethod("set", new Parameter[]{new Parameter(STRING_TYPE, "name"), new Parameter(OBJECT_TYPE, "value")});
                }
                if (mopMethod == null) mopMethod = receiverType.getMethod("propertyMissing", new Parameter[]{new Parameter(STRING_TYPE, "propertyName")});

                if (mopMethod != null && !mopMethod.isStatic() && !mopMethod.isSynthetic()) {
                    pexp.putNodeMetaData(DYNAMIC_RESOLUTION, Boolean.TRUE);
                    pexp.removeNodeMetaData(DECLARATION_INFERRED_TYPE);
                    pexp.removeNodeMetaData(INFERRED_TYPE);
                    visitor.visitMethod(mopMethod);
                    return true;
                }
            }
        }

        for (Receiver<String> receiver : receivers) {
            ClassNode receiverType = receiver.getType();
            ClassNode propertyType = getTypeForMapPropertyExpression(receiverType, pexp);
            if (propertyType == null)
                propertyType = getTypeForListPropertyExpression(receiverType, pexp);
            if (propertyType == null)
                propertyType = getTypeForSpreadExpression(receiverType, pexp);
            if (propertyType == null)
                continue;
            if (visitor != null) {
                // TODO: type inference on maps and lists, if possible
                PropertyNode node = new PropertyNode(propertyName, Opcodes.ACC_PUBLIC, propertyType, receiver.getType(), null, null, null);
                node.setDeclaringClass(receiver.getType());
                visitor.visitProperty(node);
            }
            storeType(pexp, propertyType);
            String delegationData = receiver.getData();
            if (delegationData != null) pexp.putNodeMetaData(IMPLICIT_RECEIVER, delegationData);
            return true;
        }

        return foundGetterOrSetter;
    }

    private static boolean hasAccessToMember(final ClassNode accessor, final ClassNode receiver, final int modifiers) {
        if (Modifier.isPublic(modifiers)
                || accessor.equals(receiver)
                || accessor.getOuterClasses().contains(receiver)) {
            return true;
        }
        if (!Modifier.isPrivate(modifiers) && Objects.equals(accessor.getPackageName(), receiver.getPackageName())) {
            return true;
        }
        return Modifier.isProtected(modifiers) && accessor.isDerivedFrom(receiver);
    }

    private MethodNode findGetter(final ClassNode current, String name, final boolean searchOuterClasses) {
        MethodNode getterMethod = current.getGetterMethod(name);
        if (getterMethod == null && searchOuterClasses && current.getOuterClass() != null) {
            return findGetter(current.getOuterClass(), name, true);
        }
        return getterMethod;
    }

    private ClassNode getTypeForMultiValueExpression(final ClassNode compositeType, final Expression prop) {
        GenericsType[] gts = compositeType.getGenericsTypes();
        ClassNode itemType = (gts != null && gts.length == 1 ? getCombinedBoundType(gts[0]) : OBJECT_TYPE);

        AtomicReference<ClassNode> propertyType = new AtomicReference<>();
        if (existsProperty(propX(varX("{}", itemType), prop), true, new PropertyLookupVisitor(propertyType))) {
            return extension.buildListType(propertyType.get());
        }
        return null;
    }

    private ClassNode getTypeForSpreadExpression(final ClassNode testClass, final PropertyExpression pexp) {
        if (pexp.isSpreadSafe()) {
            MethodCallExpression mce = callX(varX("_", testClass), "iterator");
            mce.setImplicitThis(false);
            mce.visit(this);
            ClassNode iteratorType = getType(mce);
            if (isOrImplements(iteratorType, Iterator_TYPE)) {
                return getTypeForMultiValueExpression(iteratorType, pexp.getProperty());
            }
        }
        return null;
    }

    private ClassNode getTypeForListPropertyExpression(final ClassNode testClass, final PropertyExpression pexp) {
        if (isOrImplements(testClass, LIST_TYPE)) {
            ClassNode listType = testClass.equals(LIST_TYPE) ? testClass
                    : GenericsUtils.parameterizeType(testClass, LIST_TYPE);
            return getTypeForMultiValueExpression(listType, pexp.getProperty());
        }
        return null;
    }

    private ClassNode getTypeForMapPropertyExpression(final ClassNode testClass, final PropertyExpression pexp) {
        if (isOrImplements(testClass, MAP_TYPE)) {
            ClassNode mapType = testClass.equals(MAP_TYPE) ? testClass
                    : GenericsUtils.parameterizeType(testClass, MAP_TYPE);
            GenericsType[] gts = mapType.getGenericsTypes();//<K,V> params
            if (gts == null || gts.length != 2) gts = new GenericsType[] {
                OBJECT_TYPE.asGenericsType(), OBJECT_TYPE.asGenericsType()
            };

            if (!pexp.isSpreadSafe()) {
                return getCombinedBoundType(gts[1]);
            } else {
                // map*.property syntax acts on Entry
                switch (pexp.getPropertyAsString()) {
                  case "key":
                    pexp.putNodeMetaData(READONLY_PROPERTY,Boolean.TRUE); // GROOVY-10326
                    return makeClassSafe0(LIST_TYPE, gts[0]);
                  case "value":
                    GenericsType v = gts[1];
                    if (!v.isWildcard()
                            && !Modifier.isFinal(v.getType().getModifiers())
                            && typeCheckingContext.isTargetOfEnclosingAssignment(pexp)) {
                        v = GenericsUtils.buildWildcardType(v.getType()); // GROOVY-10325
                    }
                    return makeClassSafe0(LIST_TYPE, v);
                  default:
                    addStaticTypeError("Spread operator on map only allows one of [key,value]", pexp);
                }
            }
        }
        return null;
    }

    /**
     * Filters search result to prevent access to instance members from a static
     * context.
     * <p>
     * Return null if the given member is not static, but we want to access in a
     * static way (staticOnly=true). If we want to access in a non-static way we
     * always return the member, since then access to static members and
     * non-static members is allowed.
     *
     * @return {@code member} or null
     */
    private <T> T allowStaticAccessToMember(final T member, final boolean staticOnly) {
        if (member == null || !staticOnly) return member;

        if (member instanceof List) {
            @SuppressWarnings("unchecked")
            T list = (T) ((List<MethodNode>) member).stream()
                    .map(m -> allowStaticAccessToMember(m, true))
                    .filter(Objects::nonNull).collect(Collectors.toList());
            return list;
        }

        boolean isStatic;
        if (member instanceof FieldNode) {
            isStatic = ((FieldNode) member).isStatic();
        } else if (member instanceof MethodNode) {
            isStatic = ((MethodNode) member).isStatic();
        } else {
            isStatic = ((PropertyNode) member).isStatic();
        }
        return (isStatic ? member : null);
    }

    private boolean storeField(final FieldNode field, final PropertyExpression expressionToStoreOn, final ClassNode receiver, final ClassCodeVisitorSupport visitor, final String delegationData, final boolean lhsOfAssignment) {
        if (visitor != null) visitor.visitField(field);
        checkOrMarkPrivateAccess(expressionToStoreOn, field, lhsOfAssignment);
        boolean accessible = hasAccessToMember(isSuperExpression(expressionToStoreOn.getObjectExpression()) ? typeCheckingContext.getEnclosingClassNode() : receiver, field.getDeclaringClass(), field.getModifiers());

        if (expressionToStoreOn instanceof AttributeExpression) { // TODO: expand to include PropertyExpression
            if (!accessible) {
                addStaticTypeError("The field " + field.getDeclaringClass().getNameWithoutPackage() + "." + field.getName() + " is not accessible", expressionToStoreOn.getProperty());
            }
        }

        storeWithResolve(field.getOriginType(), receiver, field.getDeclaringClass(), field.isStatic(), expressionToStoreOn);
        if (delegationData != null) {
            expressionToStoreOn.putNodeMetaData(IMPLICIT_RECEIVER, delegationData);
        }
        if (field.isFinal()) {
            MethodNode enclosing = typeCheckingContext.getEnclosingMethod();
            if (enclosing == null || !enclosing.getName().endsWith("init>"))
                expressionToStoreOn.putNodeMetaData(READONLY_PROPERTY, Boolean.TRUE); // GROOVY-5450
        } else if (accessible) {
            expressionToStoreOn.removeNodeMetaData(READONLY_PROPERTY);
        }
        return true;
    }

    private boolean storeProperty(final PropertyNode property, final PropertyExpression expression, final ClassNode receiver, final ClassCodeVisitorSupport visitor, final String delegationData, final boolean lhsOfAssignment) {
        if (visitor != null) visitor.visitProperty(property);
        ClassNode propertyType = property.getOriginType();

        storeWithResolve(propertyType, receiver, property.getDeclaringClass(), property.isStatic(), expression);

        if (delegationData != null) {
            expression.putNodeMetaData(IMPLICIT_RECEIVER, delegationData);
        }
        if (Modifier.isFinal(property.getModifiers())) {
            expression.putNodeMetaData(READONLY_PROPERTY, Boolean.TRUE);
            if (!lhsOfAssignment) {
                MethodNode implicitGetter = new MethodNode(property.getGetterNameOrDefault(), Opcodes.ACC_PUBLIC, propertyType, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, null);
                implicitGetter.setDeclaringClass(property.getDeclaringClass());
                extension.onMethodSelection(expression, implicitGetter);
            }
        } else {
            expression.removeNodeMetaData(READONLY_PROPERTY);
            if (lhsOfAssignment) {
                MethodNode implicitSetter = new MethodNode(property.getSetterNameOrDefault(), Opcodes.ACC_PUBLIC, VOID_TYPE, new Parameter[] {new Parameter(propertyType, "value")}, ClassNode.EMPTY_ARRAY, null);
                implicitSetter.setDeclaringClass(property.getDeclaringClass());
                extension.onMethodSelection(expression, implicitSetter);
            }
        }
        return true;
    }

    private void storeWithResolve(ClassNode type, final ClassNode receiver, final ClassNode declaringClass, final boolean isStatic, final Expression expressionToStoreOn) {
        if (!isStatic && GenericsUtils.hasUnresolvedGenerics(type)) {
            type = resolveGenericsWithContext(extractPlaceHolders(receiver, declaringClass), type);
        }
        if (expressionToStoreOn instanceof PropertyExpression) {
            storeInferredTypeForPropertyExpression((PropertyExpression) expressionToStoreOn, type);
        } else {
            storeType(expressionToStoreOn, type);
        }
    }

    private ClassNode resolveGenericsWithContext(final Map<GenericsTypeName, GenericsType> resolvedPlaceholders, final ClassNode currentType) {
        Map<GenericsTypeName, GenericsType> placeholdersFromContext = extractGenericsParameterMapOfThis(typeCheckingContext);
        return resolveClassNodeGenerics(resolvedPlaceholders, placeholdersFromContext, currentType);
    }

    private void storeInferredTypeForPropertyExpression(final PropertyExpression pexp, final ClassNode type) {
        if (pexp.isSpreadSafe()) {
            storeType(pexp, extension.buildListType(type));
        } else {
            storeType(pexp, type);
        }
    }

    @Override
    public void visitProperty(final PropertyNode node) {
        boolean osc = typeCheckingContext.isInStaticContext;
        try {
            typeCheckingContext.isInStaticContext = node.isInStaticContext();
            currentProperty = node;
            visitAnnotations(node);
            visitClassCodeContainer(node.getGetterBlock());
            visitClassCodeContainer(node.getSetterBlock());
        } finally {
            currentProperty = null;
            typeCheckingContext.isInStaticContext = osc;
        }
    }

    @Override
    public void visitField(final FieldNode node) {
        boolean osc = typeCheckingContext.isInStaticContext;
        try {
            typeCheckingContext.isInStaticContext = node.isInStaticContext();
            currentField = node;
            visitAnnotations(node);
            visitInitialExpression(node.getInitialExpression(), new FieldExpression(node), node);
        } finally {
            currentField = null;
            typeCheckingContext.isInStaticContext = osc;
        }
    }

    private void visitInitialExpression(final Expression value, final Expression target, final ASTNode position) {
        if (value != null) {
            ClassNode lType = target.getType();
            applyTargetType(lType, value); // GROOVY-9977

            typeCheckingContext.pushEnclosingBinaryExpression(assignX(target, value, position));

            value.visit(this);
            ClassNode rType = getType(value);
            if (value instanceof ConstructorCallExpression) {
                inferDiamondType((ConstructorCallExpression) value, lType);
            }

            BinaryExpression dummy = typeCheckingContext.popEnclosingBinaryExpression();
            typeCheckAssignment(dummy, target, lType, value, getResultType(lType, ASSIGN, rType, dummy));
        }
    }

    @Override
    public void visitForLoop(final ForStatement forLoop) {
        // collect every variable expression used in the loop body
        Map<VariableExpression, ClassNode> varOrigType = new HashMap<>();
        forLoop.getLoopBlock().visit(new VariableExpressionTypeMemoizer(varOrigType));

        // visit body
        Map<VariableExpression, List<ClassNode>> oldTracker = pushAssignmentTracking();
        Expression collectionExpression = forLoop.getCollectionExpression();
        if (collectionExpression instanceof ClosureListExpression) {
            // for (int i=0; i<...; i++) style loop
            super.visitForLoop(forLoop);
        } else {
            visitStatement(forLoop);
            collectionExpression.visit(this);
            ClassNode collectionType = getType(collectionExpression);
            ClassNode forLoopVariableType = forLoop.getVariableType();
            ClassNode componentType;
            if (isWrapperCharacter(getWrapper(forLoopVariableType)) && isStringType(collectionType)) {
                // we allow auto-coercion here
                componentType = forLoopVariableType;
            } else {
                componentType = inferLoopElementType(collectionType);
            }
            if (getUnwrapper(componentType) == forLoopVariableType) {
                // prefer primitive type over boxed type
                componentType = forLoopVariableType;
            }
            if (!checkCompatibleAssignmentTypes(forLoopVariableType, componentType)) {
                addStaticTypeError("Cannot loop with element of type " + prettyPrintType(forLoopVariableType) + " with collection of type " + prettyPrintType(collectionType), forLoop);
            }
            if (!isDynamicTyped(forLoopVariableType)) {
                // user has specified a type, prefer it over the inferred type
                componentType = forLoopVariableType;
            }
            typeCheckingContext.controlStructureVariables.put(forLoop.getVariable(), componentType);
            try {
                forLoop.getLoopBlock().visit(this);
            } finally {
                typeCheckingContext.controlStructureVariables.remove(forLoop.getVariable());
            }
        }
        if (isSecondPassNeededForControlStructure(varOrigType, oldTracker)) {
            visitForLoop(forLoop);
        }
    }

    /**
     * Returns the inferred loop element type given a loop collection type. Used,
     * for example, to infer the element type of a {@code for (e in list)} loop.
     *
     * @param collectionType the type of the collection
     * @return the inferred component type
     * @see #inferComponentType
     */
    public static ClassNode inferLoopElementType(final ClassNode collectionType) {
        ClassNode componentType = collectionType.getComponentType();
        if (componentType == null) {
            if (isOrImplements(collectionType, ITERABLE_TYPE)) {
                ClassNode col = GenericsUtils.parameterizeType(collectionType, ITERABLE_TYPE);
                componentType = getCombinedBoundType(col.getGenericsTypes()[0]);

            } else if (isOrImplements(collectionType, MAP_TYPE)) { // GROOVY-6240
                ClassNode col = GenericsUtils.parameterizeType(collectionType, MAP_TYPE);
                componentType = makeClassSafe0(MAP_ENTRY_TYPE, col.getGenericsTypes());

            } else if (isOrImplements(collectionType, STREAM_TYPE)) { // GROOVY-10476
                ClassNode col = GenericsUtils.parameterizeType(collectionType, STREAM_TYPE);
                componentType = getCombinedBoundType(col.getGenericsTypes()[0]);

            } else if (isOrImplements(collectionType, ENUMERATION_TYPE)) { // GROOVY-6123
                ClassNode col = GenericsUtils.parameterizeType(collectionType, ENUMERATION_TYPE);
                componentType = getCombinedBoundType(col.getGenericsTypes()[0]);

            } else if (isOrImplements(collectionType, Iterator_TYPE)) { // GROOVY-10712
                ClassNode col = GenericsUtils.parameterizeType(collectionType, Iterator_TYPE);
                componentType = getCombinedBoundType(col.getGenericsTypes()[0]);

            } else if (isStringType(collectionType)) {
                componentType = STRING_TYPE;
            } else {
                componentType = OBJECT_TYPE;
            }
        }
        return componentType;
    }

    protected boolean isSecondPassNeededForControlStructure(final Map<VariableExpression, ClassNode> varOrigType, final Map<VariableExpression, List<ClassNode>> oldTracker) {
        for (Map.Entry<VariableExpression, ClassNode> entry : popAssignmentTracking(oldTracker).entrySet()) {
            Variable key = findTargetVariable(entry.getKey());
            if (key instanceof VariableExpression && varOrigType.containsKey(key)) {
                ClassNode origType = varOrigType.get(key);
                ClassNode newType = entry.getValue();
                if (!newType.equals(origType)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void visitWhileLoop(final WhileStatement loop) {
        Map<VariableExpression, List<ClassNode>> oldTracker = pushAssignmentTracking();
        super.visitWhileLoop(loop);
        popAssignmentTracking(oldTracker);
    }

    @Override
    public void visitBitwiseNegationExpression(final BitwiseNegationExpression expression) {
        super.visitBitwiseNegationExpression(expression);
        ClassNode type = getType(expression);
        ClassNode typeRe = type.redirect();
        ClassNode resultType;
        if (isBigIntCategory(typeRe)) {
            // allow any internal number that is not a floating point one
            resultType = type;
        } else if (isStringType(typeRe) || isGStringType(typeRe)) {
            resultType = PATTERN_TYPE;
        } else if (typeRe.equals(ArrayList_TYPE)) {
            resultType = ArrayList_TYPE;
        } else if (typeRe.equals(PATTERN_TYPE)) {
            resultType = PATTERN_TYPE;
        } else {
            MethodNode mn = findMethodOrFail(expression, type, "bitwiseNegate");
            if (mn != null) {
                resultType = mn.getReturnType();
            } else {
                resultType = OBJECT_TYPE;
            }
        }
        storeType(expression, resultType);
    }

    @Override
    public void visitUnaryPlusExpression(final UnaryPlusExpression expression) {
        super.visitUnaryPlusExpression(expression);
        negativeOrPositiveUnary(expression, "positive");
    }

    @Override
    public void visitUnaryMinusExpression(final UnaryMinusExpression expression) {
        super.visitUnaryMinusExpression(expression);
        negativeOrPositiveUnary(expression, "negative");
    }

    @Override
    public void visitPostfixExpression(final PostfixExpression expression) {
        Expression operand = expression.getExpression();
        int operator = expression.getOperation().getType();
        visitPrefixOrPostifExpression(expression, operand, operator);
    }

    @Override
    public void visitPrefixExpression(final PrefixExpression expression) {
        Expression operand = expression.getExpression();
        int operator = expression.getOperation().getType();
        visitPrefixOrPostifExpression(expression, operand, operator);
    }

    private void visitPrefixOrPostifExpression(final Expression origin, final Expression operand, final int operator) {
        Optional<Token> token = TokenUtil.asAssignment(operator);
        // push "operand += 1" or "operand -= 1" onto stack for LHS checks
        token.ifPresent(value -> typeCheckingContext.pushEnclosingBinaryExpression(binX(operand, value, constX(1))));
        try {
            operand.visit(this);
            SetterInfo setterInfo = removeSetterInfo(operand);
            if (setterInfo != null) {
                BinaryExpression rewrite = typeCheckingContext.getEnclosingBinaryExpression();
                rewrite.setSourcePosition(origin);
                if (ensureValidSetter(rewrite, operand, rewrite.getRightExpression(), setterInfo)) {
                    return;
                }
            }

            ClassNode operandType = getType(operand);
            boolean isPostfix = (origin instanceof PostfixExpression);
            String name = (operator == PLUS_PLUS ? "next" : operator == MINUS_MINUS ? "previous" : null);

            if (name != null && isNumberType(operandType)) {
                if (!isPrimitiveType(operandType)) {
                    MethodNode node = findMethodOrFail(varX("_dummy_", operandType), operandType, name);
                    if (node != null) {
                        storeTargetMethod(origin, node);
                        storeType(origin, isPostfix ? operandType : getMathWideningClassNode(operandType));
                        return;
                    }
                }
                storeType(origin, operandType);
                return;
            }
            if (name != null && operandType.isDerivedFrom(Number_TYPE)) {
                // special case for numbers, improve type checking as we can expect ++ and -- to return the same type
                MethodNode node = findMethodOrFail(operand, operandType, name);
                if (node != null) {
                    storeTargetMethod(origin, node);
                    storeType(origin, getMathWideningClassNode(operandType));
                    return;
                }
            }
            if (name == null) {
                addUnsupportedPreOrPostfixExpressionError(origin);
                return;
            }

            MethodNode node = findMethodOrFail(operand, operandType, name);
            if (node != null) {
                storeTargetMethod(origin, node);
                storeType(origin, isPostfix ? operandType : inferReturnTypeGenerics(operandType, node, ArgumentListExpression.EMPTY_ARGUMENTS));
            }
        } finally {
            if (token.isPresent()) typeCheckingContext.popEnclosingBinaryExpression();
        }
    }

    private static ClassNode getMathWideningClassNode(final ClassNode type) {
        if (isPrimitiveByte(type) || isPrimitiveShort(type) || isPrimitiveInt(type)) {
            return int_TYPE;
        }
        if (isWrapperByte(type) || isWrapperShort(type) || isWrapperInteger(type)) {
            return Integer_TYPE;
        }
        if (isPrimitiveFloat(type)) {
            return double_TYPE;
        }
        if (isWrapperFloat(type)) {
            return Double_TYPE;
        }
        return type;
    }

    private void negativeOrPositiveUnary(final Expression expression, final String name) {
        ClassNode type = getType(expression);
        ClassNode typeRe = type.redirect();
        ClassNode resultType;
        if (isDoubleCategory(getUnwrapper(typeRe))) {
            resultType = type;
        } else if (typeRe.equals(ArrayList_TYPE)) {
            resultType = ArrayList_TYPE;
        } else {
            MethodNode mn = findMethodOrFail(expression, type, name);
            if (mn != null) {
                resultType = mn.getReturnType();
            } else {
                resultType = type;
            }
        }
        storeType(expression, resultType);
    }

    @Override
    public void visitExpressionStatement(final ExpressionStatement statement) {
        typeCheckingContext.pushTemporaryTypeInfo();
        super.visitExpressionStatement(statement);
        typeCheckingContext.popTemporaryTypeInfo();
    }

    @Override
    public void visitReturnStatement(final ReturnStatement statement) {
        if (typeCheckingContext.getEnclosingClosure() == null) {
            MethodNode method = typeCheckingContext.getEnclosingMethod();
            if (method != null && !method.isVoidMethod() && !method.isDynamicReturnType()) {
                applyTargetType(method.getReturnType(), statement.getExpression()); // GROOVY-10660
            }
        }
        super.visitReturnStatement(statement);
        returnListener.returnStatementAdded(statement);
    }

    protected ClassNode checkReturnType(final ReturnStatement statement) {
        Expression expression = statement.getExpression();
        ClassNode type = getType(expression);

        TypeCheckingContext.EnclosingClosure enclosingClosure = typeCheckingContext.getEnclosingClosure();
        if (enclosingClosure != null) {
            if (enclosingClosure.getClosureExpression().getNodeMetaData(INFERRED_TYPE) != null) return null; // re-visit

            ClassNode inferredReturnType = getInferredReturnType(enclosingClosure.getClosureExpression());
            // GROOVY-9995: return ctor call with diamond operator
            if (expression instanceof ConstructorCallExpression) {
                inferDiamondType((ConstructorCallExpression) expression, inferredReturnType != null ? inferredReturnType : /*GROOVY-10080:*/dynamicType());
            }
            if (inferredReturnType != null
                    && !inferredReturnType.equals(type)
                    && !isObjectType(inferredReturnType)
                    && !isPrimitiveVoid(inferredReturnType)
                    && !isPrimitiveBoolean(inferredReturnType)
                    && !GenericsUtils.hasUnresolvedGenerics(inferredReturnType)) {
                if (isStringType(inferredReturnType) && isGStringOrGStringStringLUB(type)) {
                    type = STRING_TYPE; // GROOVY-9971: convert GString to String at point of return
                } else if (GenericsUtils.buildWildcardType(wrapTypeIfNecessary(inferredReturnType)).isCompatibleWith(wrapTypeIfNecessary(type))) {
                    type = inferredReturnType; // GROOVY-8310, GROOVY-10082, GROOVY-10091, GROOVY-10128, GROOVY-10306: allow simple covariance
                } else if (!isPrimitiveVoid(type) && !extension.handleIncompatibleReturnType(statement, type)) { // GROOVY-10277: incompatible return value
                    addStaticTypeError("Cannot return value of type " + prettyPrintType(type) + " for " + (enclosingClosure.getClosureExpression() instanceof LambdaExpression ? "lambda" : "closure") + " expecting " + prettyPrintType(inferredReturnType), expression);
                }
            }
            return type;
        }

        MethodNode enclosingMethod = typeCheckingContext.getEnclosingMethod();
        if (enclosingMethod != null && !enclosingMethod.isVoidMethod() && !enclosingMethod.isDynamicReturnType()) {
            ClassNode returnType = enclosingMethod.getReturnType();
            if (!isPrimitiveVoid(getUnwrapper(type)) && !checkCompatibleAssignmentTypes(returnType, type, null, false)) {
                if (!extension.handleIncompatibleReturnType(statement, type)) {
                    addStaticTypeError("Cannot return value of type " + prettyPrintType(type) + " for method returning " + prettyPrintType(returnType), expression);
                }
            } else if (implementsInterfaceOrIsSubclassOf(type, returnType)) {
                BinaryExpression dummy = assignX(varX("{target}", returnType), expression, statement);
                ClassNode resultType = getResultType(returnType, ASSIGN, type, dummy); // GROOVY-10295
                checkTypeGenerics(returnType, resultType, expression);
            }
        }
        return null;
    }

    protected void addClosureReturnType(final ClassNode returnType) {
        if (returnType != null && !isPrimitiveVoid(returnType)) // GROOVY-8202
            typeCheckingContext.getEnclosingClosure().addReturnType(returnType);
    }

    @Override
    public void visitConstructorCallExpression(final ConstructorCallExpression call) {
        if (!extension.beforeMethodCall(call)) {
            ClassNode receiver;
            if (call.isThisCall()) {
                receiver = makeThis();
            } else if (call.isSuperCall()) {
                receiver = makeSuper();
            } else {
                receiver = call.getType();
            }
            Expression arguments = call.getArguments();
            ArgumentListExpression argumentList = InvocationWriter.makeArgumentList(arguments);

            checkForbiddenSpreadArgument(argumentList);
            visitMethodCallArguments(receiver, argumentList, false, null);
            final ClassNode[] argumentTypes = getArgumentTypes(argumentList);

            MethodNode ctor;
            if (looksLikeNamedArgConstructor(receiver, argumentTypes)
                    && findMethod(receiver, "<init>", argumentTypes).isEmpty()
                    && findMethod(receiver, "<init>", init(argumentTypes)).size() == 1) {
                ctor = typeCheckMapConstructor(call, receiver, arguments);
            } else {
                ctor = findMethodOrFail(call, receiver, "<init>", argumentTypes);
                if (ctor != null) {
                    Parameter[] parameters = ctor.getParameters();
                    if (looksLikeNamedArgConstructor(receiver, argumentTypes)
                            && parameters.length == argumentTypes.length - 1) {
                        ctor = typeCheckMapConstructor(call, receiver, arguments);
                    } else {
                        GenericsType[] typeParameters = ctor.getDeclaringClass().getGenericsTypes();
                        if (typeParameters != null) { // GROOVY-10283, GROOVY-10316, GROOVY-10482, GROOVY-10624, GROOVY-10698
                            Map<GenericsTypeName, GenericsType> context = extractGenericsConnectionsFromArguments(typeParameters, parameters, argumentList, receiver.getGenericsTypes());
                            if (!context.isEmpty()) parameters = Arrays.stream(parameters).map(p -> new Parameter(applyGenericsContext(context, p.getType()), p.getName())).toArray(Parameter[]::new);
                        }
                        resolvePlaceholdersFromImplicitTypeHints(argumentTypes, argumentList, parameters);
                        typeCheckMethodsWithGenericsOrFail(receiver, argumentTypes, ctor, call);
                        visitMethodCallArguments(receiver, argumentList, true, ctor);
                    }
                }
            }
            if (ctor != null) {
                storeTargetMethod(call, ctor);
            }
        }

        // GROOVY-9327: check for AIC in STC method with non-STC enclosing class
        if (call.isUsingAnonymousInnerClass()) {
            Set<MethodNode> methods = typeCheckingContext.methodsToBeVisited;
            if (!methods.isEmpty()) { // indicates specific methods have STC
                typeCheckingContext.methodsToBeVisited = Collections.emptySet();

                ClassNode anonType = call.getType();
                visitClass(anonType); // visit anon. inner class inline with method
                anonType.putNodeMetaData(StaticTypeCheckingVisitor.class, Boolean.TRUE);

                typeCheckingContext.methodsToBeVisited = methods;
            }
        }

        extension.afterMethodCall(call);
    }

    private boolean looksLikeNamedArgConstructor(final ClassNode receiver, final ClassNode[] argumentTypes) {
        if (argumentTypes.length == 1 || argumentTypes.length == 2 && argumentTypes[0].equals(receiver.getOuterClass())) {
            return isOrImplements(argumentTypes[argumentTypes.length - 1], MAP_TYPE);
        }
        return false;
    }

    protected MethodNode typeCheckMapConstructor(final ConstructorCallExpression call, final ClassNode receiver, final Expression arguments) {
        MethodNode node = null;
        if (arguments instanceof TupleExpression) {
            TupleExpression texp = (TupleExpression) arguments;
            List<Expression> expressions = texp.getExpressions();
            // should only get here with size = 2 when inner class constructor
            if (expressions.size() == 1 || expressions.size() == 2) {
                Expression expression = expressions.get(expressions.size() - 1);
                if (expression instanceof MapExpression) {
                    MapExpression argList = (MapExpression) expression;
                    checkGroovyConstructorMap(call, receiver, argList);
                    Parameter[] params = expressions.size() == 1
                            ? new Parameter[]{new Parameter(MAP_TYPE, "map")}
                            : new Parameter[]{new Parameter(receiver.redirect().getOuterClass(), "$p$"), new Parameter(MAP_TYPE, "map")};
                    node = new ConstructorNode(Opcodes.ACC_PUBLIC, params, ClassNode.EMPTY_ARRAY, GENERATED_EMPTY_STATEMENT);
                    node.setDeclaringClass(receiver);
                }
            }
        }
        return node;
    }

    protected ClassNode[] getArgumentTypes(final ArgumentListExpression argumentList) {
        return argumentList.getExpressions().stream().map(exp ->
            isNullConstant(exp) ? UNKNOWN_PARAMETER_TYPE : getType(exp)
        ).toArray(ClassNode[]::new);
    }

    private ClassNode getInferredTypeFromTempInfo(final Expression expression, final ClassNode expressionType) {
        if (expression instanceof VariableExpression && !typeCheckingContext.temporaryIfBranchTypeInformation.isEmpty()) {
            List<ClassNode> tempTypes = getTemporaryTypesForExpression(expression);
            if (tempTypes != null && !tempTypes.isEmpty()) {
                List<ClassNode> types = new ArrayList<>(tempTypes.size() + 1);
                if (expressionType != null && !isObjectType(expressionType) // GROOVY-7333
                        && tempTypes.stream().noneMatch(t -> implementsInterfaceOrIsSubclassOf(t, expressionType))) { // GROOVY-9769
                    types.add(expressionType);
                }
                types.addAll(tempTypes);

                if (types.isEmpty()) {
                    return OBJECT_TYPE;
                } else if (types.size() == 1) {
                    return types.get(0);
                } else {
                    return new UnionTypeClassNode(types.toArray(ClassNode.EMPTY_ARRAY));
                }
            }
        }
        return expressionType;
    }

    @Override
    public void visitClosureExpression(final ClosureExpression expression) {
        // collect every variable expression used in the closure body
        Map<VariableExpression, ClassNode> varTypes = new HashMap<>();
        expression.getCode().visit(new VariableExpressionTypeMemoizer(varTypes, true));
        Map<VariableExpression, List<ClassNode>> oldTracker = pushAssignmentTracking();
        SharedVariableCollector collector = new SharedVariableCollector(getSourceUnit());
        collector.visitClosureExpression(expression);

        Set<VariableExpression> closureSharedVariables = collector.getClosureSharedExpressions();
        Map<VariableExpression, Map<StaticTypesMarker, Object>> variableMetadata;
        if (!closureSharedVariables.isEmpty()) {
            // GROOVY-6921: call getType in order to update closure shared variables
            // whose types are inferred thanks to closure parameter type inference
            for (VariableExpression ve : closureSharedVariables) {
                getType(ve);
            }
            variableMetadata = new HashMap<>();
            saveVariableExpressionMetadata(closureSharedVariables, variableMetadata);
        } else {
            variableMetadata = null;
        }

        // perform visit
        typeCheckingContext.pushEnclosingClosureExpression(expression);
        DelegationMetadata dmd = getDelegationMetadata(expression);
        if (dmd != null) {
            typeCheckingContext.delegationMetadata = newDelegationMetadata(dmd.getType(), dmd.getStrategy());
        } else {
            typeCheckingContext.delegationMetadata = newDelegationMetadata(typeCheckingContext.getEnclosingClassNode(), Closure.OWNER_FIRST);
        }
        super.visitClosureExpression(expression);
        typeCheckingContext.delegationMetadata = typeCheckingContext.delegationMetadata.getParent();

        returnAdder.visitMethod(new MethodNode("dummy", 0, OBJECT_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, expression.getCode()));
        TypeCheckingContext.EnclosingClosure enclosingClosure = typeCheckingContext.popEnclosingClosure();
        if (!enclosingClosure.getReturnTypes().isEmpty()) { // populated by ReturnAdder
            ClassNode returnType = lowestUpperBound(enclosingClosure.getReturnTypes());
            storeInferredReturnType(expression, wrapTypeIfNecessary(returnType));
            storeType(expression, wrapClosureType(returnType));
        }

        // check types of closure shared variables for change
        if (isSecondPassNeededForControlStructure(varTypes, oldTracker)) {
            visitClosureExpression(expression);
        }

        // restore original metadata
        restoreVariableExpressionMetadata(variableMetadata);
        for (Parameter parameter : getParametersSafe(expression)) {
            typeCheckingContext.controlStructureVariables.remove(parameter);
            // GROOVY-10072: visit param default argument expression if present
            visitInitialExpression(parameter.getInitialExpression(), varX(parameter), parameter);
        }
    }

    @Override
    public void visitMethodPointerExpression(final MethodPointerExpression expression) {
        super.visitMethodPointerExpression(expression);
        Expression nameExpr = expression.getMethodName();
        if (nameExpr instanceof ConstantExpression
                && isStringType(getType(nameExpr))) {
            String nameText = nameExpr.getText();

            if ("new".equals(nameText)) {
                ClassNode receiverType = getType(expression.getExpression());
                if (isClassClassNodeWrappingConcreteType(receiverType)) {
                    storeType(expression, wrapClosureType(receiverType));
                }
                return;
            }

            List<Receiver<String>> receivers = new ArrayList<>();
            addReceivers(receivers, makeOwnerList(expression.getExpression()), false);

            ClassNode receiverType = null;
            List<MethodNode> candidates = EMPTY_METHODNODE_LIST;
            for (Receiver<String> currentReceiver : receivers) {
                receiverType = wrapTypeIfNecessary(currentReceiver.getType());

                candidates = findMethodsWithGenerated(receiverType, nameText);
                candidates.addAll(findDGMMethodsForClassNode(getSourceUnit().getClassLoader(), receiverType, nameText));
                candidates = filterMethodsByVisibility(candidates, typeCheckingContext.getEnclosingClassNode());

                if (!candidates.isEmpty()) {
                    break;
                }
            }

            if (candidates.isEmpty()) {
                candidates = extension.handleMissingMethod(
                    getType(expression.getExpression()), nameText, null, null, null);
            } else if (candidates.size() > 1) {
                candidates = extension.handleAmbiguousMethods(candidates, expression);
            }

            if (!candidates.isEmpty()) {
                int nParameters = candidates.stream().mapToInt(m -> m.getParameters().length).reduce((i,j) -> i == j ? i : -1).getAsInt();
                Map<GenericsTypeName, GenericsType> gts = GenericsUtils.extractPlaceholders(receiverType);
                candidates.stream().map(candidate -> applyGenericsContext(gts, candidate.getReturnType()))
                        .reduce(WideningCategories::lowestUpperBound).ifPresent(returnType -> {
                            ClassNode closureType = wrapClosureType(returnType);
                            closureType.putNodeMetaData(CLOSURE_ARGUMENTS, nParameters); // GROOVY-10714
                            storeType(expression, closureType);
                        });
                expression.putNodeMetaData(MethodNode.class, candidates);
            } else if (!(expression instanceof MethodReferenceExpression)) {
                ClassNode type = wrapTypeIfNecessary(getType(expression.getExpression()));
                if (isClassClassNodeWrappingConcreteType(type)) type = type.getGenericsTypes()[0].getType();
                addStaticTypeError("Cannot find matching method " + prettyPrintTypeName(type) + "#" + nameText + ". Please check if the declared type is correct and if the method exists.", nameExpr);
            }
        }
    }

    private static ClassNode wrapClosureType(final ClassNode returnType) {
        return makeClassSafe0(CLOSURE_TYPE, wrapTypeIfNecessary(returnType).asGenericsType());
    }

    protected DelegationMetadata getDelegationMetadata(final ClosureExpression expression) {
        return expression.getNodeMetaData(DELEGATION_METADATA);
    }

    private DelegationMetadata newDelegationMetadata(final ClassNode delegateType, final int resolveStrategy) {
        return new DelegationMetadata(delegateType, resolveStrategy, typeCheckingContext.delegationMetadata);
    }

    protected void restoreVariableExpressionMetadata(final Map<VariableExpression, Map<StaticTypesMarker, Object>> typesBeforeVisit) {
        if (typesBeforeVisit != null) {
            typesBeforeVisit.forEach((var, map) -> {
                for (StaticTypesMarker marker : StaticTypesMarker.values()) {
                    // GROOVY-9344, GROOVY-9516: keep type
                    if (marker == INFERRED_TYPE) continue;

                    Object value = map.get(marker);
                    if (value == null) {
                        var.removeNodeMetaData(marker);
                    } else {
                        var.putNodeMetaData(marker, value);
                    }
                }
                var.removeNodeMetaData(DECLARATION_INFERRED_TYPE); // GROOVY-8946
            });
        }
    }

    protected void saveVariableExpressionMetadata(final Set<VariableExpression> closureSharedExpressions, final Map<VariableExpression, Map<StaticTypesMarker, Object>> typesBeforeVisit) {
        for (VariableExpression ve : closureSharedExpressions) {
            Variable v;
            while ((v = ve.getAccessedVariable()) != ve && v instanceof VariableExpression) {
                ve = (VariableExpression) v;
            }

            Map<StaticTypesMarker, Object> metadata = new EnumMap<>(StaticTypesMarker.class);
            for (StaticTypesMarker marker : StaticTypesMarker.values()) {
                Object value = ve.getNodeMetaData(marker);
                if (value != null) {
                    metadata.put(marker, value);
                }
            }
            typesBeforeVisit.put(ve, metadata);
        }
    }

    @Override
    public void visitConstructor(final ConstructorNode node) {
        if (shouldSkipMethodNode(node)) {
            return;
        }
        super.visitConstructor(node);
    }

    @Override
    public void visitMethod(final MethodNode node) {
        if (shouldSkipMethodNode(node)) {
            return;
        }
        if (!extension.beforeVisitMethod(node)) {
            ErrorCollector collector = node.getNodeMetaData(ERROR_COLLECTOR);
            if (collector != null) {
                typeCheckingContext.getErrorCollector().addCollectorContents(collector);
            } else {
                startMethodInference(node, typeCheckingContext.getErrorCollector());
            }
            node.removeNodeMetaData(ERROR_COLLECTOR);
        }
        extension.afterVisitMethod(node);
    }

    protected void startMethodInference(final MethodNode node, final ErrorCollector collector) {
        // second, we must ensure that this method MUST be statically checked
        // for example, in a mixed mode where only some methods are statically checked
        // we must not visit a method which used dynamic dispatch.
        // We do not check for an annotation because some other AST transformations
        // may use this visitor without the annotation being explicitly set
        if ((typeCheckingContext.methodsToBeVisited.isEmpty() || typeCheckingContext.methodsToBeVisited.contains(node))
                && typeCheckingContext.alreadyVisitedMethods.add(node)) { // prevent re-visiting method (infinite loop)
            typeCheckingContext.pushErrorCollector(collector);
            boolean osc = typeCheckingContext.isInStaticContext;
            try {
                // GROOVY-7890: non-static trait method is static in helper type
                typeCheckingContext.isInStaticContext = isNonStaticHelperMethod(node) ? false : node.isStatic();

                super.visitMethod(node);
            } finally {
                typeCheckingContext.isInStaticContext = osc;
            }
            typeCheckingContext.popErrorCollector();
            node.putNodeMetaData(ERROR_COLLECTOR, collector);
        }
    }

    @Override
    protected void visitConstructorOrMethod(final MethodNode node, final boolean isConstructor) {
        typeCheckingContext.pushEnclosingMethod(node);
        final ClassNode returnType = node.getReturnType(); // GROOVY-10660: implicit return case
        if (!isConstructor && (isClosureWithType(returnType) || isFunctionalInterface(returnType))) {
            new ReturnAdder(returnStmt -> applyTargetType(returnType, returnStmt.getExpression())).visitMethod(node);
        }
        readClosureParameterAnnotation(node); // GROOVY-6603
        super.visitConstructorOrMethod(node, isConstructor);
        if (node.hasDefaultValue()) {
            visitDefaultParameterArguments(node.getParameters());
        }
        if (!isConstructor) {
            returnAdder.visitMethod(node); // GROOVY-7753: we cannot count these auto-generated return statements, see `typeCheckingContext.pushEnclosingReturnStatement`
        }
        typeCheckingContext.popEnclosingMethod();
    }

    private void readClosureParameterAnnotation(final MethodNode node) {
        for (Parameter parameter : node.getParameters()) {
            for (AnnotationNode annotation : parameter.getAnnotations()) {
                if (annotation.getClassNode().equals(CLOSUREPARAMS_CLASSNODE)) {
                    // GROOVY-6603: propagate closure parameter types
                    Expression value = annotation.getMember("value");
                    Expression options = annotation.getMember("options");
                    List<ClassNode[]> signatures = getSignaturesFromHint(node, value, options, annotation);
                    if (signatures.size() == 1) { // TODO: handle multiple signatures
                        parameter.putNodeMetaData(CLOSURE_ARGUMENTS, Arrays.stream(signatures.get(0)).map(t -> new Parameter(t,"")).toArray(Parameter[]::new));
                    }
                }
            }
        }
    }

    private void visitDefaultParameterArguments(final Parameter[] parameters) {
        for (Parameter parameter : parameters) {
            if (!parameter.hasInitialExpression()) continue;
            // GROOVY-10094: visit param default argument expression
            visitInitialExpression(parameter.getInitialExpression(), varX(parameter), parameter);
            // GROOVY-10104: remove direct target setting to prevent errors
            parameter.getInitialExpression().visit(new CodeVisitorSupport() {
                @Override
                public void visitMethodCallExpression(final MethodCallExpression mce) {
                    super.visitMethodCallExpression(mce);
                    mce.setMethodTarget(null);
                }
            });
        }
    }

    @Override
    protected void visitObjectInitializerStatements(final ClassNode node) {
        // GROOVY-5450: create fake constructor node so final field analysis can allow write within non-static initializer block(s)
        ConstructorNode init = new ConstructorNode(0, null, null, new BlockStatement(node.getObjectInitializerStatements(), null));
        typeCheckingContext.pushEnclosingMethod(init);
        super.visitObjectInitializerStatements(node);
        typeCheckingContext.popEnclosingMethod();
    }

    protected void addTypeCheckingInfoAnnotation(final MethodNode node) {
        // TypeChecked$TypeCheckingInfo cannot be applied on constructors
        if (node.isConstructor()) return;

        // if a returned inferred type is available and no @TypeCheckingInfo is on node, then add an
        // annotation to the method node
        ClassNode rtype = getInferredReturnType(node);
        if (rtype != null && node.getAnnotations(TYPECHECKING_INFO_NODE).isEmpty()) {
            AnnotationNode anno = new AnnotationNode(TYPECHECKING_INFO_NODE);
            anno.setMember("version", CURRENT_SIGNATURE_PROTOCOL);
            SignatureCodec codec = SignatureCodecFactory.getCodec(CURRENT_SIGNATURE_PROTOCOL_VERSION, getTransformLoader());
            String genericsSignature = codec.encode(rtype);
            if (genericsSignature != null) {
                ConstantExpression signature = new ConstantExpression(genericsSignature);
                signature.setType(STRING_TYPE);
                anno.setMember("inferredType", signature);
                node.addAnnotation(anno);
            }
        }
    }

    @Override
    public void visitStaticMethodCallExpression(final StaticMethodCallExpression call) {
        String name = call.getMethod();
        if (name == null) {
            addStaticTypeError("cannot resolve dynamic method name at compile time.", call);
            return;
        }

        if (extension.beforeMethodCall(call)) {
            extension.afterMethodCall(call);
            return;
        }

        Expression callArguments = call.getArguments();

        ArgumentListExpression argumentList = InvocationWriter.makeArgumentList(callArguments);

        checkForbiddenSpreadArgument(argumentList);

        ClassNode receiver = call.getOwnerType();
        visitMethodCallArguments(receiver, argumentList, false, null);

        ClassNode[] args = getArgumentTypes(argumentList);

        try {
            // method call receivers are :
            //   - possible "with" receivers
            //   - the actual receiver as found in the method call expression
            //   - any of the potential receivers found in the instanceof temporary table
            // in that order
            List<Receiver<String>> receivers = new LinkedList<>();
            addReceivers(receivers, makeOwnerList(new ClassExpression(receiver)), false);
            List<MethodNode> mn = null;
            Receiver<String> chosenReceiver = null;
            for (Receiver<String> currentReceiver : receivers) {
                mn = findMethod(currentReceiver.getType(), name, args);
                if (!mn.isEmpty()) {
                    if (mn.size() == 1) {
                        // GROOVY-8909, GROOVY-8961, GROOVY-9734, GROOVY-9844, GROOVY-9915, et al.
                        resolvePlaceholdersFromImplicitTypeHints(args, argumentList, mn.get(0).getParameters());
                        typeCheckMethodsWithGenericsOrFail(currentReceiver.getType(), args, mn.get(0), call);
                    }
                    chosenReceiver = currentReceiver;
                    break;
                }
            }
            if (mn.isEmpty()) {
                mn = extension.handleMissingMethod(receiver, name, argumentList, args, call);
            }
            boolean callArgsVisited = false;
            if (mn.isEmpty()) {
                addNoMatchingMethodError(receiver, name, args, call);
            } else {
                mn = disambiguateMethods(mn, receiver, args, call);
                if (mn.size() == 1) {
                    MethodNode directMethodCallCandidate = mn.get(0);
                    ClassNode returnType = getType(directMethodCallCandidate);
                    if (returnType.isUsingGenerics() && !returnType.isEnum()) {
                        visitMethodCallArguments(receiver, argumentList, true, directMethodCallCandidate); callArgsVisited = true;
                        ClassNode rt = inferReturnTypeGenerics(chosenReceiver.getType(), directMethodCallCandidate, argumentList);
                        if (rt != null && implementsInterfaceOrIsSubclassOf(rt, returnType))
                            returnType = rt;
                    }
                    storeType(call, returnType);
                    storeTargetMethod(call, directMethodCallCandidate);
                } else {
                    addAmbiguousErrorMessage(mn, name, args, call);
                }
                if (!callArgsVisited) {
                    visitMethodCallArguments(receiver, argumentList, true, call.getNodeMetaData(DIRECT_METHOD_CALL_TARGET));
                }
            }
        } finally {
            extension.afterMethodCall(call);
        }
    }

    /**
     * @deprecated this method is unused, replaced with {@link DelegatesTo} inference.
     */
    @Deprecated
    protected void checkClosureParameters(final Expression callArguments, final ClassNode receiver) {
        if (callArguments instanceof ArgumentListExpression) {
            ArgumentListExpression argList = (ArgumentListExpression) callArguments;
            ClosureExpression closure = (ClosureExpression) argList.getExpression(0);
            Parameter[] parameters = closure.getParameters();
            if (parameters.length > 1) {
                addStaticTypeError("Unexpected number of parameters for a with call", argList);
            } else if (parameters.length == 1) {
                Parameter param = parameters[0];
                if (!param.isDynamicTyped() && !isAssignableTo(receiver, param.getType().redirect())) {
                    addStaticTypeError("Expected parameter type: " + prettyPrintType(receiver) + " but was: " + prettyPrintType(param.getType()), param);
                }
            }
            closure.putNodeMetaData(DELEGATION_METADATA, newDelegationMetadata(receiver, Closure.DELEGATE_FIRST));
        }
    }

    /**
     * Visits a method call target, to infer the type. Don't report errors right
     * away, that will be done by a later visitMethod call.
     */
    protected void silentlyVisitMethodNode(final MethodNode directMethodCallCandidate) {
        // visit is authorized because the classnode belongs to the same source unit
        ErrorCollector collector = new ErrorCollector(typeCheckingContext.getErrorCollector().getConfiguration());
        startMethodInference(directMethodCallCandidate, collector);
    }

    protected void visitMethodCallArguments(final ClassNode receiver, final ArgumentListExpression arguments, final boolean visitClosures, final MethodNode selectedMethod) {
        Parameter[] parameters;
        List<Expression> expressions = new ArrayList<>();
        if (selectedMethod instanceof ExtensionMethodNode) {
            parameters = ((ExtensionMethodNode) selectedMethod).getExtensionMethodNode().getParameters();
            expressions.add(varX("$self", receiver));
        } else {
            parameters = selectedMethod != null ? selectedMethod.getParameters() : Parameter.EMPTY_ARRAY;
        }
        expressions.addAll(arguments.getExpressions());

        int nExpressions = expressions.size();
        for (int i = 0; i < nExpressions; i += 1) {
            Expression expression = expressions.get(i);
            if (visitClosures && expression instanceof ClosureExpression
                    || !visitClosures && !(expression instanceof ClosureExpression)) {
                if (i < parameters.length && visitClosures) {
                    Parameter target = parameters[i];
                    ClassNode targetType = target.getType();
                    ClosureExpression source = (ClosureExpression) expression;
                    checkClosureWithDelegatesTo(receiver, selectedMethod, args(expressions), parameters, source, target);
                    if (selectedMethod instanceof ExtensionMethodNode) {
                        if (i > 0) {
                            inferClosureParameterTypes(receiver, arguments, source, target, selectedMethod);
                        }
                    } else {
                        inferClosureParameterTypes(receiver, arguments, source, target, selectedMethod);
                    }
                    if (isFunctionalInterface(targetType)) {
                        storeInferredReturnType(source, GenericsUtils.parameterizeSAM(targetType).getV2());
                    } else if (isClosureWithType(targetType)) {
                        storeInferredReturnType(source, getCombinedBoundType(targetType.getGenericsTypes()[0]));
                    }
                }
                expression.visit(this);
                expression.removeNodeMetaData(DELEGATION_METADATA);
            }
            if (i == 0 && parameters.length > 0 && expression instanceof MapExpression) {
                checkNamedParamsAnnotation(parameters[0], (MapExpression) expression);
            }
        }
        if (visitClosures) {
            inferMethodReferenceType(receiver, arguments, selectedMethod);
        }
    }

    private void checkNamedParamsAnnotation(final Parameter param, final MapExpression args) {
        if (!isOrImplements(param.getType(), MAP_TYPE)) return;
        List<MapEntryExpression> entryExpressions = args.getMapEntryExpressions();
        Map<Object, Expression> entries = new LinkedHashMap<>();
        for (MapEntryExpression entry : entryExpressions) {
            Object key = entry.getKeyExpression();
            if (key instanceof ConstantExpression) {
                key = ((ConstantExpression) key).getValue();
            }
            entries.put(key, entry.getValueExpression());
        }
        List<String> collectedNames = new ArrayList<>();
        List<AnnotationNode> annotations = param.getAnnotations(NAMED_PARAMS_CLASSNODE);
        if (annotations != null && !annotations.isEmpty()) {
            AnnotationNode an = null;
            for (AnnotationNode next : annotations) {
                if (next.getClassNode().getName().equals(NamedParams.class.getName())) {
                    an = next;
                }
            }
            if (an != null) {
                Expression value = an.getMember("value");
                if (value instanceof AnnotationConstantExpression) {
                    processNamedParam((AnnotationConstantExpression) value, entries, args, collectedNames);
                } else if (value instanceof ListExpression) {
                    ListExpression le = (ListExpression) value;
                    for (Expression next : le.getExpressions()) {
                        if (next instanceof AnnotationConstantExpression) {
                            processNamedParam((AnnotationConstantExpression) next, entries, args, collectedNames);
                        }
                    }
                }
            }
        }
        annotations = param.getAnnotations(NAMED_PARAM_CLASSNODE);
        if (annotations != null && !annotations.isEmpty()) {
            for (AnnotationNode next : annotations) {
                if (next.getClassNode().getName().equals(NamedParam.class.getName())) {
                    processNamedParam(next, entries, args, collectedNames);
                }
            }
        }
        if (!collectedNames.isEmpty()) {
            for (Map.Entry<Object, Expression> entry : entries.entrySet()) {
                if (!collectedNames.contains(entry.getKey())) {
                    addStaticTypeError("unexpected named arg: " + entry.getKey(), args);
                }
            }
        }
    }

    private void processNamedParam(final AnnotationConstantExpression value, final Map<Object, Expression> entries, final Expression expression, final List<String> collectedNames) {
        AnnotationNode namedParam = (AnnotationNode) value.getValue();
        if (!namedParam.getClassNode().getName().equals(NamedParam.class.getName())) return;
        processNamedParam(namedParam, entries, expression, collectedNames);
    }

    private void processNamedParam(final AnnotationNode namedParam, final Map<Object, Expression> entries, final Expression expression, final List<String> collectedNames) {
        String name = null;
        boolean required = false;
        ClassNode expectedType = null;
        ConstantExpression constX = (ConstantExpression) namedParam.getMember("value");
        if (constX != null) {
            name = (String) constX.getValue();
            collectedNames.add(name);
        }
        constX = (ConstantExpression) namedParam.getMember("required");
        if (constX != null) {
            required = (Boolean) constX.getValue();
        }
        ClassExpression typeX = (ClassExpression) namedParam.getMember("type");
        if (typeX != null) {
            expectedType = typeX.getType();
        }
        if (!entries.containsKey(name)) {
            if (required) {
                addStaticTypeError("required named param '" + name + "' not found.", expression);
            }
        } else if (expectedType != null) {
            ClassNode argumentType = getDeclaredOrInferredType(entries.get(name));
            if (!isAssignableTo(argumentType, expectedType)) {
                addStaticTypeError("argument for named param '" + name + "' has type '" + prettyPrintType(argumentType) + "' but expected '" + prettyPrintType(expectedType) + "'.", expression);
            }
        }
    }

    /**
     * Performs type inference on closure argument types whenever code like this
     * is found: <code>foo.collect { it.toUpperCase() }</code>.
     * <p>
     * In this case the type checker tries to find if the {@code collect} method
     * has its {@link Closure} argument annotated with {@link ClosureParams}. If
     * so, then additional type inference can be performed and the type of
     * {@code it} may be inferred.
     *
     * @param receiver
     * @param arguments
     * @param expression closure or lambda expression for which the argument types should be inferred
     * @param target     parameter which may provide {@link ClosureParams} annotation or SAM type
     * @param method     method that declares {@code target}
     */
    protected void inferClosureParameterTypes(final ClassNode receiver, final Expression arguments, final ClosureExpression expression, final Parameter target, final MethodNode method) {
        List<AnnotationNode> annotations = target.getAnnotations(CLOSUREPARAMS_CLASSNODE);
        if (annotations != null && !annotations.isEmpty()) {
            for (AnnotationNode annotation : annotations) {
                Expression value = annotation.getMember("value");
                Expression options = annotation.getMember("options");
                Expression conflictResolver = annotation.getMember("conflictResolutionStrategy");
                doInferClosureParameterTypes(receiver, arguments, expression, method, value, conflictResolver, options);
            }
        } else if (isSAMType(target.getOriginType())) { // SAM-type coercion
            boolean isConstructor = method.isConstructor();
            boolean hasTypeArguments = asBoolean(receiver.getGenericsTypes());
            Map<GenericsTypeName, GenericsType> context = isConstructor && !hasTypeArguments ? new HashMap<>() // GROOVY-10699
                                                        : extractPlaceHoldersVisibleToDeclaration(receiver, method, arguments);
            GenericsType[] typeParameters = isConstructor ? method.getDeclaringClass().getGenericsTypes() : applyGenericsContext(context, method.getGenericsTypes());

            if (typeParameters != null) {
                boolean typeParametersResolved = false;
                // first check for explicit type arguments
                if (isConstructor) {
                    typeParametersResolved = hasTypeArguments;
                } else {
                    Expression emc = typeCheckingContext.getEnclosingMethodCall();
                    if (emc instanceof MethodCallExpression) {
                        MethodCallExpression mce = (MethodCallExpression) emc;
                        if (mce.getArguments() == arguments) {
                            GenericsType[] typeArguments = mce.getGenericsTypes();
                            if (typeArguments != null) {
                                int n = typeParameters.length;
                                if (n == typeArguments.length) {
                                    typeParametersResolved = true;
                                    for (int i = 0; i < n; i += 1) {
                                        context.put(new GenericsTypeName(typeParameters[i].getName()), typeArguments[i]);
                                    }
                                }
                            }
                        }
                    }
                }
                if (!typeParametersResolved) {
                    // check for implicit type arguments
                    int i = -1; Parameter[] p = method.getParameters();
                    for (Expression argument : (ArgumentListExpression) arguments) { i += 1;
                        if (isNullConstant(argument)) continue;

                        ClassNode pType = p[Math.min(i, p.length - 1)].getType();
                        Map<GenericsTypeName, GenericsType> gc = new HashMap<>();
                        extractGenericsConnections(gc, wrapTypeIfNecessary(getType(argument)), pType);
                        // GROOVY-10436: extract generics connections from closure parameter declaration(s)
                        if (argument == expression || (argument instanceof ClosureExpression && isSAMType(pType))) {
                            Parameter[] q = getParametersSafe((ClosureExpression) argument);
                            ClassNode[] r = extractTypesFromParameters(q); // maybe typed
                            ClassNode[] s = GenericsUtils.parameterizeSAM(pType).getV1();
                            for (int j = 0; j < r.length && j < s.length; j += 1)
                                if (!q[j].isDynamicTyped()) extractGenericsConnections(gc, r[j], s[j]);
                        }

                        gc.forEach((key, gt) -> {
                            for (GenericsType tp : typeParameters) {
                                if (tp.getName().equals(key.getName())) {
                                    context.putIfAbsent(key, gt); // TODO: merge
                                    break;
                                }
                            }
                        });
                    }

                    for (GenericsType tp : typeParameters) {
                        context.computeIfAbsent(new GenericsTypeName(tp.getName()), x -> fullyResolve(tp, context));
                    }
                }
            }

            ClassNode[] samParamTypes = GenericsUtils.parameterizeSAM(applyGenericsContext(context, target.getType())).getV1();

            ClassNode[] paramTypes = expression.getNodeMetaData(CLOSURE_ARGUMENTS);
            if (paramTypes == null) {
                int n; Parameter[] p = expression.getParameters();
                if (p == null) {
                    // zero parameters
                    paramTypes = ClassNode.EMPTY_ARRAY;
                } else if ((n = p.length) == 0) {
                    // implicit parameter(s)
                    paramTypes = samParamTypes;
                } else { // TODO: error for length mismatch
                    paramTypes = Arrays.copyOf(samParamTypes, n);
                    for (int i = 0; i < Math.min(n, samParamTypes.length); i += 1) {
                        checkParamType(p[i], paramTypes[i], i == n-1, expression instanceof LambdaExpression);
                    }
                }
                expression.putNodeMetaData(CLOSURE_ARGUMENTS, paramTypes);
            }
        }
    }

    private List<ClassNode[]> getSignaturesFromHint(final MethodNode mn, final Expression hintType, final Expression options, final ASTNode usage) {
        String hintTypeName = hintType.getText(); // load hint class using compiler's classloader
        try {
            @SuppressWarnings("unchecked")
            Class<? extends ClosureSignatureHint> hintClass = (Class<? extends ClosureSignatureHint>) getTransformLoader().loadClass(hintTypeName);
            List<ClassNode[]> closureSignatures = hintClass.getDeclaredConstructor().newInstance().getClosureSignatures(
                    mn instanceof ExtensionMethodNode ? ((ExtensionMethodNode) mn).getExtensionMethodNode() : mn,
                    typeCheckingContext.getSource(),
                    typeCheckingContext.getCompilationUnit(),
                    convertToStringArray(options),
                    usage);
            return closureSignatures;
        } catch (ReflectiveOperationException e) {
            throw new GroovyBugError(e);
        }
    }

    private List<ClassNode[]> resolveWithResolver(final List<ClassNode[]> candidates, final ClassNode receiver, final Expression arguments, final ClosureExpression expression, final MethodNode selectedMethod, final Expression resolverClass, final Expression options) {
        // initialize resolver
        try {
            ClassLoader transformLoader = getTransformLoader();
            @SuppressWarnings("unchecked")
            Class<? extends ClosureSignatureConflictResolver> resolver = (Class<? extends ClosureSignatureConflictResolver>) transformLoader.loadClass(resolverClass.getText());
            ClosureSignatureConflictResolver resolverInstance = resolver.getDeclaredConstructor().newInstance();
            return resolverInstance.resolve(
                    candidates,
                    receiver,
                    arguments,
                    expression,
                    selectedMethod instanceof ExtensionMethodNode ? ((ExtensionMethodNode) selectedMethod).getExtensionMethodNode() : selectedMethod,
                    typeCheckingContext.getSource(),
                    typeCheckingContext.getCompilationUnit(),
                    convertToStringArray(options));
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            throw new GroovyBugError(e);
        }
    }

    private ClassLoader getTransformLoader() {
        return Optional.ofNullable(typeCheckingContext.getCompilationUnit()).map(CompilationUnit::getTransformLoader).orElseGet(() -> getSourceUnit().getClassLoader());
    }

    private void doInferClosureParameterTypes(final ClassNode receiver, final Expression arguments, final ClosureExpression expression, final MethodNode selectedMethod, final Expression hintClass, final Expression resolverClass, final Expression options) {
        Parameter[] closureParams = expression.getParameters();
        if (closureParams == null) return; // no-arg closure

        List<ClassNode[]> closureSignatures = getSignaturesFromHint(selectedMethod, hintClass, options, expression);
        List<ClassNode[]> candidates = new LinkedList<>();
        for (ClassNode[] signature : closureSignatures) {
            resolveGenericsFromTypeHint(receiver, arguments, selectedMethod, signature);
            if (signature.length == closureParams.length // same number of arguments
                    || (signature.length == 1 && closureParams.length == 0) // implicit it
                    || (closureParams.length > signature.length && last(signature).isArray())) { // vargs
                candidates.add(signature);
            }
        }
        if (candidates.size() > 1) {
            for (Iterator<ClassNode[]> candIt = candidates.iterator(); candIt.hasNext(); ) {
                ClassNode[] inferred = candIt.next();
                for (int i = 0, n = closureParams.length; i < n; i += 1) {
                    Parameter closureParam = closureParams[i];
                    ClassNode declaredType = closureParam.getOriginType();
                    ClassNode inferredType;
                    if (i < inferred.length - 1 || inferred.length == n) {
                        inferredType = inferred[i];
                    } else {
                        ClassNode lastInferred = inferred[inferred.length - 1];
                        if (lastInferred.isArray()) {
                            inferredType = lastInferred.getComponentType();
                        } else {
                            candIt.remove();
                            continue;
                        }
                    }
                    if (!typeCheckMethodArgumentWithGenerics(declaredType, inferredType, i == (n - 1))) {
                        candIt.remove();
                    }
                }
            }
            if (candidates.size() > 1 && resolverClass instanceof ClassExpression) {
                candidates = resolveWithResolver(candidates, receiver, arguments, expression, selectedMethod, resolverClass, options);
            }
            if (candidates.size() > 1) {
                addError("Ambiguous prototypes for closure. More than one target method matches. Please use explicit argument types.", expression);
            }
        }
        if (candidates.size() == 1) {
            ClassNode[] inferred = candidates.get(0);
            if (closureParams.length == 0 && inferred.length == 1) {
                expression.putNodeMetaData(CLOSURE_ARGUMENTS, inferred);
            } else {
                for (int i = 0, n = closureParams.length; i < n; i += 1) {
                    Parameter closureParam = closureParams[i];
                    ClassNode inferredType = OBJECT_TYPE;
                    if (i < inferred.length - 1 || inferred.length == n) {
                        inferredType = inferred[i];
                    } else {
                        ClassNode lastInferred = inferred[inferred.length - 1];
                        if (lastInferred.isArray()) {
                            inferredType = lastInferred.getComponentType();
                        } else {
                            addError("Incorrect number of parameters. Expected " + inferred.length + " but found " + n, expression);
                        }
                    }
                    checkParamType(closureParam, inferredType, i == n-1, false);
                    typeCheckingContext.controlStructureVariables.put(closureParam, inferredType);
                }
            }
        }
    }

    /**
     * Computes the inferred types of the closure parameters using the following trick:
     * <ol>
     * <li> creates a dummy MethodNode for which the return type is a class node
     *      for which the generic types are the types returned by the hint
     * <li> calls {@link #inferReturnTypeGenerics}
     * <li> returns inferred types from the result
     * </ol>
     * In practice it could be done differently but it has the main advantage of
     * reusing existing code, hence reducing the amount of code to debug in case
     * of failure.
     */
    private void resolveGenericsFromTypeHint(final ClassNode receiver, final Expression arguments, final MethodNode selectedMethod, final ClassNode[] signature) {
        ClassNode returnType = new ClassNode("ClForInference$" + UNIQUE_LONG.incrementAndGet(), 0, OBJECT_TYPE).getPlainNodeReference();
        returnType.setGenericsTypes(Arrays.stream(signature).map(ClassNode::asGenericsType).toArray(GenericsType[]::new));

        MethodNode methodNode = selectedMethod instanceof ExtensionMethodNode ? ((ExtensionMethodNode) selectedMethod).getExtensionMethodNode() : selectedMethod;
        methodNode = new MethodNode("$$", methodNode.getModifiers(), returnType, methodNode.getParameters(), methodNode.getExceptions(), null);
        methodNode.setDeclaringClass(selectedMethod.getDeclaringClass());
        methodNode.setGenericsTypes(selectedMethod.getGenericsTypes());
        if (selectedMethod instanceof ExtensionMethodNode) {
            methodNode = new ExtensionMethodNode(
                    methodNode,
                    methodNode.getName(),
                    methodNode.getModifiers(),
                    returnType,
                    selectedMethod.getParameters(),
                    selectedMethod.getExceptions(),
                    null,
                    ((ExtensionMethodNode) selectedMethod).isStaticExtension()
            );
            methodNode.setDeclaringClass(selectedMethod.getDeclaringClass());
            methodNode.setGenericsTypes(selectedMethod.getGenericsTypes());
        }

        returnType = inferReturnTypeGenerics(receiver, methodNode, arguments);
        GenericsType[] returnTypeGenerics = returnType.getGenericsTypes();
        for (int i = 0, n = returnTypeGenerics.length; i < n; i += 1) {
            GenericsType gt = returnTypeGenerics[i];
            if (gt.isPlaceholder()) { // GROOVY-9968, GROOVY-10528, et al.
                signature[i] = gt.getUpperBounds() != null ? gt.getUpperBounds()[0] : gt.getType().redirect();
            } else {
                signature[i] = getCombinedBoundType(gt);
            }
        }
    }

    private static String[] convertToStringArray(final Expression options) {
        if (options == null) {
            return ResolveVisitor.EMPTY_STRING_ARRAY;
        }
        if (options instanceof ConstantExpression) {
            return new String[]{options.getText()};
        }
        if (options instanceof ListExpression) {
            return ((ListExpression) options).getExpressions().stream().map(Expression::getText).toArray(String[]::new);
        }
        throw new IllegalArgumentException("Unexpected options for @ClosureParams:" + options);
    }

    private void checkClosureWithDelegatesTo(final ClassNode receiver, final MethodNode mn, final ArgumentListExpression arguments, final Parameter[] params, final Expression expression, final Parameter param) {
        List<AnnotationNode> annotations = param.getAnnotations(DELEGATES_TO);
        if (annotations != null && !annotations.isEmpty()) {
            for (AnnotationNode annotation : annotations) {
                // in theory, there can only be one annotation of that type
                Expression value = annotation.getMember("value");
                Expression strategy = annotation.getMember("strategy");
                Expression genericTypeIndex = annotation.getMember("genericTypeIndex");
                Expression type = annotation.getMember("type");
                Integer stInt = Closure.OWNER_FIRST;
                if (strategy != null) {
                    stInt = (Integer) evaluateExpression(castX(Integer_TYPE, strategy), getSourceUnit().getConfiguration());
                }
                if (value instanceof ClassExpression && !value.getType().equals(DELEGATES_TO_TARGET)) {
                    if (genericTypeIndex != null) {
                        addStaticTypeError("Cannot use @DelegatesTo(genericTypeIndex=" + genericTypeIndex.getText()
                                + ") without @DelegatesTo.Target because generic argument types are not available at runtime", value);
                    }
                    // temporarily store the delegation strategy and the delegate type
                    expression.putNodeMetaData(DELEGATION_METADATA, newDelegationMetadata(value.getType(), stInt));
                } else if (type != null && !"".equals(type.getText()) && type instanceof ConstantExpression) {
                    String typeString = type.getText();
                    ClassNode[] resolved = GenericsUtils.parseClassNodesFromString(
                            typeString,
                            getSourceUnit(),
                            typeCheckingContext.getCompilationUnit(),
                            mn,
                            type
                    );
                    if (resolved != null) {
                        if (resolved.length == 1) {
                            resolveGenericsFromTypeHint(receiver, arguments, mn, resolved);
                            expression.putNodeMetaData(DELEGATION_METADATA, newDelegationMetadata(resolved[0], stInt));
                        } else {
                            addStaticTypeError("Incorrect type hint found in method " + (mn), type);
                        }
                    }
                } else {
                    List<Expression> expressions = arguments.getExpressions();
                    int expressionsSize = expressions.size();
                    Expression parameter = annotation.getMember("target");
                    String parameterName = parameter instanceof ConstantExpression ? parameter.getText() : "";
                    // todo: handle vargs!
                    for (int j = 0, paramsLength = params.length; j < paramsLength; j++) {
                        Parameter methodParam = params[j];
                        List<AnnotationNode> targets = methodParam.getAnnotations(DELEGATES_TO_TARGET);
                        if (targets != null && targets.size() == 1) {
                            AnnotationNode targetAnnotation = targets.get(0); // @DelegatesTo.Target Obj foo
                            Expression idMember = targetAnnotation.getMember("value");
                            String id = idMember instanceof ConstantExpression ? idMember.getText() : "";
                            if (id.equals(parameterName)) {
                                if (j < expressionsSize) {
                                    Expression actualArgument = expressions.get(j);
                                    ClassNode actualType = getType(actualArgument);
                                    if (genericTypeIndex instanceof ConstantExpression) {
                                        int gti = Integer.parseInt(genericTypeIndex.getText());
                                        ClassNode paramType = methodParam.getType(); // type annotated with @DelegatesTo.Target
                                        GenericsType[] genericsTypes = paramType.getGenericsTypes();
                                        if (genericsTypes == null) {
                                            addStaticTypeError("Cannot use @DelegatesTo(genericTypeIndex=" + genericTypeIndex.getText()
                                                    + ") with a type that doesn't use generics", methodParam);
                                        } else if (gti < 0 || gti >= genericsTypes.length) {
                                            addStaticTypeError("Index of generic type @DelegatesTo(genericTypeIndex=" + genericTypeIndex.getText()
                                                    + ") " + (gti < 0 ? "lower" : "greater") + " than those of the selected type", methodParam);
                                        } else {
                                            ClassNode pType = GenericsUtils.parameterizeType(actualType, paramType);
                                            GenericsType[] pTypeGenerics = pType.getGenericsTypes();
                                            if (pTypeGenerics != null && pTypeGenerics.length > gti) {
                                                actualType = pTypeGenerics[gti].getType();
                                            } else {
                                                addStaticTypeError("Unable to map actual type [" + prettyPrintType(actualType) + "] onto " + prettyPrintType(paramType), methodParam);
                                            }
                                        }
                                    }
                                    expression.putNodeMetaData(DELEGATION_METADATA, newDelegationMetadata(actualType, stInt));
                                    break;
                                }
                            }
                        }
                    }
                    if (expression.getNodeMetaData(DELEGATION_METADATA) == null) {
                        addError("Not enough arguments found for a @DelegatesTo method call. Please check that you either use an explicit class or @DelegatesTo.Target with a correct id", annotation);
                    }
                }
            }
        }
    }

    protected void addReceivers(final List<Receiver<String>> receivers, final Collection<Receiver<String>> owners, final boolean implicitThis) {
        if (!implicitThis || typeCheckingContext.delegationMetadata == null) {
            receivers.addAll(owners);
        } else {
            addReceivers(receivers, owners, typeCheckingContext.delegationMetadata, "");
        }
    }

    private static void addReceivers(final List<Receiver<String>> receivers, final Collection<Receiver<String>> owners, final DelegationMetadata dmd, final String path) {
        int strategy = dmd.getStrategy();
        switch (strategy) {
          case Closure.DELEGATE_ONLY:
          case Closure.DELEGATE_FIRST:
            addDelegateReceiver(receivers, dmd.getType(), path + "delegate");
            if (strategy == Closure.DELEGATE_FIRST) {
                if (dmd.getParent() == null) {
                    receivers.addAll(owners);
                } else {
                    //receivers.add(new Receiver<String>(CLOSURE_TYPE, path + "owner"));
                    addReceivers(receivers, owners, dmd.getParent(), path + "owner.");
                }
            }
            break;
          case Closure.OWNER_ONLY:
          case Closure.OWNER_FIRST:
            if (dmd.getParent() == null) {
                receivers.addAll(owners);
            } else {
                //receivers.add(new Receiver<String>(CLOSURE_TYPE, path + "owner"));
                addReceivers(receivers, owners, dmd.getParent(), path + "owner.");
            }
            if (strategy == Closure.OWNER_FIRST) {
                addDelegateReceiver(receivers, dmd.getType(), path + "delegate");
            }
            break;
        }
    }

    private static void addDelegateReceiver(final List<Receiver<String>> receivers, final ClassNode delegate, final String path) {
        if (receivers.stream().map(Receiver::getType).noneMatch(delegate::equals)) {
            receivers.add(new Receiver<>(delegate, path));
        }
    }

    @Override
    public void visitMethodCallExpression(final MethodCallExpression call) {
        String name = call.getMethodAsString();
        if (name == null) {
            addStaticTypeError("Cannot resolve dynamic method name at compile time", call.getMethod());
            return;
        }
        if (extension.beforeMethodCall(call)) {
            extension.afterMethodCall(call);
            return;
        }
        typeCheckingContext.pushEnclosingMethodCall(call);
        Expression objectExpression = call.getObjectExpression();

        objectExpression.visit(this);
        call.getMethod().visit(this);

        ClassNode receiver = getType(objectExpression);
        if (objectExpression instanceof ConstructorCallExpression) { // GROOVY-10228
            inferDiamondType((ConstructorCallExpression) objectExpression, receiver.getPlainNodeReference());
        }
        if (call.isSpreadSafe()) {
            ClassNode componentType = inferComponentType(receiver, null);
            if (componentType == null) {
                addStaticTypeError("Spread-dot operator can only be used on iterable types", objectExpression);
            } else {
                MethodCallExpression subcall = callX(varX("item", componentType), name, call.getArguments());
                subcall.setLineNumber(call.getLineNumber()); subcall.setColumnNumber(call.getColumnNumber());
                subcall.setImplicitThis(call.isImplicitThis());
                visitMethodCallExpression(subcall);
                // inferred type should be a list of what sub-call returns
                storeType(call, extension.buildListType(getType(subcall)));
                storeTargetMethod(call, subcall.getNodeMetaData(DIRECT_METHOD_CALL_TARGET));
            }
            typeCheckingContext.popEnclosingMethodCall();
            return;
        }

        Expression callArguments = call.getArguments();
        ArgumentListExpression argumentList = InvocationWriter.makeArgumentList(callArguments);

        checkForbiddenSpreadArgument(argumentList);
        // visit closures *after* the method has been chosen
        visitMethodCallArguments(receiver, argumentList, false, null);

        boolean isThisObjectExpression = isThisExpression(objectExpression);
        boolean isCallOnClosure = false;
        FieldNode fieldNode = null;
        switch (name) {
          case "call":
          case "doCall":
            if (!isThisObjectExpression) {
                isCallOnClosure = receiver.equals(CLOSURE_TYPE);
                break;
            }
          default:
            if (isThisObjectExpression) {
                ClassNode enclosingType = typeCheckingContext.getEnclosingClassNode();
                fieldNode = enclosingType.getDeclaredField(name);
                if (fieldNode != null && getType(fieldNode).equals(CLOSURE_TYPE)
                        && !enclosingType.hasPossibleMethod(name, callArguments)) {
                    isCallOnClosure = true;
                }
            }
        }

        try {
            ClassNode[] args = getArgumentTypes(argumentList);
            boolean callArgsVisited = false;
            if (isCallOnClosure) {
                if (fieldNode != null) {
                    GenericsType[] genericsTypes = getType(fieldNode).getGenericsTypes();
                    if (genericsTypes != null) {
                        Parameter[] parameters = fieldNode.getNodeMetaData(CLOSURE_ARGUMENTS);
                        if (parameters != null) {
                            typeCheckClosureCall(callArguments, args, parameters);
                        }
                        ClassNode closureReturnType = genericsTypes[0].getType();
                        storeType(call, closureReturnType);
                    }
                } else if (objectExpression instanceof VariableExpression) {
                    Variable variable = findTargetVariable((VariableExpression) objectExpression);
                    if (variable instanceof ASTNode) {
                        Parameter[] parameters = ((ASTNode) variable).getNodeMetaData(CLOSURE_ARGUMENTS);
                        if (parameters != null) {
                            typeCheckClosureCall(callArguments, args, parameters);
                        }
                        ClassNode type = getType((ASTNode) variable);
                        if (type.equals(CLOSURE_TYPE)) { // GROOVY-10098, et al.
                            GenericsType[] genericsTypes = type.getGenericsTypes();
                            if (genericsTypes != null && genericsTypes.length == 1
                                    && genericsTypes[0].getLowerBound() == null) {
                                type = genericsTypes[0].getType();
                            } else {
                                type = OBJECT_TYPE;
                            }
                        }
                        if (type != null) {
                            storeType(call, type);
                        }
                    }
                } else if (objectExpression instanceof ClosureExpression) {
                    // we can get actual parameters directly
                    Parameter[] parameters = ((ClosureExpression) objectExpression).getParameters();
                    if (parameters != null) {
                        typeCheckClosureCall(callArguments, args, parameters);
                    }
                    ClassNode type = getInferredReturnType(objectExpression);
                    if (type != null) {
                        storeType(call, type);
                    }
                }

                int nArgs = 0;
                if (callArguments instanceof ArgumentListExpression) {
                    nArgs = ((ArgumentListExpression) callArguments).getExpressions().size();
                }
                storeTargetMethod(call, nArgs == 0 ? CLOSURE_CALL_NO_ARG : nArgs == 1 ? CLOSURE_CALL_ONE_ARG : CLOSURE_CALL_VARGS);
            } else {
                // method call receivers are :
                //   - possible "with" receivers
                //   - the actual receiver as found in the method call expression
                //   - any of the potential receivers found in the instanceof temporary table
                // in that order
                List<Receiver<String>> receivers = new ArrayList<>();
                addReceivers(receivers, makeOwnerList(objectExpression), call.isImplicitThis());

                List<MethodNode> mn = null;
                Receiver<String> chosenReceiver = null;
                for (Receiver<String> currentReceiver : receivers) {
                    ClassNode receiverType = currentReceiver.getType();
                    mn = findMethod(receiverType, name, args);

                    // if receiver is "this" in a static context then only static methods are compatible
                    // if not in a static context but the current receiver is a static class ensure that
                    // all methods are either static or declared by the current receiver or a superclass
                    if (!mn.isEmpty() && currentReceiver.getData() == null && (isThisObjectExpression || call.isImplicitThis())
                            && (typeCheckingContext.isInStaticContext || (receiverType.getModifiers() & Opcodes.ACC_STATIC) != 0)) {
                        // we create separate method lists just to be able to print out
                        // a nice error message to the user
                        // a method is accessible if it is static, or if we are not in a static context and it is
                        // declared by the current receiver or a superclass
                        List<MethodNode> accessibleMethods = new LinkedList<>();
                        List<MethodNode> inaccessibleMethods = new LinkedList<>();
                        for (final MethodNode node : mn) {
                            if (node.isStatic() || (!typeCheckingContext.isInStaticContext
                                    && implementsInterfaceOrIsSubclassOf(receiverType, node.getDeclaringClass()))) {
                                accessibleMethods.add(node);
                            } else {
                                inaccessibleMethods.add(node);
                            }
                        }
                        mn = accessibleMethods;
                        if (accessibleMethods.isEmpty()) {
                            MethodNode node = inaccessibleMethods.get(0); // choose an arbitrary method to display an error message
                            addStaticTypeError("Non-static method " + prettyPrintTypeName(node.getDeclaringClass()) + "#" + node.getName() + " cannot be called from static context", call);
                        }
                    }

                    if (!mn.isEmpty()) {
                        chosenReceiver = currentReceiver;
                        break;
                    }
                }
                if (mn.isEmpty() && isThisObjectExpression && call.isImplicitThis() && typeCheckingContext.getEnclosingClosure() != null) {
                    mn = CLOSURE_TYPE.getDeclaredMethods(name);
                    if (!mn.isEmpty()) {
                        chosenReceiver = Receiver.make(CLOSURE_TYPE);
                        objectExpression.removeNodeMetaData(INFERRED_TYPE);
                    }
                }
                if (mn.isEmpty()) {
                    mn = extension.handleMissingMethod(receiver, name, argumentList, args, call);
                }
                if (mn.isEmpty()) {
                    addNoMatchingMethodError(receiver, name, args, call);
                } else {
                    if (areCategoryMethodCalls(mn, name, args)) {
                        addCategoryMethodCallError(call);
                    }

                    {
                        ClassNode obj = chosenReceiver != null ? chosenReceiver.getType() : null;
                        if (mn.size() > 1 && obj instanceof UnionTypeClassNode) { // GROOVY-8965: support duck-typing using dynamic resolution
                            ClassNode returnType = mn.stream().map(MethodNode::getReturnType).reduce(WideningCategories::lowestUpperBound).get();
                            call.putNodeMetaData(DYNAMIC_RESOLUTION, returnType);

                            MethodNode tmp = new MethodNode(name, 0, returnType, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, null);
                            tmp.setDeclaringClass(obj); // for storeTargetMethod
                            mn = Collections.singletonList(tmp);
                        } else {
                            mn = disambiguateMethods(mn, obj, args, call);
                        }
                    }

                    if (mn.size() == 1) {
                        MethodNode targetMethodCandidate = mn.get(0);
                        ClassNode declaringClass = targetMethodCandidate.getDeclaringClass();
                        if (!targetMethodCandidate.isStatic() && !isClassType(declaringClass)
                                && objectExpression instanceof ClassExpression && call.getNodeMetaData(DYNAMIC_RESOLUTION) == null) {
                            addStaticTypeError("Non-static method " + prettyPrintTypeName(declaringClass) + "#" + targetMethodCandidate.getName() + " cannot be called from static context", call);
                        } else if (targetMethodCandidate.isAbstract() && isSuperExpression(objectExpression)) { // GROOVY-10341
                            String target = toMethodParametersString(targetMethodCandidate.getName(), extractTypesFromParameters(targetMethodCandidate.getParameters()));
                            if (Traits.hasDefaultImplementation(targetMethodCandidate)) { // GROOVY-10494
                                addStaticTypeError("Default method " + target + " requires qualified super", call);
                            } else {
                                addStaticTypeError("Abstract method " + target + " cannot be called directly", call);
                            }
                        }
                        if (chosenReceiver == null) {
                            chosenReceiver = Receiver.make(declaringClass);
                        }
                        // note second pass here to differentiate from extension that sets type
                        boolean mergeType = (call.getNodeMetaData(INFERRED_TYPE) != null);
                        storeTargetMethod(call, targetMethodCandidate);

                        visitMethodCallArguments(chosenReceiver.getType(), argumentList, true, targetMethodCandidate); callArgsVisited = true;

                        ClassNode returnType = getType(targetMethodCandidate);
                        if (isUsingGenericsOrIsArrayUsingGenerics(returnType)) {
                            ClassNode irtg = inferReturnTypeGenerics(chosenReceiver.getType(), targetMethodCandidate, callArguments, call.getGenericsTypes());
                            if (irtg != null && implementsInterfaceOrIsSubclassOf(irtg, returnType))
                                returnType = irtg;
                        }
                        // GROOVY-6091: use of "delegate" or "getDelegate()" does not make use of @DelegatesTo metadata
                        if (targetMethodCandidate == GET_DELEGATE && typeCheckingContext.getEnclosingClosure() != null) {
                            DelegationMetadata md = getDelegationMetadata(typeCheckingContext.getEnclosingClosure().getClosureExpression());
                            if (md != null) {
                                returnType = md.getType();
                            } else {
                                returnType = typeCheckingContext.getEnclosingClassNode();
                            }
                        }
                        // GROOVY-7106, GROOVY-7274, GROOVY-8909, GROOVY-8961, GROOVY-9734, GROOVY-9844, GROOVY-9915, et al.
                        Parameter[] parameters = targetMethodCandidate.getParameters();
                        if (chosenReceiver.getType().getGenericsTypes() != null && !targetMethodCandidate.isStatic() && !(targetMethodCandidate instanceof ExtensionMethodNode)) {
                            Map<GenericsTypeName, GenericsType> context = extractPlaceHoldersVisibleToDeclaration(chosenReceiver.getType(), targetMethodCandidate, argumentList);
                            parameters = Arrays.stream(parameters).map(p -> new Parameter(applyGenericsContext(context, p.getType()), p.getName())).toArray(Parameter[]::new);
                        }
                        resolvePlaceholdersFromImplicitTypeHints(args, argumentList, parameters);

                        if (typeCheckMethodsWithGenericsOrFail(chosenReceiver.getType(), args, targetMethodCandidate, call)) {
                            String data = chosenReceiver.getData();
                            if (data != null) {
                                // the method which has been chosen is supposed to be a call on delegate or owner
                                // so we store the information so that the static compiler may reuse it
                                call.putNodeMetaData(IMPLICIT_RECEIVER, data);
                            }
                            receiver = chosenReceiver.getType();
                            if (mergeType || call.getNodeMetaData(INFERRED_TYPE) == null)
                                storeType(call, adjustWithTraits(targetMethodCandidate, receiver, args, returnType));

                            if (objectExpression instanceof VariableExpression && ((VariableExpression) objectExpression).isClosureSharedVariable()) {
                                // if the object expression is a closure shared variable, we will have to perform a second pass
                                typeCheckingContext.secondPassExpressions.add(new SecondPassExpression<>(call, args));
                            }
                        } else {
                            call.removeNodeMetaData(DIRECT_METHOD_CALL_TARGET);
                        }
                    } else {
                        addAmbiguousErrorMessage(mn, name, args, call);
                    }
                }
            }
            // adjust typing for explicit math methods which have special handling; operator variants handled elsewhere
            if (args.length == 1 && isNumberType(args[0]) && isNumberType(receiver) && NUMBER_OPS.containsKey(name)) {
                ClassNode resultType = getMathResultType(NUMBER_OPS.get(name), receiver, args[0], name);
                if (resultType != null) {
                    storeType(call, resultType);
                }
            }

            MethodNode target = call.getNodeMetaData(DIRECT_METHOD_CALL_TARGET);
            if (!callArgsVisited) {
                visitMethodCallArguments(receiver, argumentList, true, target);
            }
            if (target != null) {
                checkClosureMetadata(argumentList.getExpressions(), target.getParameters());
            }
        } finally {
            typeCheckingContext.popEnclosingMethodCall();
            extension.afterMethodCall(call);
        }
    }

    private void checkClosureMetadata(final List<Expression> arguments, final Parameter[] parameters) {
        // TODO: Check additional delegation metadata like type (see checkClosureWithDelegatesTo).
        for (int i = 0, n = Math.min(arguments.size(), parameters.length); i < n; i += 1) {
            Expression argument = arguments.get(i);
            ClassNode aType = getType(argument), pType = parameters[i].getType();
            // GROOVY-7996: check delegation metadata of closure parameter used as method call argument
            if (CLOSURE_TYPE.equals(aType) && CLOSURE_TYPE.equals(pType) && argument instanceof VariableExpression && ((VariableExpression) argument).getAccessedVariable() instanceof Parameter) {
                int incomingStrategy = getResolveStrategy((Parameter) ((VariableExpression) argument).getAccessedVariable());
                int outgoingStrategy = getResolveStrategy(parameters[i]);
                if (incomingStrategy != outgoingStrategy) {
                    addStaticTypeError("Closure parameter with resolve strategy " + getResolveStrategyName(incomingStrategy) + " passed to method with resolve strategy " + getResolveStrategyName(outgoingStrategy), argument);
                }
            }
        }
    }

    private int getResolveStrategy(final Parameter parameter) {
        List<AnnotationNode> annotations = parameter.getAnnotations(DELEGATES_TO);
        if (annotations != null && !annotations.isEmpty()) {
            Expression strategy = annotations.get(0).getMember("strategy");
            if (strategy != null) {
                return (Integer) evaluateExpression(castX(Integer_TYPE, strategy), getSourceUnit().getConfiguration());
            }
        }
        return Closure.OWNER_FIRST;
    }

    private void inferMethodReferenceType(final ClassNode receiver, final ArgumentListExpression argumentList, final MethodNode selectedMethod) {
        if (receiver == null) return;
        if (argumentList == null) return;
        if (selectedMethod == null) return;

        List<Expression> argumentExpressions = argumentList.getExpressions();
        if (argumentExpressions == null || argumentExpressions.stream()
                .noneMatch(e -> e instanceof MethodReferenceExpression)) {
            return;
        }

        Parameter[] parameters = selectedMethod.getParameters();
        final int nthParameter = parameters.length - 1;

        List<Integer> methodReferencePositions = new LinkedList<>();
        List<Expression> newArgumentExpressions = new LinkedList<>();
        for (int i = 0, n = argumentExpressions.size(); i < n; i += 1) {
            Expression argumentExpression = argumentExpressions.get(i);
            if (!(argumentExpression instanceof MethodReferenceExpression)) {
                newArgumentExpressions.add(argumentExpression);
            } else {
                Parameter param = parameters[Math.min(i, nthParameter)]; // GROOVY-10336
                ClassNode paramType = param.getType();
                if (i >= nthParameter && paramType.isArray())
                    paramType = paramType.getComponentType();

                if (!isFunctionalInterface(paramType.redirect())) {
                    addError("The argument is a method reference, but the parameter type is not a functional interface", argumentExpression);
                    newArgumentExpressions.add(argumentExpression);
                } else {
                    methodReferencePositions.add(i);
                    newArgumentExpressions.add(constructLambdaExpressionForMethodReference(paramType));
                }
            }
        }

        if (methodReferencePositions.isEmpty()) return; // GROOVY-10269

        visitMethodCallArguments(receiver, args(newArgumentExpressions), true, selectedMethod);

        for (int index : methodReferencePositions) {
            Expression lambdaExpression = newArgumentExpressions.get(index);
            Expression methodReferenceExpression = argumentExpressions.get(index);
            methodReferenceExpression.putNodeMetaData(CLOSURE_ARGUMENTS, lambdaExpression.getNodeMetaData(CLOSURE_ARGUMENTS));
        }
    }

    private LambdaExpression constructLambdaExpressionForMethodReference(final ClassNode functionalInterfaceType) {
        Parameter[] parameters = findSAM(functionalInterfaceType).getParameters().clone();
        for (int i = 0, n = parameters.length; i < n; i += 1) {
            parameters[i] = new Parameter(dynamicType(), "p" + System.nanoTime());
        }

        return new LambdaExpression(parameters, EmptyStatement.INSTANCE);
    }

    /**
     * A special method handling the "withTrait" call for which the type checker knows more than
     * what the type signature is able to tell. If "withTrait" is detected, then a new class node
     * is created representing the list of trait interfaces.
     *
     * @param directMethodCallCandidate a method selected by the type checker
     * @param receiver                  the receiver of the method call
     * @param args                      the arguments of the method call
     * @param returnType                the original return type, as inferred by the type checker
     * @return fixed return type if the selected method is {@link org.codehaus.groovy.runtime.DefaultGroovyMethods#withTraits(Object, Class[]) withTraits}
     */
    private static ClassNode adjustWithTraits(final MethodNode directMethodCallCandidate, final ClassNode receiver, final ClassNode[] args, final ClassNode returnType) {
        if (directMethodCallCandidate instanceof ExtensionMethodNode) {
            ExtensionMethodNode emn = (ExtensionMethodNode) directMethodCallCandidate;
            if ("withTraits".equals(emn.getName()) && "DefaultGroovyMethods".equals(emn.getExtensionMethodNode().getDeclaringClass().getNameWithoutPackage())) {
                List<ClassNode> nodes = new LinkedList<>();
                Collections.addAll(nodes, receiver.getInterfaces());
                for (ClassNode arg : args) {
                    if (isClassClassNodeWrappingConcreteType(arg)) {
                        nodes.add(arg.getGenericsTypes()[0].getType());
                    } else {
                        nodes.add(arg);
                    }
                }
                return new WideningCategories.LowestUpperBoundClassNode(returnType.getName() + "Composed", OBJECT_TYPE, nodes.toArray(ClassNode.EMPTY_ARRAY));
            }
        }
        return returnType;
    }

    /**
     * Adds various getAt and setAt methods for primitive arrays.
     *
     * @param receiver the receiver class
     * @param name     the name of the method
     * @param args     the argument classes
     */
    private static void addArrayMethods(final List<MethodNode> methods, final ClassNode receiver, final String name, final ClassNode[] args) {
        if (args.length != 1) return;
        if (!receiver.isArray()) return;
        if (!isIntCategory(getUnwrapper(args[0]))) return;
        if ("getAt".equals(name)) {
            MethodNode node = new MethodNode(name, Opcodes.ACC_PUBLIC, receiver.getComponentType(), new Parameter[]{new Parameter(args[0], "arg")}, null, null);
            node.setDeclaringClass(receiver.redirect());
            methods.add(node);
        } else if ("setAt".equals(name)) {
            MethodNode node = new MethodNode(name, Opcodes.ACC_PUBLIC, VOID_TYPE, new Parameter[]{new Parameter(args[0], "arg")}, null, null);
            node.setDeclaringClass(receiver.redirect());
            methods.add(node);
        }
    }

    /**
     * In the case of a <em>Object.with { ... }</em> call, this method is supposed to retrieve
     * the inferred closure return type.
     *
     * @param callArguments the argument list from the <em>Object#with(Closure)</em> call, ie. a single closure expression
     * @return the inferred closure return type or <em>null</em>
     */
    protected ClassNode getInferredReturnTypeFromWithClosureArgument(final Expression callArguments) {
        if (!(callArguments instanceof ArgumentListExpression)) return null;

        ArgumentListExpression argList = (ArgumentListExpression) callArguments;
        ClosureExpression closure = (ClosureExpression) argList.getExpression(0);

        visitClosureExpression(closure);

        return getInferredReturnType(closure);
    }

    /**
     * Given an object expression (a message receiver expression), generate list
     * of possible types.
     *
     * @param objectExpression the receiver expression
     * @return the list of types the receiver may be
     */
    protected List<Receiver<String>> makeOwnerList(final Expression objectExpression) {
        ClassNode receiver = getType(objectExpression);
        List<Receiver<String>> owners = new ArrayList<>();
        if (typeCheckingContext.delegationMetadata != null
                && objectExpression instanceof VariableExpression
                && ((Variable) objectExpression).getName().equals("owner")
                && /*isNested:*/typeCheckingContext.delegationMetadata.getParent() != null) {
            List<Receiver<String>> enclosingClass = Collections.singletonList(
                    Receiver.make(typeCheckingContext.getEnclosingClassNode()));
            addReceivers(owners, enclosingClass, typeCheckingContext.delegationMetadata.getParent(), "owner.");
        } else {
            List<ClassNode> temporaryTypes = getTemporaryTypesForExpression(objectExpression);
            int temporaryTypesCount = (temporaryTypes != null ? temporaryTypes.size() : 0);
            if (temporaryTypesCount > 0) { // GROOVY-8965, GROOVY-10180, GROOVY-10668
                owners.add(Receiver.make(lowestUpperBound(temporaryTypes)));
            }
            if (typeCheckingContext.lastImplicitItType != null
                    && objectExpression instanceof VariableExpression
                    && ((Variable) objectExpression).getName().equals("it")) {
                owners.add(Receiver.make(typeCheckingContext.lastImplicitItType));
            }
            if (isClassClassNodeWrappingConcreteType(receiver)) {
                ClassNode staticType = receiver.getGenericsTypes()[0].getType();
                owners.add(Receiver.make(staticType)); // Type from Class<Type>
                addTraitType(staticType, owners); // T in Class<T$Trait$Helper>
                owners.add(Receiver.make(receiver)); // Class<Type>
            } else {
                addBoundType(receiver, owners);
                addSelfTypes(receiver, owners);
                addTraitType(receiver, owners);
                if (receiver.redirect().isInterface()) {
                    owners.add(Receiver.make(OBJECT_TYPE));
                } else if (isSuperExpression(objectExpression)) { //GROOVY-9909: super.defaultMethod()
                    for (ClassNode in : typeCheckingContext.getEnclosingClassNode().getInterfaces()) {
                        if (!receiver.implementsInterface(in)) owners.add(Receiver.make(in));
                    }
                }
            }
            if (temporaryTypesCount > 1 && !(objectExpression instanceof VariableExpression)) {
                owners.add(Receiver.make(new UnionTypeClassNode(temporaryTypes.toArray(ClassNode.EMPTY_ARRAY))));
            }
        }
        return owners;
    }

    private static void addBoundType(final ClassNode receiver, final List<Receiver<String>> owners) {
        if (!receiver.isGenericsPlaceHolder() || receiver.getGenericsTypes() == null) {
            owners.add(Receiver.make(receiver));
            return;
        }

        GenericsType gt = receiver.getGenericsTypes()[0];
        if (gt.getLowerBound() == null && gt.getUpperBounds() != null) {
            for (ClassNode cn : gt.getUpperBounds()) { // T extends C & I
                addBoundType(cn, owners);
                addSelfTypes(cn, owners);
            }
        } else {
            owners.add(Receiver.make(OBJECT_TYPE)); // T or T super Type
        }
    }

    private static void addSelfTypes(final ClassNode receiver, final List<Receiver<String>> owners) {
        for (ClassNode selfType : Traits.collectSelfTypes(receiver, new LinkedHashSet<>())) {
            owners.add(Receiver.make(selfType));
        }
    }

    private static void addTraitType(final ClassNode receiver, final List<Receiver<String>> owners) {
        if (Traits.isTrait(receiver.getOuterClass()) && receiver.getName().endsWith("$Helper")) {
            ClassNode traitType = receiver.getOuterClass();
            owners.add(Receiver.make(traitType));
            addSelfTypes(traitType, owners);
        }
    }

    protected void checkForbiddenSpreadArgument(final ArgumentListExpression argumentList) {
        for (Expression arg : argumentList.getExpressions()) {
            if (arg instanceof SpreadExpression) {
                addStaticTypeError("The spread operator cannot be used as argument of method or closure calls with static type checking because the number of arguments cannot be determined at compile time", arg);
            }
        }
    }

    protected List<ClassNode> getTemporaryTypesForExpression(final Expression objectExpression) {
        List<ClassNode> classNodes = null;
        int depth = typeCheckingContext.temporaryIfBranchTypeInformation.size();
        while (classNodes == null && depth > 0) {
            Map<Object, List<ClassNode>> tempo = typeCheckingContext.temporaryIfBranchTypeInformation.get(--depth);
            Object key = objectExpression instanceof ParameterVariableExpression
                    ? ((ParameterVariableExpression) objectExpression).parameter
                    : extractTemporaryTypeInfoKey(objectExpression);
            classNodes = tempo.get(key);
        }
        return classNodes;
    }

    protected void storeTargetMethod(final Expression call, final MethodNode target) {
        if (target == null) {
            call.removeNodeMetaData(DIRECT_METHOD_CALL_TARGET); return;
        }
        call.putNodeMetaData(DIRECT_METHOD_CALL_TARGET,target);

        checkInterfaceStaticCall(call, target);
        checkOrMarkPrivateAccess(call, target);
        checkSuperCallFromClosure(call, target);
        extension.onMethodSelection(call, target);
    }

    private void checkInterfaceStaticCall(final Expression call, final MethodNode target) {
        if (target instanceof ExtensionMethodNode) return;
        ClassNode declaringClass = target.getDeclaringClass();
        if (declaringClass.isInterface() && target.isStatic()) {
            Expression objectExpression = getObjectExpression(call);
            if (objectExpression != null) { ClassNode type = getType(objectExpression);
                if (!isClassClassNodeWrappingConcreteType(type) || !type.getGenericsTypes()[0].getType().equals(declaringClass)) {
                    addStaticTypeError("static method of interface " + prettyPrintTypeName(declaringClass) + " can only be accessed with class qualifier", call);
                }
            }
        }
    }

    private void checkSuperCallFromClosure(final Expression call, final MethodNode target) {
        if (isSuperExpression(getObjectExpression(call)) && typeCheckingContext.getEnclosingClosure() != null) {
            ClassNode current = typeCheckingContext.getEnclosingClassNode();
            current.getNodeMetaData(SUPER_MOP_METHOD_REQUIRED, x -> new LinkedList<>()).add(target);
            call.putNodeMetaData(SUPER_MOP_METHOD_REQUIRED, current);
        }
    }

    private static Expression getObjectExpression(final Expression expression) {
        if (expression instanceof MethodCallExpression) {
            return ((MethodCallExpression) expression).getObjectExpression();
        }
        if (expression instanceof PropertyExpression) {
            return ((PropertyExpression) expression).getObjectExpression();
        }
        /*if (expression instanceof MethodPointerExpression) {
            return ((MethodPointerExpression) expression).getExpression();
        }*/
        return null;
    }

    protected void typeCheckClosureCall(final Expression arguments, final ClassNode[] argumentTypes, final Parameter[] parameters) {
        if (allParametersAndArgumentsMatchWithDefaultParams(parameters, argumentTypes) < 0 && lastArgMatchesVarg(parameters, argumentTypes) < 0) {
            addStaticTypeError("Cannot call closure that accepts " + formatArgumentList(extractTypesFromParameters(parameters)) + " with " + formatArgumentList(argumentTypes), arguments);
        }
    }

    @Override
    public void visitIfElse(final IfStatement ifElse) {
        Map<VariableExpression, List<ClassNode>> oldTracker = pushAssignmentTracking();
        try {
            // create a new temporary element in the if-then-else type info
            typeCheckingContext.pushTemporaryTypeInfo();
            visitStatement(ifElse);
            ifElse.getBooleanExpression().visit(this);
            ifElse.getIfBlock().visit(this);

            // pop if-then-else temporary type info
            typeCheckingContext.popTemporaryTypeInfo();

            // GROOVY-6099: restore assignment info as before the if branch
            restoreTypeBeforeConditional();

            ifElse.getElseBlock().visit(this);

            // GROOVY-9786: if chaining: "if (...) x=?; else if (...) x=?;"
            Map<VariableExpression, ClassNode> updates =
                ifElse.getElseBlock().getNodeMetaData("assignments");
            if (updates != null) {
                updates.forEach(this::recordAssignment);
            }
        } finally {
            ifElse.putNodeMetaData("assignments", popAssignmentTracking(oldTracker));
        }

        if (!typeCheckingContext.enclosingBlocks.isEmpty()) {
            BinaryExpression instanceOfExpression = findInstanceOfNotReturnExpression(ifElse);
            if (instanceOfExpression == null) {
                instanceOfExpression = findNotInstanceOfReturnExpression(ifElse);
            }
            if (instanceOfExpression != null) {
                visitInstanceofNot(instanceOfExpression);
            }
        }
    }

    protected void visitInstanceofNot(final BinaryExpression be) {
        BlockStatement currentBlock = typeCheckingContext.enclosingBlocks.getFirst();
        assert currentBlock != null;
        if (typeCheckingContext.blockStatements2Types.containsKey(currentBlock)) {
            // another instanceOf_not was before, no need store vars
        } else {
            // saving type of variables to restoring them after returning from block
            Map<VariableExpression, List<ClassNode>> oldTracker = pushAssignmentTracking();
            getTypeCheckingContext().pushTemporaryTypeInfo();
            typeCheckingContext.blockStatements2Types.put(currentBlock, oldTracker);
        }
        pushInstanceOfTypeInfo(be.getLeftExpression(), be.getRightExpression());
    }

    @Override
    public void visitBlockStatement(final BlockStatement block) {
        if (block != null) {
            typeCheckingContext.enclosingBlocks.addFirst(block);
        }
        super.visitBlockStatement(block);
        if (block != null) {
            visitClosingBlock(block);
        }
    }

    public void visitClosingBlock(final BlockStatement block) {
        BlockStatement peekBlock = typeCheckingContext.enclosingBlocks.removeFirst();
        boolean found = typeCheckingContext.blockStatements2Types.containsKey(peekBlock);
        if (found) {
            Map<VariableExpression, List<ClassNode>> oldTracker = typeCheckingContext.blockStatements2Types.remove(peekBlock);
            getTypeCheckingContext().popTemporaryTypeInfo();
            popAssignmentTracking(oldTracker);
        }
    }

    /**
     * Check IfStatement matched pattern :
     * Object var1;
     * if (!(var1 instanceOf Runnable)) {
     * return
     * }
     * // Here var1 instance of Runnable
     * <p>
     * Return expression , which contains instanceOf (without not)
     * Return null, if not found
     */
    protected BinaryExpression findInstanceOfNotReturnExpression(final IfStatement ifElse) {
        Statement elseBlock = ifElse.getElseBlock();
        if (!(elseBlock instanceof EmptyStatement)) {
            return null;
        }
        Expression conditionExpression = ifElse.getBooleanExpression().getExpression();
        if (!(conditionExpression instanceof NotExpression)) {
            return null;
        }
        NotExpression notExpression = (NotExpression) conditionExpression;
        Expression expression = notExpression.getExpression();
        if (!(expression instanceof BinaryExpression)) {
            return null;
        }
        BinaryExpression instanceOfExpression = (BinaryExpression) expression;
        int op = instanceOfExpression.getOperation().getType();
        if (op != KEYWORD_INSTANCEOF) {
            return null;
        }
        if (notReturningBlock(ifElse.getIfBlock())) {
            return null;
        }
        return instanceOfExpression;
    }

    /**
     * Check IfStatement matched pattern :
     * Object var1;
     * if (var1 !instanceOf Runnable) {
     * return
     * }
     * // Here var1 instance of Runnable
     * <p>
     * Return expression , which contains instanceOf (without not)
     * Return null, if not found
     */
    protected BinaryExpression findNotInstanceOfReturnExpression(final IfStatement ifElse) {
        Statement elseBlock = ifElse.getElseBlock();
        if (!(elseBlock instanceof EmptyStatement)) {
            return null;
        }
        Expression conditionExpression = ifElse.getBooleanExpression().getExpression();
        if (!(conditionExpression instanceof BinaryExpression)) {
            return null;
        }
        BinaryExpression instanceOfExpression = (BinaryExpression) conditionExpression;
        int op = instanceOfExpression.getOperation().getType();
        if (op != COMPARE_NOT_INSTANCEOF) {
            return null;
        }
        if (notReturningBlock(ifElse.getIfBlock())) {
            return null;
        }
        return instanceOfExpression;
    }

    private static boolean notReturningBlock(final Statement statement) {
        return statement.isEmpty() || !(statement instanceof BlockStatement)
            || !(last(((BlockStatement) statement).getStatements()) instanceof ReturnStatement);
    }

    @Override
    public void visitSwitch(final SwitchStatement statement) {
        typeCheckingContext.pushEnclosingSwitchStatement(statement);
        try {
            Map<VariableExpression, List<ClassNode>> oldTracker = pushAssignmentTracking();
            try {
                super.visitSwitch(statement);
            } finally {
                popAssignmentTracking(oldTracker);
            }
        } finally {
            typeCheckingContext.popEnclosingSwitchStatement();
        }
    }

    @Override
    protected void afterSwitchConditionExpressionVisited(final SwitchStatement statement) {
        Expression conditionExpression = statement.getExpression();
        conditionExpression.putNodeMetaData(TYPE, getType(conditionExpression));
    }

    @Override
    public void visitCaseStatement(final CaseStatement statement) {
        Expression expression = statement.getExpression();
        if (expression instanceof ClosureExpression) { // GROOVY-9854: propagate the switch type
            SwitchStatement switchStatement = typeCheckingContext.getEnclosingSwitchStatement();
            ClassNode inf = switchStatement.getExpression().getNodeMetaData(TYPE);
            expression.putNodeMetaData(CLOSURE_ARGUMENTS, new ClassNode[]{inf});

            Parameter[] params = ((ClosureExpression) expression).getParameters();
            if (params != null && params.length == 1) {
                boolean lambda = (expression instanceof LambdaExpression);
                checkParamType(params[0], wrapTypeIfNecessary(inf), false, lambda);
            } else if (params == null || params.length > 1) {
                int paramCount = (params != null ? params.length : 0);
                addError("Incorrect number of parameters. Expected 1 but found " + paramCount, expression);
            }
        }
        super.visitCaseStatement(statement);
        restoreTypeBeforeConditional();
    }

    private void recordAssignment(final VariableExpression lhsExpr, final ClassNode rhsType) {
        typeCheckingContext.ifElseForWhileAssignmentTracker.computeIfAbsent(lhsExpr, lhs -> {
            ClassNode lhsType = lhs.getNodeMetaData(INFERRED_TYPE);
            List<ClassNode> types = new ArrayList<>(2);
            types.add(lhsType);
            return types;
        }).add(rhsType);
    }

    private void restoreTypeBeforeConditional() {
        typeCheckingContext.ifElseForWhileAssignmentTracker.forEach((var, types) -> {
            var.putNodeMetaData(INFERRED_TYPE, types.get(0));
        });
    }

    protected Map<VariableExpression, ClassNode> popAssignmentTracking(final Map<VariableExpression, List<ClassNode>> oldTracker) {
        Map<VariableExpression, ClassNode> assignments = new HashMap<>();
        typeCheckingContext.ifElseForWhileAssignmentTracker.forEach((var, types) -> {
            types.stream().filter(t -> t != null && t != UNKNOWN_PARAMETER_TYPE) // GROOVY-6099, GROOVY-10294
                    .reduce(WideningCategories::lowestUpperBound).ifPresent(type -> {
                assignments.put(var, type);
                storeType(var, type);
            });
        });
        typeCheckingContext.ifElseForWhileAssignmentTracker = oldTracker;
        return assignments;
    }

    protected Map<VariableExpression, List<ClassNode>> pushAssignmentTracking() {
        // memorize current assignment context
        Map<VariableExpression, List<ClassNode>> oldTracker = typeCheckingContext.ifElseForWhileAssignmentTracker;
        typeCheckingContext.ifElseForWhileAssignmentTracker = new HashMap<>();
        return oldTracker;
    }

    @Override
    public void visitArrayExpression(final ArrayExpression expression) {
        super.visitArrayExpression(expression);
        ClassNode elementType;
        List<Expression> expressions;
        if (expression.hasInitializer()) {
            elementType = expression.getElementType();
            expressions = expression.getExpressions();
        } else {
            elementType = int_TYPE;
            expressions = expression.getSizeExpression();
        }
        for (Expression elementExpr : expressions) {
            if (!checkCompatibleAssignmentTypes(elementType, getType(elementExpr), elementExpr, false)) {
                addStaticTypeError("Cannot convert from " + prettyPrintType(getType(elementExpr)) + " to " + prettyPrintType(elementType), elementExpr);
            }
        }
    }

    @Override
    public void visitCastExpression(final CastExpression expression) {
        ClassNode target = expression.getType();
        Expression source = expression.getExpression();
        applyTargetType(target, source); // GROOVY-9997

        source.visit(this);

        if (!expression.isCoerce() && !checkCast(target, source)) {
            addStaticTypeError("Inconvertible types: cannot cast " + prettyPrintType(getType(source)) + " to " + prettyPrintType(target), expression);
        }
    }

    protected boolean checkCast(final ClassNode targetType, final Expression source) {
        boolean sourceIsNull = isNullConstant(source);
        ClassNode expressionType = getType(source);
        if (targetType.isArray() && expressionType.isArray()) {
            return checkCast(targetType.getComponentType(), varX("foo", expressionType.getComponentType()));
        } else if (isPrimitiveChar(targetType) && isStringType(expressionType)
                && source instanceof ConstantExpression && source.getText().length() == 1) {
            // ex: (char) 'c'
        } else if (isWrapperCharacter(targetType) && (isStringType(expressionType) || sourceIsNull)
                && (sourceIsNull || source instanceof ConstantExpression && source.getText().length() == 1)) {
            // ex : (Character) 'c'
        } else if (isNumberCategory(getWrapper(targetType)) && (isNumberCategory(getWrapper(expressionType)) || isPrimitiveChar(expressionType))) {
            // ex: short s = (short) 0
        } else if (sourceIsNull && !isPrimitiveType(targetType)) {
            // ex: (Date)null
        } else if (isPrimitiveChar(targetType) && isPrimitiveType(expressionType) && isNumberType(expressionType)) {
            // char c = (char) ...
        } else if (sourceIsNull && isPrimitiveType(targetType) && !isPrimitiveBoolean(targetType)) {
            return false;
        } else if (!Modifier.isFinal(expressionType.getModifiers()) && targetType.isInterface()) {
            return true;
        } else if (!Modifier.isFinal(targetType.getModifiers()) && expressionType.isInterface()) {
            return true;
        } else if (!isAssignableTo(targetType, expressionType) && !implementsInterfaceOrIsSubclassOf(expressionType, targetType)) {
            return false;
        }
        return true;
    }

    @Override
    public void visitTernaryExpression(final TernaryExpression expression) {
        Map<VariableExpression, List<ClassNode>> oldTracker = pushAssignmentTracking();
        typeCheckingContext.pushTemporaryTypeInfo(); // capture instanceof restriction
        if (!(expression instanceof ElvisOperatorExpression)) {
            expression.getBooleanExpression().visit(this);
        }
        Expression trueExpression = expression.getTrueExpression();
        ClassNode typeOfTrue = findCurrentInstanceOfClass(trueExpression, null);
        typeOfTrue = Optional.ofNullable(typeOfTrue).orElse(visitValueExpression(trueExpression));
        typeCheckingContext.popTemporaryTypeInfo(); // instanceof doesn't apply to false branch
        Expression falseExpression = expression.getFalseExpression();
        ClassNode typeOfFalse = visitValueExpression(falseExpression);

        ClassNode resultType;
        if (isNullConstant(trueExpression) && isNullConstant(falseExpression)) { // GROOVY-5523
            resultType = checkForTargetType(trueExpression, UNKNOWN_PARAMETER_TYPE);
        } else if (isNullConstant(trueExpression) || (isEmptyCollection(trueExpression)
                && isOrImplements(typeOfTrue, typeOfFalse))) { // [] : List/Collection/Iterable
            resultType = wrapTypeIfNecessary(checkForTargetType(falseExpression, typeOfFalse));
        } else if (isNullConstant(falseExpression) || (isEmptyCollection(falseExpression)
                && isOrImplements(typeOfFalse, typeOfTrue))) { // List/Collection/Iterable : []
            resultType = wrapTypeIfNecessary(checkForTargetType(trueExpression, typeOfTrue));
        } else {
            typeOfFalse = checkForTargetType(falseExpression, typeOfFalse);
            typeOfTrue = checkForTargetType(trueExpression, typeOfTrue);
            resultType = lowestUpperBound(typeOfTrue, typeOfFalse);
        }
        storeType(expression, resultType);
        popAssignmentTracking(oldTracker);
    }

    /**
     * @param expr true or false branch of ternary expression
     * @return the inferred type of {@code expr}
     */
    private ClassNode visitValueExpression(final Expression expr) {
        if (expr instanceof ClosureExpression) {
            applyTargetType(checkForTargetType(expr, null), expr);
        }
        expr.visit(this);
        return getType(expr);
    }

    /**
     * @param expr true or false branch of ternary expression
     * @param type the inferred type of {@code expr}
     */
    private ClassNode checkForTargetType(final Expression expr, final ClassNode type) {
        ClassNode targetType = null;
        MethodNode enclosingMethod = typeCheckingContext.getEnclosingMethod();
        MethodCall enclosingMethodCall = (MethodCall)typeCheckingContext.getEnclosingMethodCall();
        BinaryExpression enclosingExpression = typeCheckingContext.getEnclosingBinaryExpression();
        if (enclosingExpression != null
                && isAssignment(enclosingExpression.getOperation().getType())
                && isTypeSource(expr, enclosingExpression.getRightExpression())) {
            targetType = getDeclaredOrInferredType(enclosingExpression.getLeftExpression());
        } else if (enclosingMethodCall != null
                && InvocationWriter.makeArgumentList(enclosingMethodCall.getArguments())
                        .getExpressions().stream().anyMatch(arg -> isTypeSource(expr, arg))) {
            // TODO: locate method target parameter
        } else if (enclosingMethod != null
                && !enclosingMethod.isAbstract()
                && !enclosingMethod.isVoidMethod()
                && isTypeSource(expr, enclosingMethod)) {
             targetType = enclosingMethod.getReturnType();
        }

        if (expr instanceof ClosureExpression) { // GROOVY-10271, GROOVY-10272
            return isSAMType(targetType) ? targetType : type;
        }

        if (targetType == null)
            targetType = type.getPlainNodeReference();
        if (type == UNKNOWN_PARAMETER_TYPE) return targetType;

        if (expr instanceof ConstructorCallExpression) { // GROOVY-9972, GROOVY-9983, GROOVY-10114
            inferDiamondType((ConstructorCallExpression) expr, targetType);
        }

        return adjustForTargetType(type, targetType);
    }

    private static ClassNode adjustForTargetType(final ClassNode resultType, final ClassNode targetType) {
        if (targetType.isUsingGenerics()
                && missesGenericsTypes(resultType)
                // GROOVY-10324, GROOVY-10342, et al.
                && !resultType.isGenericsPlaceHolder()) {
            // unchecked assignment
            // List<Type> list = new LinkedList()
            // Iterable<Type> iter = new LinkedList()
            // Collection<Type> col1 = Collections.emptyList()
            // Collection<Type> col2 = Collections.emptyList() ?: []
            // Collection<Type> view = ConcurrentHashMap.newKeySet()

            // the inferred type of the binary expression is the type of the RHS
            // "completed" with generics type information available from the LHS
            if (targetType.equals(resultType)) {
                // GROOVY-6126, GROOVY-6558, GROOVY-6564, et al.
                if (!targetType.isGenericsPlaceHolder()) return targetType;
            } else {
                // GROOVY-5640, GROOVY-9033, GROOVY-10220, GROOVY-10235, GROOVY-10688, et al.
                Map<GenericsTypeName, GenericsType> gt = new HashMap<>();
                extractGenericsConnections(gt, resultType, resultType.redirect());
                ClassNode sc = resultType;
                do { sc = getNextSuperClass(sc, targetType);
                } while (sc != null && !sc.equals(targetType));
                extractGenericsConnections(gt, targetType, sc);

                return applyGenericsContext(gt, resultType.redirect());
            }
        }

        return resultType;
    }

    private static boolean isTypeSource(final Expression expr, final Expression right) {
        if (right instanceof TernaryExpression) {
            return isTypeSource(expr, ((TernaryExpression) right).getTrueExpression())
                || isTypeSource(expr, ((TernaryExpression) right).getFalseExpression());
        }
        return expr == right;
    }

    private static boolean isTypeSource(final Expression expr, final MethodNode mNode) {
        boolean[] returned = new boolean[1];

        mNode.getCode().visit(new CodeVisitorSupport() {
            @Override
            public void visitReturnStatement(final ReturnStatement returnStatement) {
                if (isTypeSource(expr, returnStatement.getExpression())) {
                    returned[0] = true;
                }
            }
            @Override
            public void visitClosureExpression(final ClosureExpression expression) {
            }
        });

        if (!returned[0]) {
            new ReturnAdder(returnStatement -> {
                if (isTypeSource(expr, returnStatement.getExpression())) {
                    returned[0] = true;
                }
            }).visitMethod(mNode);
        }

        return returned[0];
    }

    private static boolean isEmptyCollection(final Expression expr) {
        return isEmptyList(expr) || isEmptyMap(expr);
    }

    private static boolean isEmptyList(final Expression expr) {
        return expr instanceof ListExpression && ((ListExpression) expr).getExpressions().isEmpty();
    }

    private static boolean isEmptyMap(final Expression expr) {
        return expr instanceof MapExpression && ((MapExpression) expr).getMapEntryExpressions().isEmpty();
    }

    @Override
    public void visitTryCatchFinally(final TryCatchStatement statement) {
        List<CatchStatement> catchStatements = statement.getCatchStatements();
        for (CatchStatement catchStatement : catchStatements) {
            ClassNode exceptionType = catchStatement.getExceptionType();
            typeCheckingContext.controlStructureVariables.put(catchStatement.getVariable(), exceptionType);
        }
        try {
            super.visitTryCatchFinally(statement);
        } finally {
            for (CatchStatement catchStatement : catchStatements) {
                typeCheckingContext.controlStructureVariables.remove(catchStatement.getVariable());
            }
        }
    }

    protected void storeType(final Expression exp, ClassNode cn) {
        if (cn == UNKNOWN_PARAMETER_TYPE) { // null for assignment or parameter
            // instead of storing an "unknown" type, reset the type information
            // by determining the declaration type of the expression
            cn = getOriginalDeclarationType(exp);
            // GROOVY-10356, GROOVY-10623: "def"
            if (isDynamicTyped(cn)) return;
        }
        if (cn != null && isPrimitiveType(cn)) {
            if (exp instanceof VariableExpression && ((VariableExpression) exp).isClosureSharedVariable()) {
                cn = getWrapper(cn);
            } else if (exp instanceof MethodCallExpression && ((MethodCallExpression) exp).isSafe()) {
                cn = getWrapper(cn);
            } else if (exp instanceof PropertyExpression && ((PropertyExpression) exp).isSafe()) {
                cn = getWrapper(cn);
            }
        }

        ClassNode oldValue = (ClassNode) exp.putNodeMetaData(INFERRED_TYPE, cn);
        if (oldValue != null) {
            // this may happen when a variable declaration type is wider than the subsequent assignment values
            // for example :
            // def o = 1 // first, an int
            // o = 'String' // then a string
            // o = new Object() // and eventually an object !
            // in that case, the INFERRED_TYPE corresponds to the current inferred type, while
            // DECLARATION_INFERRED_TYPE is the type which should be used for the initial type declaration
            ClassNode oldDIT = exp.getNodeMetaData(DECLARATION_INFERRED_TYPE);
            if (oldDIT != null) {
                exp.putNodeMetaData(DECLARATION_INFERRED_TYPE, cn == null ? oldDIT : lowestUpperBound(oldDIT, cn));
            } else {
                exp.putNodeMetaData(DECLARATION_INFERRED_TYPE, cn == null ? null : lowestUpperBound(oldValue, cn));
            }
        }

        if (exp instanceof VariableExpression) {
            VariableExpression var = (VariableExpression) exp;
            Variable accessedVariable = var.getAccessedVariable();
            if (accessedVariable instanceof VariableExpression) {
                if (accessedVariable != exp)
                    storeType((VariableExpression) accessedVariable, cn);
            } else if (accessedVariable instanceof Parameter
                    || accessedVariable instanceof PropertyNode
                    && ((PropertyNode) accessedVariable).getField().isSynthetic()) {
                ((AnnotatedNode) accessedVariable).putNodeMetaData(INFERRED_TYPE, cn);
            }
            if (cn != null && var.isClosureSharedVariable()) {
                List<ClassNode> assignedTypes = typeCheckingContext.closureSharedVariablesAssignmentTypes.computeIfAbsent(var, k -> new LinkedList<ClassNode>());
                assignedTypes.add(cn);
            }
            if (!typeCheckingContext.temporaryIfBranchTypeInformation.isEmpty()) {
                List<ClassNode> temporaryTypesForExpression = getTemporaryTypesForExpression(exp);
                if (temporaryTypesForExpression != null && !temporaryTypesForExpression.isEmpty()) {
                    // a type inference has been made on a variable whose type was defined in an instanceof block
                    // we erase available information with the new type
                    temporaryTypesForExpression.clear();
                }
            }
        }
    }

    protected ClassNode getResultType(ClassNode left, final int op, final ClassNode right, final BinaryExpression expr) {
        ClassNode leftRedirect = left.redirect();
        ClassNode rightRedirect = right.redirect();

        Expression leftExpression = expr.getLeftExpression();
        Expression rightExpression = expr.getRightExpression();

        if (op == EQUAL || op == ELVIS_EQUAL) {
            if (rightRedirect.isDerivedFrom(CLOSURE_TYPE)) {
                ClosureExpression closureExpression = null;
                if (rightExpression instanceof ClosureExpression) {
                    closureExpression = (ClosureExpression) rightExpression;
                } else if (rightExpression instanceof MethodReferenceExpression) {
                    closureExpression = rightExpression.getNodeMetaData(CONSTRUCTED_LAMBDA_EXPRESSION);
                }
                if (closureExpression != null) {
                    MethodNode abstractMethod = findSAM(left);
                    if (abstractMethod != null) {
                        return inferSAMTypeGenericsInAssignment(left, abstractMethod, right, closureExpression);
                    }
                }
            }

            if (leftExpression instanceof VariableExpression) {
                ClassNode initialType = getOriginalDeclarationType(leftExpression);

                if (isPrimitiveType(rightRedirect) && initialType.isDerivedFrom(Number_TYPE)) {
                    return getWrapper(right);
                }

                if (isPrimitiveType(initialType) && rightRedirect.isDerivedFrom(Number_TYPE)) {
                    return getUnwrapper(right);
                }

                // as anything can be assigned to a String, Class or [Bb]oolean, return the left type instead
                if (isWildcardLeftHandSide(initialType) && !isObjectType(initialType)) {
                    return initialType;
                }
            }

            if (!isObjectType(leftRedirect)) {
                if (rightExpression instanceof ListExpression) {
                    if (LIST_TYPE.equals(leftRedirect)
                            || ITERABLE_TYPE.equals(leftRedirect)
                            || Collection_TYPE.equals(leftRedirect)
                            || ArrayList_TYPE.isDerivedFrom(leftRedirect)) { // GROOVY-6912
                        return getLiteralResultType(left, right, ArrayList_TYPE); // GROOVY-7128
                    }
                    if (SET_TYPE.equals(leftRedirect)
                            || LinkedHashSet_TYPE.isDerivedFrom(leftRedirect)) { // GROOVY-6912
                        return getLiteralResultType(left, right, LinkedHashSet_TYPE); // GROOVY-7128
                    }
                }
                if (rightExpression instanceof MapExpression) {
                    if (MAP_TYPE.equals(leftRedirect)
                            || LinkedHashMap_TYPE.isDerivedFrom(leftRedirect)) {
                        return getLiteralResultType(left, right, LinkedHashMap_TYPE); // GROOVY-7128, GROOVY-9844
                    }
                }
            }

            return right;
        }

        if (isBoolIntrinsicOp(op)) {
            return boolean_TYPE;
        }

        if (op == FIND_REGEX) {
            return Matcher_TYPE;
        }

        if (isArrayOp(op)) {
            Expression copy = binX(leftExpression, expr.getOperation(), rightExpression);
            copy.setSourcePosition(expr); // do not propagate BINARY_EXP_TARGET, etc.
            MethodNode method = findMethodOrFail(copy, left, "getAt", rightRedirect);
            if (method != null && !isNumberCategory(getWrapper(rightRedirect))) {
                return inferReturnTypeGenerics(left, method, rightExpression);
            }
            return inferComponentType(left, right);
        }

        String operationName = getOperationName(op);
        if (operationName == null) throw new GroovyBugError(
                "Unknown result type for binary operator " + op);
        // the left operand is determining the result of the operation
        // for primitives and their wrapper we use a fixed table here:
        ClassNode mathResultType = getMathResultType(op, leftRedirect, rightRedirect, operationName);
        if (mathResultType != null) {
            return mathResultType;
        }
        // GROOVY-9006: compare to null for types that overload equals
        if ("equals".equals(operationName) && (left == UNKNOWN_PARAMETER_TYPE
                                            || right == UNKNOWN_PARAMETER_TYPE)) {
            return boolean_TYPE;
        }
        // GROOVY-5890: do not mix Class<Type> with Type
        if (leftExpression instanceof ClassExpression) {
            left = CLASS_Type.getPlainNodeReference();
        }
        MethodNode method = findMethodOrFail(expr, left, operationName, right);
        if (method != null) {
            storeTargetMethod(expr, method);
            typeCheckMethodsWithGenericsOrFail(left, new ClassNode[]{right}, method, expr);

            if (isAssignment(op)) return left;
            if (!"compareTo".equals(operationName))
                return inferReturnTypeGenerics(left, method, args(rightExpression));
        }

        if (isCompareToBoolean(op)) return boolean_TYPE;
        if (op == COMPARE_TO) return int_TYPE;
        return null;
    }

    /**
     * For "{@code List<Type> x = [...]}" or "{@code Set<Type> y = [...]}", etc.
     * the literal may be composed of sub-types of {@code Type}. In these cases,
     * {@code ArrayList<Type>} is an appropriate result type for the expression.
     */
    private static ClassNode getLiteralResultType(final ClassNode targetType, final ClassNode sourceType, final ClassNode baseType) {
        ClassNode resultType = sourceType.equals(baseType) ? sourceType
                : GenericsUtils.parameterizeType(sourceType, baseType.getPlainNodeReference());

        if (targetType.getGenericsTypes() != null
                && !GenericsUtils.buildWildcardType(targetType).isCompatibleWith(resultType)) {
            BiPredicate<GenericsType, GenericsType> isEqualOrSuper = (target, source) -> {
                if (target.isCompatibleWith(source.getType())) {
                    return true;
                }
                if (!target.isPlaceholder() && !target.isWildcard()) {
                    return GenericsUtils.buildWildcardType(getCombinedBoundType(target)).isCompatibleWith(source.getType());
                }
                return false;
            };

            GenericsType[] lgt = targetType.getGenericsTypes(), rgt = resultType.getGenericsTypes();
            if (IntStream.range(0, lgt.length).allMatch(i -> isEqualOrSuper.test(lgt[i], rgt[i]))) {
                resultType = GenericsUtils.parameterizeType(targetType, baseType.getPlainNodeReference());
            }
        }

        return resultType;
    }

    private static ClassNode getMathResultType(final int op, final ClassNode leftRedirect, final ClassNode rightRedirect, final String operationName) {
        if (isNumberType(leftRedirect) && isNumberType(rightRedirect)) {
            if (isOperationInGroup(op)) {
                if (isIntCategory(leftRedirect) && isIntCategory(rightRedirect)) return int_TYPE;
                if (isLongCategory(leftRedirect) && isLongCategory(rightRedirect)) return long_TYPE;
                if (isFloat(leftRedirect) && isFloat(rightRedirect)) return float_TYPE;
                if (isDouble(leftRedirect) && isDouble(rightRedirect)) return double_TYPE;
            } else if (isPowerOperator(op)) {
                return Number_TYPE;
            } else if (isBitOperator(op) || op == INTDIV || op == INTDIV_EQUAL) {
                if (isIntCategory(getUnwrapper(leftRedirect)) && isIntCategory(getUnwrapper(rightRedirect)))
                    return int_TYPE;
                if (isLongCategory(getUnwrapper(leftRedirect)) && isLongCategory(getUnwrapper(rightRedirect)))
                    return long_TYPE;
                if (isBigIntCategory(getUnwrapper(leftRedirect)) && isBigIntCategory(getUnwrapper(rightRedirect)))
                    return BigInteger_TYPE;
            } else if (isCompareToBoolean(op) || op == COMPARE_EQUAL || op == COMPARE_NOT_EQUAL) {
                return boolean_TYPE;
            }
        } else if (isPrimitiveChar(leftRedirect) && isPrimitiveChar(rightRedirect)) {
            if (isCompareToBoolean(op) || op == COMPARE_EQUAL || op == COMPARE_NOT_EQUAL) {
                return boolean_TYPE;
            }
        }

        // try to find a method for the operation
        if (isShiftOperation(operationName) && isNumberCategory(leftRedirect) && (isIntCategory(rightRedirect) || isLongCategory(rightRedirect))) {
            return leftRedirect;
        }

        // Divisions may produce different results depending on operand types
        if (isNumberCategory(getWrapper(rightRedirect)) && (isNumberCategory(getWrapper(leftRedirect)) && (DIVIDE == op || DIVIDE_EQUAL == op))) {
            if (isFloatingCategory(leftRedirect) || isFloatingCategory(rightRedirect)) {
                if (!isPrimitiveType(leftRedirect) || !isPrimitiveType(rightRedirect)) {
                    return Double_TYPE;
                }
                return double_TYPE;
            }
            if (DIVIDE == op) {
                return BigDecimal_TYPE;
            }
            return leftRedirect;
        } else if (isOperationInGroup(op)) {
            if (isNumberCategory(getWrapper(leftRedirect)) && isNumberCategory(getWrapper(rightRedirect))) {
                return getGroupOperationResultType(leftRedirect, rightRedirect);
            }
        }
        if (isNumberCategory(getWrapper(rightRedirect)) && isNumberCategory(getWrapper(leftRedirect)) && (MOD == op || MOD_EQUAL == op)) {
            return leftRedirect;
        }
        return null;
    }

    private ClassNode inferSAMTypeGenericsInAssignment(final ClassNode samType, final MethodNode abstractMethod, final ClassNode closureType, final ClosureExpression closureExpression) {
        // if the sam type or closure type do not provide generics information,
        // we cannot infer anything, thus we simply return the provided samUsage
        GenericsType[] samTypeGenerics = samType.getGenericsTypes();
        GenericsType[] closureGenerics = closureType.getGenericsTypes();
        if (samTypeGenerics == null || closureGenerics == null) return samType;

        // extract the generics from the return type
        Map<GenericsTypeName, GenericsType> connections = new HashMap<>();
        extractGenericsConnections(connections, wrapTypeIfNecessary(getInferredReturnType(closureExpression)), abstractMethod.getReturnType());

        // next we get the block parameter types and set the generics
        // information just like before
        // TODO: add vargs handling
        if (closureExpression.isParameterSpecified()) {
            Parameter[] closureParams = closureExpression.getParameters();
            Parameter[] methodParams = abstractMethod.getParameters();
            for (int i = 0, n = Math.min(closureParams.length, methodParams.length); i < n; i += 1) {
                ClassNode closureParamType = closureParams[i].getType();
                ClassNode methodParamType = methodParams[i].getType();
                extractGenericsConnections(connections, closureParamType, methodParamType);
            }
        }
        return applyGenericsContext(connections, samType.redirect());
    }

    protected static ClassNode getGroupOperationResultType(final ClassNode a, final ClassNode b) {
        if (isBigIntCategory(a) && isBigIntCategory(b)) return BigInteger_TYPE;
        if (isBigDecCategory(a) && isBigDecCategory(b)) return BigDecimal_TYPE;
        if (isBigDecimalType(a) || isBigDecimalType(b)) return BigDecimal_TYPE;
        if (isBigIntegerType(a) || isBigIntegerType(b)) {
            if (isBigIntCategory(a) && isBigIntCategory(b)) return BigInteger_TYPE;
            return BigDecimal_TYPE;
        }
        if (isPrimitiveDouble(a) || isPrimitiveDouble(b)) return double_TYPE;
        if (isWrapperDouble(a) || isWrapperDouble(b)) return Double_TYPE;
        if (isPrimitiveFloat(a) || isPrimitiveFloat(b)) return float_TYPE;
        if (isWrapperFloat(a) || isWrapperFloat(b)) return Float_TYPE;
        if (isPrimitiveLong(a) || isPrimitiveLong(b)) return long_TYPE;
        if (isWrapperLong(a) || isWrapperLong(b)) return Long_TYPE;
        if (isPrimitiveInt(a) || isPrimitiveInt(b)) return int_TYPE;
        if (isWrapperInteger(a) || isWrapperInteger(b)) return Integer_TYPE;
        if (isPrimitiveShort(a) || isPrimitiveShort(b)) return short_TYPE;
        if (isWrapperShort(a) || isWrapperShort(b)) return Short_TYPE;
        if (isPrimitiveByte(a) || isPrimitiveByte(b)) return byte_TYPE;
        if (isWrapperByte(a) || isWrapperByte(b)) return Byte_TYPE;
        if (isPrimitiveChar(a) || isPrimitiveChar(b)) return char_TYPE;
        if (isWrapperCharacter(a) || isWrapperCharacter(b)) return Character_TYPE;
        return Number_TYPE;
    }

    protected ClassNode inferComponentType(final ClassNode receiverType, final ClassNode subscriptType) {
        ClassNode componentType = receiverType.getComponentType();
        if (componentType == null) {
            MethodCallExpression mce;
            if (subscriptType != null) { // GROOVY-5521: check for a suitable "getAt(T)" method
                mce = callX(varX("#", receiverType), "getAt", varX("selector", subscriptType));
            } else { // GROOVY-8133: check for an "iterator()" method
                mce = callX(varX("#", receiverType), "iterator");
            }
            mce.setImplicitThis(false); // GROOVY-8943

            typeCheckingContext.pushErrorCollector();
            try {
                visitMethodCallExpression(mce);
            } finally {
                typeCheckingContext.popErrorCollector();
            }

            if (subscriptType != null) {
                componentType = getType(mce);
            } else {
                ClassNode iteratorType = getType(mce);
                if (isOrImplements(iteratorType, Iterator_TYPE) && (iteratorType.getGenericsTypes() != null
                        // ignore the iterator(Object) extension method, since it makes *everything* appear iterable
                        || !mce.<MethodNode>getNodeMetaData(DIRECT_METHOD_CALL_TARGET).getDeclaringClass().equals(OBJECT_TYPE))) {
                    componentType = Optional.ofNullable(iteratorType.getGenericsTypes()).map(gt -> getCombinedBoundType(gt[0])).orElse(OBJECT_TYPE);
                }
            }
        }
        return componentType;
    }

    protected MethodNode findMethodOrFail(final Expression expr, final ClassNode receiver, final String name, final ClassNode... args) {
        List<MethodNode> methods = findMethod(receiver, name, args);
        if (methods.isEmpty() && (expr instanceof BinaryExpression)) {
            BinaryExpression be = (BinaryExpression) expr;
            MethodCallExpression call = callX(be.getLeftExpression(), name, be.getRightExpression());
            methods = extension.handleMissingMethod(receiver, name, args(be.getLeftExpression()), args, call);
        }
        if (methods.isEmpty()) {
            addNoMatchingMethodError(receiver, name, args, expr);
        } else {
            if (areCategoryMethodCalls(methods, name, args)) {
                addCategoryMethodCallError(expr);
            }
            methods = disambiguateMethods(methods, receiver, args, expr);
            if (methods.size() == 1) {
                return methods.get(0);
            } else {
                addAmbiguousErrorMessage(methods, name, args, expr);
            }
        }
        return null;
    }

    private List<MethodNode> disambiguateMethods(List<MethodNode> methods, final ClassNode receiver, final ClassNode[] arguments, final Expression call) {
        if (methods.size() > 1 && receiver != null && arguments != null) {
            List<MethodNode> filteredWithGenerics = new LinkedList<>();
            for (MethodNode method : methods) {
                if (typeCheckMethodsWithGenerics(receiver, arguments, method)
                        && (method.getModifiers() & Opcodes.ACC_BRIDGE) == 0) {
                    filteredWithGenerics.add(method);
                }
            }
            if (filteredWithGenerics.size() == 1) {
                return filteredWithGenerics;
            }

            methods = extension.handleAmbiguousMethods(methods, call);
        }

        if (methods.size() > 1 && call instanceof MethodCall) {
            String methodName = ((MethodCall) call).getMethodAsString();
            methods = methods.stream().filter(m -> m.getName().equals(methodName)).collect(Collectors.toList());
        }

        return methods;
    }

    protected static String prettyPrintMethodList(final List<MethodNode> nodes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0, n = nodes.size(); i < n; i += 1) {
            MethodNode node = nodes.get(i);
            sb.append(prettyPrintType(node.getReturnType()));
            sb.append(" ");
            sb.append(prettyPrintTypeName(node.getDeclaringClass()));
            sb.append("#");
            sb.append(toMethodParametersString(node.getName(), extractTypesFromParameters(node.getParameters())));
            if (i < n - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    protected boolean areCategoryMethodCalls(final List<MethodNode> foundMethods, final String name, final ClassNode[] args) {
        boolean category = false;
        if ("use".equals(name) && args != null && args.length == 2 && args[1].equals(CLOSURE_TYPE)) {
            category = true;
            for (MethodNode method : foundMethods) {
                if (!(method instanceof ExtensionMethodNode) || !((ExtensionMethodNode) method).getExtensionMethodNode().getDeclaringClass().equals(DGM_CLASSNODE)) {
                    category = false;
                    break;
                }
            }
        }
        return category;
    }

    /**
     * Returns methods defined for the specified receiver and adds "non-existing"
     * methods that will be generated afterwards by the compiler; for example if
     * a method is using default values and the class node is not compiled yet.
     *
     * @param receiver the type to search for methods
     * @param name     the name of the methods to return
     * @return the methods that are defined on the receiver completed with stubs for future methods
     */
    protected List<MethodNode> findMethodsWithGenerated(final ClassNode receiver, final String name) {
        if (receiver.isArray()) {
            if (name.equals("clone")) { // GROOVY-10319: array clone -- <https://docs.oracle.com/javase/specs/jls/se8/html/jls-10.html#jls-10.7>
                MethodNode clone = new MethodNode("clone", Opcodes.ACC_PUBLIC, OBJECT_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, null);
                clone.setDeclaringClass(OBJECT_TYPE); // retain Object for declaringClass and returnType
                clone.setNodeMetaData(INFERRED_RETURN_TYPE, receiver);
                return Collections.singletonList(clone);
            } else {
                return OBJECT_TYPE.getMethods(name);
            }
        }

        List<MethodNode> methods = receiver.getMethods(name);

        // GROOVY-5166, GROOVY-9890, GROOVY-10700: non-static interface/trait methods
        Set<ClassNode> done = new HashSet<>();
        for (ClassNode next = receiver; next != null; next = next.getSuperClass()) {
            done.add(next);
            for (ClassNode face : next.getAllInterfaces()) {
                if (done.add(face)) {
                    for (MethodNode mn : face.getDeclaredMethods(name)) {
                        if (mn.isPublic() && !mn.isStatic()) methods.add(mn);
                    }
                }
            }
        }

        if (receiver.isInterface()) {
            methods.addAll(OBJECT_TYPE.getMethods(name));
        }

        if (!receiver.isResolved() && !methods.isEmpty()) {
            methods = addGeneratedMethods(receiver, methods);
        }

        return methods;
    }

    private static List<MethodNode> addGeneratedMethods(final ClassNode receiver, final List<MethodNode> methods) {
        // using a comparator of parameters
        List<MethodNode> result = new LinkedList<>();
        for (MethodNode method : methods) {
            result.add(method);
            Parameter[] parameters = method.getParameters();
            int counter = 0;
            int size = parameters.length;
            for (int i = size - 1; i >= 0; i--) {
                Parameter parameter = parameters[i];
                if (parameter != null && parameter.hasInitialExpression()) {
                    counter++;
                }
            }

            for (int j = 1; j <= counter; j++) {
                Parameter[] newParams = new Parameter[parameters.length - j];
                int index = 0;
                int k = 1;
                for (Parameter parameter : parameters) {
                    if (k > counter - j && parameter != null && parameter.hasInitialExpression()) {
                        k++;
                    } else if (parameter != null && parameter.hasInitialExpression()) {
                        newParams[index++] = parameter;
                        k++;
                    } else {
                        newParams[index++] = parameter;
                    }
                }
                MethodNode stubbed;
                if (method.isConstructor()) {
                    stubbed = new ConstructorNode(
                            method.getModifiers(),
                            newParams,
                            method.getExceptions(),
                            GENERATED_EMPTY_STATEMENT
                    );
                } else {
                    stubbed = new MethodNode(
                            method.getName(),
                            method.getModifiers(),
                            method.getReturnType(),
                            newParams,
                            method.getExceptions(),
                            GENERATED_EMPTY_STATEMENT
                    );
                    stubbed.setGenericsTypes(method.getGenericsTypes());
                }
                stubbed.setDeclaringClass(method.getDeclaringClass());
                result.add(stubbed);
            }
        }
        return result;
    }

    protected List<MethodNode> findMethod(ClassNode receiver, final String name, final ClassNode... args) {
        if (isPrimitiveType(receiver)) receiver = getWrapper(receiver);

        List<MethodNode> methods;
        if ("<init>".equals(name) && !receiver.isInterface()) {
            methods = addGeneratedMethods(receiver, new ArrayList<>(receiver.getDeclaredConstructors()));
            if (methods.isEmpty()) {
                MethodNode node = new ConstructorNode(Opcodes.ACC_PUBLIC, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, GENERATED_EMPTY_STATEMENT);
                node.setDeclaringClass(receiver);
                methods.add(node);
                if (receiver.isArray()) {
                    // No need to check the arguments against an array constructor: it just needs to exist. The array is
                    // created through coercion or by specifying its dimension(s), anyway, and would not match an
                    // arbitrary number of parameters.
                    return methods;
                }
            }
        } else {
            methods = findMethodsWithGenerated(receiver, name);
            if ("call".equals(name) && receiver.isInterface()) {
                MethodNode sam = findSAM(receiver);
                if (sam != null) {
                    MethodNode callMethod = new MethodNode("call", sam.getModifiers(), sam.getReturnType(), sam.getParameters(), sam.getExceptions(), sam.getCode());
                    callMethod.setDeclaringClass(sam.getDeclaringClass());
                    callMethod.setSourcePosition(sam);
                    methods.add(callMethod);
                }
            }
            if (!receiver.isStaticClass() && receiver.getOuterClass() != null
                    && typeCheckingContext.getEnclosingClassNodes().contains(receiver)) {
                ClassNode outer = receiver.getOuterClass();
                do { methods.addAll(findMethodsWithGenerated(outer, name));
                } while (!outer.isStaticClass() && (outer = outer.getOuterClass()) != null);
            }
            if (methods.isEmpty()) {
                addArrayMethods(methods, receiver, name, args);
            }
            if (methods.isEmpty() && (args == null || args.length == 0)) {
                // check if it's a property
                String pname = extractPropertyNameFromMethodName("get", name);
                if (pname == null) {
                    pname = extractPropertyNameFromMethodName("is", name);
                }
                PropertyNode property = null;
                if (pname != null) {
                    property = findProperty(receiver, pname);
                } else {
                    out: // look for property via getGetterName() for non-canonical case
                    for (ClassNode cn = receiver; cn != null; cn = cn.getSuperClass()) {
                        for (PropertyNode pn : cn.getProperties()) {
                            if (name.equals(pn.getGetterName())) {
                                property = pn;
                                break out;
                            }
                        }
                    }
                }
                if (property != null) {
                    int mods = Opcodes.ACC_PUBLIC | (property.isStatic() ? Opcodes.ACC_STATIC : 0);
                    MethodNode node = new MethodNode(name, mods, property.getType(), Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, GENERATED_EMPTY_STATEMENT);
                    node.setDeclaringClass(property.getDeclaringClass());
                    return Collections.singletonList(node);
                }
            } else if (methods.isEmpty() && args.length == 1) {
                // maybe we are looking for a setter ?
                String pname = extractPropertyNameFromMethodName("set", name);
                if (pname != null) {
                    PropertyNode property = findProperty(receiver, pname);
                    if (property != null && !Modifier.isFinal(property.getModifiers())) {
                        ClassNode type = property.getOriginType();
                        if (implementsInterfaceOrIsSubclassOf(wrapTypeIfNecessary(args[0]), wrapTypeIfNecessary(type))) {
                            int mods = Opcodes.ACC_PUBLIC | (property.isStatic() ? Opcodes.ACC_STATIC : 0);
                            MethodNode node = new MethodNode(name, mods, VOID_TYPE, new Parameter[]{new Parameter(type, name)}, ClassNode.EMPTY_ARRAY, GENERATED_EMPTY_STATEMENT);
                            node.setDeclaringClass(property.getDeclaringClass());
                            return Collections.singletonList(node);
                        }
                    }
                }
            }
        }

        if (!name.endsWith("init>")) { // also search for extension methods
            findDGMMethodsByNameAndArguments(getSourceUnit().getClassLoader(), receiver, name, args, methods);
        }
        methods = filterMethodsByVisibility(methods, typeCheckingContext.getEnclosingClassNode());
        List<MethodNode> chosen = chooseBestMethod(receiver, methods, args);
        if (!chosen.isEmpty()) return chosen;

        // GROOVY-5566
        if (receiver instanceof InnerClassNode && ((InnerClassNode) receiver).isAnonymous() && methods.size() == 1 && args != null && "<init>".equals(name)) {
            MethodNode constructor = methods.get(0);
            if (constructor.getParameters().length == args.length) {
                return methods;
            }
        }

        if (isClassClassNodeWrappingConcreteType(receiver)) { // GROOVY-6802, GROOVY-6803
            List<MethodNode> result = findMethod(receiver.getGenericsTypes()[0].getType(), name, args);
            if (!result.isEmpty()) return result;
        }

        if (isGStringType(receiver)) {
            return findMethod(STRING_TYPE, name, args);
        }

        return EMPTY_METHODNODE_LIST;
    }

    private PropertyNode findProperty(final ClassNode receiver, final String name) {
        for (ClassNode cn = receiver; cn != null; cn = cn.getSuperClass()) {
            PropertyNode property = cn.getProperty(name);
            if (property != null) return property;

            if (!cn.isStaticClass() && cn.getOuterClass() != null
                    && typeCheckingContext.getEnclosingClassNodes().contains(cn)) {
                ClassNode outer = cn.getOuterClass();
                do {
                    property = outer.getProperty(name);
                    if (property != null) return property;
                } while (!outer.isStaticClass() && (outer = outer.getOuterClass()) != null);
            }
        }
        return null;
    }

    /**
     * Given a method name and a prefix, returns the name of the property that should be looked up,
     * following the java beans rules. For example, "getName" would return "name", while
     * "getFullName" would return "fullName".
     * If the prefix is not found, returns null.
     *
     * @param prefix     the method name prefix ("get", "is", "set", ...)
     * @param methodName the method name
     * @return a property name if the prefix is found and the method matches the java beans rules, null otherwise
     */
    public static String extractPropertyNameFromMethodName(final String prefix, final String methodName) {
        if (prefix == null || methodName == null) return null;
        if (methodName.startsWith(prefix) && prefix.length() < methodName.length()) {
            String result = methodName.substring(prefix.length());
            String propertyName = decapitalize(result);
            if (result.equals(capitalize(propertyName))) return propertyName;
        }
        return null;
    }

    protected ClassNode getType(final ASTNode node) {
        ClassNode type = node.getNodeMetaData(INFERRED_TYPE);
        if (type != null) {
            return type;
        }
        if (node instanceof ClassExpression) {
            type = ((ClassExpression) node).getType();
            return makeClassSafe0(CLASS_Type, new GenericsType(type));
        }
        if (node instanceof VariableExpression) {
            VariableExpression vexp = (VariableExpression) node;
            type = isTraitSelf(vexp);
            if (type != null) return makeSelf(type);
            if (vexp.isThisExpression()) return makeThis();
            if (vexp.isSuperExpression()) return makeSuper();

            Variable variable = vexp.getAccessedVariable();
            if (variable instanceof FieldNode) {
                FieldNode fieldNode = (FieldNode) variable;
                ClassNode fieldType = fieldNode.getOriginType();
                if (!fieldNode.isStatic() && GenericsUtils.hasUnresolvedGenerics(fieldType)) {
                    ClassNode declType = fieldNode.getDeclaringClass(), thisType = typeCheckingContext.getEnclosingClassNode();
                    fieldType = resolveGenericsWithContext(extractPlaceHolders(thisType, declType), fieldType);
                }
                return fieldType;
            }
            if (variable != vexp && variable instanceof VariableExpression) {
                return getType((VariableExpression) variable);
            }
            if (variable instanceof Parameter) {
                Parameter parameter = (Parameter) variable;
                // check if param part of control structure - but not if inside instanceof
                List<ClassNode> temporaryTypesForExpression = getTemporaryTypesForExpression(vexp);
                if (temporaryTypesForExpression == null || temporaryTypesForExpression.isEmpty()) {
                    type = typeCheckingContext.controlStructureVariables.get(parameter);
                }
                // now check for closure override
                if (type == null && temporaryTypesForExpression == null) {
                    type = getTypeFromClosureArguments(parameter);
                }
                if (type != null) {
                    storeType(vexp, type);
                    return type;
                }
                return getType((Parameter) variable);
            }
            return vexp.getOriginType();
        }
        if (node instanceof Parameter || node instanceof FieldNode || node instanceof PropertyNode) {
            return ((Variable) node).getOriginType();
        }

        if (node instanceof MethodNode) {
            if ((node == GET_DELEGATE || node == GET_OWNER || node == GET_THISOBJECT)
                    && typeCheckingContext.getEnclosingClosure() != null) {
                return typeCheckingContext.getEnclosingClassNode();
            }
            type = ((MethodNode) node).getReturnType();
            return Optional.ofNullable(getInferredReturnType(node)).orElse(type);
        }
        if (node instanceof MethodCall) {
            if (node instanceof ConstructorCallExpression) {
                return ((ConstructorCallExpression) node).getType();
            }
            MethodNode target = node.getNodeMetaData(DIRECT_METHOD_CALL_TARGET);
            if (target != null) {
                return getType(target);
            }
        }
        if (node instanceof ClosureExpression) {
            type = CLOSURE_TYPE.getPlainNodeReference();
            ClassNode returnType = getInferredReturnType(node);
            if (returnType != null) {
                type.setGenericsTypes(new GenericsType[]{
                    new GenericsType(wrapTypeIfNecessary(returnType))
                });
            }
            Parameter[] parameters = ((ClosureExpression) node).getParameters();
            int nParameters = parameters == null ? 0
               : parameters.length == 0 ? -1 : parameters.length;
            type.putNodeMetaData(CLOSURE_ARGUMENTS, nParameters);
            return type;
        }

        if (node instanceof ListExpression) {
            return inferListExpressionType((ListExpression) node);
        }
        if (node instanceof MapExpression) {
            return inferMapExpressionType((MapExpression) node);
        }
        if (node instanceof RangeExpression) {
            RangeExpression re = (RangeExpression) node;
            ClassNode fromType = getType(re.getFrom());
            ClassNode toType = getType(re.getTo());
            if (fromType.equals(toType)) {
                type = wrapTypeIfNecessary(fromType);
            } else {
                type = wrapTypeIfNecessary(lowestUpperBound(fromType, toType));
            }
            return makeClassSafe0(RANGE_TYPE, new GenericsType(type));
        }
        if (node instanceof SpreadExpression) {
            type = getType(((SpreadExpression) node).getExpression());
            return inferComponentType(type, null); // for list literal
        }
        if (node instanceof UnaryPlusExpression) {
            return getType(((UnaryPlusExpression) node).getExpression());
        }
        if (node instanceof UnaryMinusExpression) {
            return getType(((UnaryMinusExpression) node).getExpression());
        }
        if (node instanceof BitwiseNegationExpression) {
            return getType(((BitwiseNegationExpression) node).getExpression());
        }
        return ((Expression) node).getType();
    }

    private ClassNode getTypeFromClosureArguments(final Parameter parameter) {
        for (TypeCheckingContext.EnclosingClosure enclosingClosure : typeCheckingContext.getEnclosingClosureStack()) {
            ClosureExpression closureExpression = enclosingClosure.getClosureExpression();
            ClassNode[] closureParamTypes = closureExpression.getNodeMetaData(CLOSURE_ARGUMENTS);
            if (closureParamTypes != null) {
                Parameter[] parameters = closureExpression.getParameters();
                if (parameters != null) {
                    final int n = parameters.length;
                    String parameterName = parameter.getName();
                    if (n == 0 && parameterName.equals("it")) {
                        return closureParamTypes.length > 0 ? closureParamTypes[0] : null;
                    }
                    for (int i = 0; i < n; i += 1) {
                        if (parameterName.equals(parameters[i].getName())) {
                            return closureParamTypes.length > i ? closureParamTypes[i] : null;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static ClassNode makeSelf(final ClassNode trait) {
        ClassNode selfType = trait;
        Set<ClassNode> selfTypes = Traits.collectSelfTypes(selfType, new LinkedHashSet<>());
        if (!selfTypes.isEmpty()) {
            selfTypes.add(selfType);
            selfType = new UnionTypeClassNode(selfTypes.toArray(ClassNode.EMPTY_ARRAY));
        }
        return selfType;
    }

    private ClassNode makeSuper() {
        return makeType(typeCheckingContext.getEnclosingClassNode().getUnresolvedSuperClass(), typeCheckingContext.isInStaticContext);
    }

    private ClassNode makeThis() {
        return makeType(typeCheckingContext.getEnclosingClassNode(), typeCheckingContext.isInStaticContext);
    }

    /**
     * Wrap type in Class&lt;&gt; if usingClass==true.
     */
    private static ClassNode makeType(final ClassNode cn, final boolean usingClass) {
        if (usingClass) {
            ClassNode clazzType = CLASS_Type.getPlainNodeReference();
            clazzType.setGenericsTypes(new GenericsType[]{new GenericsType(cn)});
            return clazzType;
        } else {
            return cn;
        }
    }

    /**
     * Stores the inferred return type of a closure or a method. We are using a separate key to store
     * inferred return type because the inferred type of a closure is {@link Closure}, which is different
     * from the inferred type of the code of the closure.
     *
     * @param node a {@link ClosureExpression} or a {@link MethodNode}
     * @param type the inferred return type of the code
     * @return the old value of the inferred type
     */
    protected ClassNode storeInferredReturnType(final ASTNode node, final ClassNode type) {
        if (!(node instanceof ClosureExpression)) {
            throw new IllegalArgumentException("Storing inferred return type is only allowed on closures but found " + node.getClass());
        }
        return (ClassNode) node.putNodeMetaData(INFERRED_RETURN_TYPE, type);
    }

    /**
     * Returns the inferred return type of a closure or a method, if stored on the AST node. This method
     * doesn't perform any type inference by itself.
     *
     * @param exp a {@link ClosureExpression} or {@link MethodNode}
     * @return the inferred type, as stored on node metadata.
     */
    protected ClassNode getInferredReturnType(final ASTNode exp) {
        return exp.getNodeMetaData(INFERRED_RETURN_TYPE);
    }

    protected ClassNode inferListExpressionType(final ListExpression list) {
        List<Expression> expressions = list.getExpressions();
        int nExpressions = expressions.size();
        if (nExpressions == 0) {
            return list.getType();
        }
        ClassNode listType = list.getType();
        GenericsType[] genericsTypes = listType.getGenericsTypes();
        if ((genericsTypes == null
                || genericsTypes.length == 0
                || (genericsTypes.length == 1 && isObjectType(genericsTypes[0].getType())))) {
            // maybe we can infer the component type
            List<ClassNode> nodes = new ArrayList<>(nExpressions);
            for (Expression expression : expressions) {
                if (isNullConstant(expression)) {
                    // a null element is found in the list, skip it because we'll use the other elements from the list
                } else {
                    nodes.add(getType(expression));
                }
            }
            if (!nodes.isEmpty()) {
                ClassNode itemType = lowestUpperBound(nodes);

                listType = listType.getPlainNodeReference();
                listType.setGenericsTypes(new GenericsType[]{new GenericsType(wrapTypeIfNecessary(itemType))});
            }
        }
        return listType;
    }

    protected static boolean isNullConstant(final Expression expression) {
        return expression instanceof ConstantExpression && ((ConstantExpression) expression).isNullExpression();
    }

    protected static boolean isThisExpression(final Expression expression) {
        return expression instanceof VariableExpression && ((VariableExpression) expression).isThisExpression();
    }

    protected static boolean isSuperExpression(final Expression expression) {
        return expression instanceof VariableExpression && ((VariableExpression) expression).isSuperExpression();
    }

    protected ClassNode inferMapExpressionType(final MapExpression map) {
        ClassNode mapType = LinkedHashMap_TYPE.getPlainNodeReference();
        List<MapEntryExpression> entryExpressions = map.getMapEntryExpressions();
        int nExpressions = entryExpressions.size();
        if (nExpressions == 0) return mapType;

        GenericsType[] genericsTypes = mapType.getGenericsTypes();
        if (genericsTypes == null
                || genericsTypes.length < 2
                || (genericsTypes.length == 2 && isObjectType(genericsTypes[0].getType()) && isObjectType(genericsTypes[1].getType()))) {
            ClassNode keyType;
            ClassNode valueType;
            List<ClassNode> keyTypes = new ArrayList<>(nExpressions);
            List<ClassNode> valueTypes = new ArrayList<>(nExpressions);
            for (MapEntryExpression entryExpression : entryExpressions) {
                valueType = getType(entryExpression.getValueExpression());
                if (!(entryExpression.getKeyExpression() instanceof SpreadMapExpression)) {
                    keyType = getType(entryExpression.getKeyExpression());
                } else { // GROOVY-7247
                    valueType = GenericsUtils.parameterizeType(valueType, MAP_TYPE);
                    keyType = getCombinedBoundType(valueType.getGenericsTypes()[0]);
                    valueType = getCombinedBoundType(valueType.getGenericsTypes()[1]);
                }
                keyTypes.add(keyType);
                valueTypes.add(valueType);
            }
            keyType = lowestUpperBound(keyTypes);
            valueType = lowestUpperBound(valueTypes);
            if (!isObjectType(keyType) || !isObjectType(valueType)) {
                mapType.setGenericsTypes(new GenericsType[]{new GenericsType(wrapTypeIfNecessary(keyType)), new GenericsType(wrapTypeIfNecessary(valueType))});
            }
        }
        return mapType;
    }

    /**
     * If a method call returns a parameterized type, then perform additional
     * inference on the return type, so that the type gets actual type arguments.
     * For example, the method {@code Arrays.asList(T...)} is parameterized with
     * {@code T}, which can be deduced type arguments or call arguments.
     *
     * @param method            the method node
     * @param arguments         the method call arguments
     * @param receiver          the object expression type
     */
    protected ClassNode inferReturnTypeGenerics(final ClassNode receiver, final MethodNode method, final Expression arguments) {
        return inferReturnTypeGenerics(receiver, method, arguments, null);
    }

    /**
     * If a method call returns a parameterized type, then perform additional
     * inference on the return type, so that the type gets actual type arguments.
     * For example, the method {@code Arrays.asList(T...)} is parameterized with
     * {@code T}, which can be deduced type arguments or call arguments.
     *
     * @param method            the method node
     * @param arguments         the method call arguments
     * @param receiver          the object expression type
     * @param explicitTypeHints type arguments (optional), for example {@code Collections.<String>emptyList()}
     */
    protected ClassNode inferReturnTypeGenerics(final ClassNode receiver, final MethodNode method, final Expression arguments, final GenericsType[] explicitTypeHints) {
        ClassNode returnType = method.isConstructor() ? method.getDeclaringClass() : method.getReturnType();
        if (!GenericsUtils.hasUnresolvedGenerics(returnType)) {
            // GROOVY-7538: replace "Type<?>" with "Type<? extends/super X>" for any "Type<T extends/super X>"
            if (getGenericsWithoutArray(returnType) != null) returnType = boundUnboundedWildcards(returnType);

            return returnType;
        }

        if (method instanceof ExtensionMethodNode) {
            ArgumentListExpression args = getExtensionArguments(receiver, method, arguments);
            MethodNode extension = ((ExtensionMethodNode) method).getExtensionMethodNode();
            return inferReturnTypeGenerics(receiver, extension, args, explicitTypeHints);
        }

        Map<GenericsTypeName, GenericsType> context = method.isStatic() || method.isConstructor()
                                            ? null : extractPlaceHoldersVisibleToDeclaration(receiver, method, arguments);
        GenericsType[] methodGenericTypes = method.isConstructor() ? method.getDeclaringClass().getGenericsTypes() : applyGenericsContext(context, method.getGenericsTypes());

        // 1) resolve type parameters of method

        if (methodGenericTypes != null) {
            Map<GenericsTypeName, GenericsType> resolvedPlaceholders = new HashMap<>();
            for (GenericsType gt : methodGenericTypes) resolvedPlaceholders.put(new GenericsTypeName(gt.getName()), gt);
            applyGenericsConnections(extractGenericsConnectionsFromArguments(methodGenericTypes, Arrays.stream(method.getParameters()).map(param ->
                new Parameter(applyGenericsContext(context, param.getType()), param.getName())
            ).toArray(Parameter[]::new), arguments, explicitTypeHints), resolvedPlaceholders);

            returnType = applyGenericsContext(resolvedPlaceholders, returnType);
        }

        // 2) resolve type parameters of method's enclosing context

        if (context != null) {
            returnType = applyGenericsContext(context, returnType);

            if (receiver.getGenericsTypes() == null && receiver.redirect().getGenericsTypes() != null
                    && !receiver.isGenericsPlaceHolder() && GenericsUtils.hasUnresolvedGenerics(returnType)) {
                returnType = returnType.getPlainNodeReference(); // GROOVY-10049: not "Stream<E>" for raw type
            }
        }

        // 3) resolve bounds of type parameters from calling context

        returnType = applyGenericsContext(extractGenericsParameterMapOfThis(typeCheckingContext), returnType);

        return returnType;
    }

    /**
     * Resolves type parameters declared by method from type or call arguments.
     */
    private Map<GenericsTypeName, GenericsType> extractGenericsConnectionsFromArguments(final GenericsType[] methodGenericTypes, final Parameter[] parameters, final Expression arguments, final GenericsType[] explicitTypeHints) {
        Map<GenericsTypeName, GenericsType> resolvedPlaceholders = new HashMap<>();

        if (asBoolean(explicitTypeHints)) { // resolve type parameters from type arguments
            int n = methodGenericTypes.length;
            if (n == explicitTypeHints.length) {
                for (int i = 0; i < n; i += 1) {
                    resolvedPlaceholders.put(new GenericsTypeName(methodGenericTypes[i].getName()), explicitTypeHints[i]);
                }
            }
        } else if (parameters.length > 0) { // resolve type parameters from call arguments
            List<Expression> expressions = InvocationWriter.makeArgumentList(arguments).getExpressions();
            boolean isVargs = isVargs(parameters);
            int nArguments = expressions.size();
            int nParams = parameters.length;

            if (isVargs ? nArguments >= nParams - 1 : nArguments == nParams) {
                for (int i = 0; i < nArguments; i += 1) {
                    if (isNullConstant(expressions.get(i)))
                        continue; // GROOVY-9984: skip null
                    ClassNode paramType = parameters[Math.min(i, nParams - 1)].getType();
                    ClassNode argumentType = getDeclaredOrInferredType(expressions.get(i));

                    if (GenericsUtils.hasUnresolvedGenerics(paramType)) {
                        // if supplying array param with multiple arguments or single non-array argument, infer using element type
                        if (isVargs && (i >= nParams || (i == nParams - 1 && (nArguments > nParams || !argumentType.isArray())))) {
                            paramType = paramType.getComponentType();
                        }

                        if (isClosureWithType(argumentType)) {
                            MethodNode sam = findSAM(paramType);
                            if (sam != null) { // adapt closure to functional interface or other single-abstract-method class
                                argumentType = convertClosureTypeToSAMType(expressions.get(i), argumentType, sam, paramType);
                            }
                        }

                        Map<GenericsTypeName, GenericsType> connections = new HashMap<>();
                        extractGenericsConnections(connections, wrapTypeIfNecessary(argumentType), paramType);
                        connections.forEach((gtn, gt) -> resolvedPlaceholders.merge(gtn, gt, (gt1, gt2) -> {
                            // GROOVY-10339: incorporate another witness
                            return getCombinedGenericsType(gt1, gt2);
                        }));
                    }
                }
            }

            // in case of "<T, U extends Type<T>>" we can learn about "T" from resolved "U"
            Map<GenericsTypeName, GenericsType> connections = Arrays.stream(methodGenericTypes)
                    .collect(toMap(gt -> new GenericsTypeName(gt.getName()), gt -> gt, (v,x) -> v));
            extractGenericsConnectionsForSuperClassAndInterfaces(connections, resolvedPlaceholders);
        }

        for (GenericsType gt : methodGenericTypes) {
            // GROOVY-8409, GROOVY-10067, et al.: provide "no type witness" mapping for param
            resolvedPlaceholders.computeIfAbsent(new GenericsTypeName(gt.getName()), gtn -> {
                GenericsType xxx = new GenericsType(ClassHelper.makeWithoutCaching("#"),
                        applyGenericsContext(resolvedPlaceholders, gt.getUpperBounds()),
                        applyGenericsContext(resolvedPlaceholders, gt.getLowerBound()));
                xxx.getType().setRedirect(gt.getType().redirect());
                xxx.putNodeMetaData(GenericsType.class, gt);
                xxx.setName("#" + gt.getName());
                xxx.setPlaceholder(true);
                return xxx;
            });
        }

        return resolvedPlaceholders;
    }

    /**
     * Given method call like "m(Collections.emptyList())", the type of the call
     * argument is {@code List<T>} without explicit type arguments. Knowning the
     * method target of "m", {@code T} could be resolved.
     */
    private void resolvePlaceholdersFromImplicitTypeHints(final ClassNode[] actuals, final ArgumentListExpression argumentList, final Parameter[] parameterArray) {
        int np = parameterArray.length;
        for (int i = 0, n = actuals.length; np > 0 && i < n; i += 1) {
            Expression a = argumentList.getExpression(i);
            Parameter p = parameterArray[Math.min(i, np - 1)];

            ClassNode at = actuals[i], pt = p.getOriginType();
            if (!isUsingGenericsOrIsArrayUsingGenerics(pt)) continue;
            if (i >= (np - 1) && pt.isArray() && !at.isArray()) pt = pt.getComponentType();

            if (a instanceof ListExpression) {
                actuals[i] = getLiteralResultType(pt, at, ArrayList_TYPE);
            } else if (a instanceof MapExpression) {
                actuals[i] = getLiteralResultType(pt, at, LinkedHashMap_TYPE);
            } else if (a instanceof ConstructorCallExpression) {
                inferDiamondType((ConstructorCallExpression) a, pt); // GROOVY-10086
            } else if (a instanceof TernaryExpression && at.getGenericsTypes() != null && at.getGenericsTypes().length == 0) {
                // GROOVY-9983: double diamond scenario -- "m(flag ? new Type<>(...) : new Type<>(...))"
                typeCheckingContext.pushEnclosingBinaryExpression(assignX(varX(p), a, a));
                a.visit(this); // re-visit with target type witness
                typeCheckingContext.popEnclosingBinaryExpression();
                actuals[i] = getType(a);
            }

            // check for method call without type arguments, with a known target
            if (!(a instanceof MethodCall) || (a instanceof MethodCallExpression
                    && ((MethodCallExpression) a).isUsingGenerics())) continue;
            MethodNode aNode = a.getNodeMetaData(DIRECT_METHOD_CALL_TARGET);
            if (aNode == null || aNode.getGenericsTypes() == null) continue;

            // and unknown generics
            if (!GenericsUtils.hasUnresolvedGenerics(at)) continue;

            while (!at.equals(pt) && !isObjectType(at) && !isGenericsPlaceHolderOrArrayOf(at) && !isGenericsPlaceHolderOrArrayOf(pt)) {
                at = applyGenericsContext(GenericsUtils.extractPlaceholders(at), getNextSuperClass(at, pt));
            }

            // try to resolve placeholder(s) in argument type using parameter type

            Map<GenericsTypeName, GenericsType> linked = new HashMap<>();
            Map<GenericsTypeName, GenericsType> source = GenericsUtils.extractPlaceholders(at);
            Map<GenericsTypeName, GenericsType> target = GenericsUtils.extractPlaceholders(pt);

            if (at.isGenericsPlaceHolder()) // GROOVY-10482: call argument via "def <T> T m()"
                target.put(new GenericsTypeName(at.getUnresolvedName()), pt.asGenericsType());

            // connect E:T from source to E:Type from target
            for (GenericsType placeholder : aNode.getGenericsTypes()) {
                for (Map.Entry<GenericsTypeName, GenericsType> e : source.entrySet()) {
                    if (e.getValue().getNodeMetaData(GenericsType.class) == placeholder) {
                        Optional.ofNullable(target.get(e.getKey()))
                            // skip "f(g())" for "f(T<String>)" and "<U extends Number> U g()"
                            .filter(gt -> isAssignableTo(gt.getType(), placeholder.getType()))
                            .ifPresent(gt -> linked.put(new GenericsTypeName(e.getValue().getName()), gt));
                        break;
                    }
                }
            }

            actuals[i] = applyGenericsContext(linked, at);
        }
    }

    private static void extractGenericsConnectionsForSuperClassAndInterfaces(final Map<GenericsTypeName, GenericsType> resolvedPlaceholders, final Map<GenericsTypeName, GenericsType> connections) {
        for (GenericsType value : new HashSet<>(connections.values())) {
            if (!value.isPlaceholder() && !value.isWildcard()) {
                ClassNode valueType = value.getType();
                List<ClassNode> deepNodes = new LinkedList<>();
                ClassNode unresolvedSuperClass = valueType.getUnresolvedSuperClass();
                if (unresolvedSuperClass != null && unresolvedSuperClass.isUsingGenerics()) {
                    deepNodes.add(unresolvedSuperClass);
                }
                for (ClassNode node : valueType.getUnresolvedInterfaces()) {
                    if (node.isUsingGenerics()) {
                        deepNodes.add(node);
                    }
                }
                if (!deepNodes.isEmpty()) {
                    for (GenericsType genericsType : resolvedPlaceholders.values()) {
                        ClassNode lowerBound = genericsType.getLowerBound();
                        if (lowerBound != null) {
                            for (ClassNode deepNode : deepNodes) {
                                if (lowerBound.equals(deepNode)) {
                                    extractGenericsConnections(connections, deepNode, lowerBound);
                                }
                            }
                        }
                        ClassNode[] upperBounds = genericsType.getUpperBounds();
                        if (upperBounds != null) {
                            for (ClassNode upperBound : upperBounds) {
                                for (ClassNode deepNode : deepNodes) {
                                    if (upperBound.equals(deepNode)) {
                                        extractGenericsConnections(connections, deepNode, upperBound);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Converts a Closure type to the appropriate SAM type, which can be used to
     * infer return type generics.
     *
     * @param expression  the argument expression
     * @param closureType the inferred type of {@code expression}
     * @param samType     the target type for the argument expression
     * @return SAM type augmented using information from the argument expression
     */
    private static ClassNode convertClosureTypeToSAMType(final Expression expression, final ClassNode closureType, final MethodNode sam, final ClassNode samType) {
        Map<GenericsTypeName, GenericsType> samTypeConnections = GenericsUtils.extractPlaceholders(samType);
        samTypeConnections.replaceAll((xx, gt) -> // GROOVY-9762, GROOVY-9803: reduce "? super T" to "T"
            Optional.ofNullable(gt.getLowerBound()).map(GenericsType::new).orElse(gt)
        );
        ClassNode closureReturnType = closureType.getGenericsTypes()[0].getType();

        Parameter[] parameters = sam.getParameters();
        if (parameters.length > 0
                && expression instanceof MethodPointerExpression
                && GenericsUtils.hasUnresolvedGenerics(closureReturnType)) {
            // try to resolve referenced method type parameters in return type
            MethodPointerExpression mp = (MethodPointerExpression) expression;
            List<MethodNode> candidates = mp.getNodeMetaData(MethodNode.class);
            if (candidates != null && !candidates.isEmpty()) {
                ClassNode[] paramTypes = applyGenericsContext(samTypeConnections, extractTypesFromParameters(parameters));
                ClassNode[] matchTypes = candidates.stream()
                        .map(candidate -> collateMethodReferenceParameterTypes(mp, candidate))
                        .filter(candidate -> checkSignatureSuitability(candidate, paramTypes))
                        .findFirst().orElse(null); // TODO: order signatures by param distance
                if (matchTypes != null) {
                    Map<GenericsTypeName, GenericsType> connections = new HashMap<>();
                    for (int i = 0, n = parameters.length; i < n; i += 1) {
                        // SAM parameters should align with the referenced method's parameters
                        extractGenericsConnections(connections, paramTypes[i], matchTypes[i]);
                    }
                    // convert the method reference's generics into the SAM's generics domain
                    closureReturnType = applyGenericsContext(connections, closureReturnType);
                    // apply known generics connections to the SAM's placeholders in the return type
                    closureReturnType = applyGenericsContext(samTypeConnections, closureReturnType);
                }
            }
        }

        // the SAM's return type exactly corresponds to the inferred closure return type
        extractGenericsConnections(samTypeConnections, closureReturnType, sam.getReturnType());

        // repeat the same for each parameter given in the ClosureExpression
        if (parameters.length > 0 && expression instanceof ClosureExpression) {
            return closureType; // TODO
        }

        return applyGenericsContext(samTypeConnections, samType.redirect());
    }

    private static ClassNode[] collateMethodReferenceParameterTypes(final MethodPointerExpression source, final MethodNode target) {
        Parameter[] params;

        if (target instanceof ExtensionMethodNode && !((ExtensionMethodNode) target).isStaticExtension()) {
            params = ((ExtensionMethodNode) target).getExtensionMethodNode().getParameters();
        } else if (!target.isStatic() && source.getExpression() instanceof ClassExpression) {
            ClassNode thisType = ((ClassExpression) source.getExpression()).getType();
            // there is an implicit parameter for "String::length"
            int n = target.getParameters().length;
            params = new Parameter[n + 1];
            params[0] = new Parameter(thisType, "");
            System.arraycopy(target.getParameters(), 0, params, 1, n);
        } else {
            params = target.getParameters();
        }

        return extractTypesFromParameters(params);
    }

    private static boolean checkSignatureSuitability(final ClassNode[] receiverTypes, final ClassNode[] providerTypes) {
        int n = receiverTypes.length;
        if (n != providerTypes.length) {
            return false;
        }
        for (int i = 0; i < n; i += 1) {
            // for method closure, SAM parameters act like arguments
            if (!isAssignableTo(providerTypes[i], receiverTypes[i])) {
                return false;
            }
        }
        return true;
    }

    private ClassNode getDeclaredOrInferredType(final Expression expression) {
        ClassNode declaredOrInferred;
        // in case of "T t = new ExtendsOrImplementsT()", return T for the expression type
        if (expression instanceof Variable && !((Variable) expression).isDynamicTyped()) {
            declaredOrInferred = getOriginalDeclarationType(expression); // GROOVY-9996
        } else {
            declaredOrInferred = getType(expression);
        }
        // GROOVY-10011: apply instanceof constraints to either option
        return getInferredTypeFromTempInfo(expression, declaredOrInferred);
    }

    private static class ExtensionMethodDeclaringClass {
    }

    private static ArgumentListExpression getExtensionArguments(final ClassNode receiver, final MethodNode method, final Expression arguments) {
        VariableExpression self = varX("$self", receiver); // implicit first argument
        self.putNodeMetaData(ExtensionMethodDeclaringClass.class, method.getDeclaringClass());

        ArgumentListExpression args = new ArgumentListExpression();
        args.addExpression(self);
        if (arguments instanceof TupleExpression) {
            for (Expression argument : (TupleExpression) arguments) {
                args.addExpression(argument);
            }
        } else {
            args.addExpression(arguments);
        }
        return args;
    }

    private static boolean isGenericsPlaceHolderOrArrayOf(ClassNode cn) {
        while (cn.isArray()) cn = cn.getComponentType();
        return cn.isGenericsPlaceHolder();
    }

    private static Map<GenericsTypeName, GenericsType> extractPlaceHolders(final ClassNode receiver, final ClassNode declaringClass) {
        Map<GenericsTypeName, GenericsType> result = null;
        ClassNode[] todo;
        if (receiver instanceof UnionTypeClassNode) {
            todo = ((UnionTypeClassNode) receiver).getDelegates();
        } else {
            todo = new ClassNode[] {!isPrimitiveType(declaringClass) ? wrapTypeIfNecessary(receiver) : receiver};
        }
        for (ClassNode type : todo) {
            for (ClassNode current = type; current != null; ) {
                Map<GenericsTypeName, GenericsType> placeHolders = new HashMap<>();
                if (current.isGenericsPlaceHolder())
                    // GROOVY-10622: type param bound "T extends Set<Type>"
                    current = current.asGenericsType().getUpperBounds()[0];
                // GROOVY-10055: handle diamond or raw type
                else if (current.getGenericsTypes() != null
                        ? current.getGenericsTypes().length == 0
                        : current.redirect().getGenericsTypes() != null) {
                    for (GenericsType gt : current.redirect().getGenericsTypes()) {
                        ClassNode cn = gt.getUpperBounds() != null ? gt.getUpperBounds()[0] : gt.getType().redirect();
                        placeHolders.put(new GenericsTypeName(gt.getName()), cn.getPlainNodeReference().asGenericsType());
                    }
                }

                boolean currentIsDeclaring = current.equals(declaringClass) || isGenericsPlaceHolderOrArrayOf(declaringClass);
                if (currentIsDeclaring) {
                    extractGenericsConnections(placeHolders, current, declaringClass);
                } else {
                    GenericsUtils.extractPlaceholders(current, placeHolders);
                }

                if (result != null) { // merge maps
                    for (Map.Entry<GenericsTypeName, GenericsType> entry : placeHolders.entrySet()) {
                        GenericsType gt = entry.getValue();
                        if (!gt.isPlaceholder()) continue;
                        GenericsType referenced = result.get(new GenericsTypeName(gt.getName()));
                        if (referenced == null) continue;
                        entry.setValue(referenced);
                    }
                }
                result = placeHolders;

                // we are done if we are now in the declaring class
                if (currentIsDeclaring) break;

                current = getNextSuperClass(current, declaringClass);
                if (current == null && isClassType(declaringClass)) {
                    // this can happen if the receiver is Class<Foo>, then
                    // the actual receiver is Foo and declaringClass is Class
                    current = declaringClass;
                } else {
                    current = applyGenericsContext(placeHolders, current);
                }
            }
        }
        if (result == null) {
            throw new GroovyBugError("Declaring class " + prettyPrintTypeName(declaringClass) + " was not matched with receiver " + prettyPrintTypeName(receiver) + ". This should not have happened!");
        }
        return result;
    }

    private static Map<GenericsTypeName, GenericsType> extractPlaceHoldersVisibleToDeclaration(final ClassNode receiver, final MethodNode method, final Expression argument) {
        Map<GenericsTypeName, GenericsType> result;
        if (method.isStatic()) {
            result = new HashMap<>();
        } else {
            ClassNode declaring = method.getDeclaringClass();
            if (argument instanceof TupleExpression) { // resolve extension method class
                List<Expression> arguments = ((TupleExpression) argument).getExpressions();
                if (!arguments.isEmpty()) {
                    ClassNode cn = arguments.get(0).getNodeMetaData(ExtensionMethodDeclaringClass.class);
                    if (cn != null)
                        declaring = cn;
                }
            }
            result = extractPlaceHolders(receiver, declaring);
            if (!result.isEmpty()) Optional.ofNullable(method.getGenericsTypes()).ifPresent(methodGenerics ->
                Arrays.stream(methodGenerics).map(gt -> new GenericsTypeName(gt.getName())).forEach(result::remove)); // GROOVY-10322
        }
        return result;
    }

    protected boolean typeCheckMethodsWithGenericsOrFail(final ClassNode receiver, final ClassNode[] arguments, final MethodNode candidateMethod, final Expression location) {
        if (!typeCheckMethodsWithGenerics(receiver, arguments, candidateMethod)) {
            ClassNode r = receiver, at[] = arguments; MethodNode m = candidateMethod;
            if (candidateMethod instanceof ExtensionMethodNode) {
                m = ((ExtensionMethodNode) candidateMethod).getExtensionMethodNode();
                r = m.getDeclaringClass();
                at = new ClassNode[arguments.length + 1];
                at[0] = receiver; // object expression is first argument
                System.arraycopy(arguments, 0, at, 1, arguments.length);
            }
            Map<GenericsTypeName, GenericsType> spec = extractPlaceHoldersVisibleToDeclaration(r, m, null);
            GenericsType[] gt = applyGenericsContext(spec, m.getGenericsTypes()); // class params in bounds
            GenericsUtils.extractPlaceholders(makeClassSafe0(OBJECT_TYPE, gt), spec);

            Parameter[] parameters = m.getParameters();
            ClassNode[] paramTypes = new ClassNode[parameters.length];
            for (int i = 0, n = parameters.length; i < n; i += 1) {
                paramTypes[i] = fullyResolveType(parameters[i].getType(), spec);
                // GROOVY-10010: check for List<String> parameter and ["foo","$bar"] argument
                if (i < at.length && hasGStringStringError(paramTypes[i], at[i], location)) {
                    return false;
                }
            }

            addStaticTypeError("Cannot call " + (gt == null ? "" : GenericsUtils.toGenericTypesString(gt)) +
                    prettyPrintTypeName(r) + "#" + toMethodParametersString(m.getName(), paramTypes) + " with arguments " + formatArgumentList(at), location);
            return false;
        }
        return true;
    }

    protected static String formatArgumentList(final ClassNode[] nodes) {
        if (nodes == null || nodes.length == 0) return "[]";

        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (ClassNode node : nodes) {
            joiner.add(prettyPrintType(node));
        }
        return joiner.toString();
    }

    private static void putSetterInfo(final Expression exp, final SetterInfo info) {
        exp.putNodeMetaData(SetterInfo.class, info);
    }

    private static SetterInfo removeSetterInfo(final Expression exp) {
        Object nodeMetaData = exp.getNodeMetaData(SetterInfo.class);
        if (nodeMetaData != null) {
            exp.removeNodeMetaData(SetterInfo.class);
            return (SetterInfo) nodeMetaData;
        }
        return null;
    }

    @Override
    public void addError(final String msg, final ASTNode expr) {
        Long err = ((long) expr.getLineNumber()) << 16 + expr.getColumnNumber();
        if ((DEBUG_GENERATED_CODE && expr.getLineNumber() < 0) || !typeCheckingContext.reportedErrors.contains(err)) {
            typeCheckingContext.getErrorCollector().addErrorAndContinue(msg + '\n', expr, getSourceUnit());
            typeCheckingContext.reportedErrors.add(err);
        }
    }

    protected void addStaticTypeError(final String msg, final ASTNode expr) {
        if (expr.getColumnNumber() > 0 && expr.getLineNumber() > 0) {
            addError(StaticTypesTransformation.STATIC_ERROR_PREFIX + msg, expr);
        } else {
            if (DEBUG_GENERATED_CODE) {
                addError(StaticTypesTransformation.STATIC_ERROR_PREFIX + "Error in generated code [" + expr.getText() + "] - " + msg, expr);
            }
            // ignore errors which are related to unknown source locations
            // because they are likely related to generated code
        }
    }

    protected void addNoMatchingMethodError(ClassNode receiver, final String name, final ClassNode[] args, final Expression call) {
        if (isClassClassNodeWrappingConcreteType(receiver)) {
            receiver = receiver.getGenericsTypes()[0].getType();
        }
        addStaticTypeError("Cannot find matching method " + prettyPrintTypeName(receiver) + "#" + toMethodParametersString(name, args) + ". Please check if the declared type is correct and if the method exists.", call);
    }

    protected void addAmbiguousErrorMessage(final List<MethodNode> foundMethods, final String name, final ClassNode[] args, final Expression expr) {
        addStaticTypeError("Reference to method is ambiguous. Cannot choose between " + prettyPrintMethodList(foundMethods), expr);
    }

    protected void addCategoryMethodCallError(final Expression call) {
        addStaticTypeError("Due to their dynamic nature, usage of categories is not possible with static type checking active", call);
    }

    protected void addAssignmentError(final ClassNode leftType, final ClassNode rightType, final Expression expression) {
        addStaticTypeError("Cannot assign value of type " + prettyPrintType(rightType) + " to variable of type " + prettyPrintType(leftType), expression);
    }

    protected void addUnsupportedPreOrPostfixExpressionError(final Expression expression) {
        if (expression instanceof PostfixExpression) {
            addStaticTypeError("Unsupported postfix operation type [" + ((PostfixExpression) expression).getOperation() + "]", expression);
        } else if (expression instanceof PrefixExpression) {
            addStaticTypeError("Unsupported prefix operation type [" + ((PrefixExpression) expression).getOperation() + "]", expression);
        } else {
            throw new IllegalArgumentException("Method should be called with a PostfixExpression or a PrefixExpression");
        }
    }

    public void setMethodsToBeVisited(final Set<MethodNode> methodsToBeVisited) {
        this.typeCheckingContext.methodsToBeVisited = methodsToBeVisited;
    }

    public void performSecondPass() {
        for (SecondPassExpression<?> wrapper : typeCheckingContext.secondPassExpressions) {
            Expression expression = wrapper.getExpression();
            if (expression instanceof BinaryExpression) {
                Expression left = ((BinaryExpression) expression).getLeftExpression();
                if (left instanceof VariableExpression) {
                    Variable target = findTargetVariable((VariableExpression) left);
                    if (target instanceof VariableExpression) {
                        VariableExpression var = (VariableExpression) target;
                        List<ClassNode> classNodes = typeCheckingContext.closureSharedVariablesAssignmentTypes.get(var);
                        if (classNodes != null && classNodes.size() > 1) {
                            ClassNode lub = lowestUpperBound(classNodes);
                            String message = getOperationName(((BinaryExpression) expression).getOperation().getType());
                            if (message != null) {
                                List<MethodNode> method = findMethod(lub, message, getType(((BinaryExpression) expression).getRightExpression()));
                                if (method.isEmpty()) {
                                    addStaticTypeError("A closure shared variable [" + target.getName() + "] has been assigned with various types and the method" +
                                            " [" + toMethodParametersString(message, getType(((BinaryExpression) expression).getRightExpression())) + "]" +
                                            " does not exist in the lowest upper bound of those types: [" +
                                            prettyPrintType(lub) + "]. In general, this is a bad practice (variable reuse) because the compiler cannot" +
                                            " determine safely what is the type of the variable at the moment of the call in a multithreaded context.", expression);
                                }
                            }
                        }
                    }
                }
            } else if (expression instanceof MethodCallExpression) {
                MethodCallExpression call = (MethodCallExpression) expression;
                Expression objectExpression = call.getObjectExpression();
                if (objectExpression instanceof VariableExpression) {
                    // this should always be the case, but adding a test is safer
                    Variable target = findTargetVariable((VariableExpression) objectExpression);
                    if (target instanceof VariableExpression) {
                        VariableExpression var = (VariableExpression) target;
                        List<ClassNode> classNodes = typeCheckingContext.closureSharedVariablesAssignmentTypes.get(var);
                        if (classNodes != null && classNodes.size() > 1) {
                            ClassNode lub = lowestUpperBound(classNodes);
                            MethodNode methodNode = call.getNodeMetaData(DIRECT_METHOD_CALL_TARGET);
                            // we must check that such a method exists on the LUB
                            Parameter[] parameters = methodNode.getParameters();
                            ClassNode[] params = extractTypesFromParameters(parameters);
                            ClassNode[] argTypes = (ClassNode[]) wrapper.getData();
                            List<MethodNode> method = findMethod(lub, methodNode.getName(), argTypes);
                            if (method.size() != 1) {
                                addStaticTypeError("A closure shared variable [" + target.getName() + "] has been assigned with various types and the method" +
                                        " [" + toMethodParametersString(methodNode.getName(), params) + "]" +
                                        " does not exist in the lowest upper bound of those types: [" +
                                        prettyPrintType(lub) + "]. In general, this is a bad practice (variable reuse) because the compiler cannot" +
                                        " determine safely what is the type of the variable at the moment of the call in a multithreaded context.", call);
                            }
                        }
                    }
                }
            }
        }
        // give a chance to type checker extensions to throw errors based on information gathered afterwards
        extension.finish();
    }

    protected static ClassNode[] extractTypesFromParameters(final Parameter[] parameters) {
        return Arrays.stream(parameters).map(Parameter::getType).toArray(ClassNode[]::new);
    }

    /**
     * Returns a wrapped type if, and only if, the provided class node is a primitive type.
     * This method differs from {@link ClassHelper#getWrapper(org.codehaus.groovy.ast.ClassNode)} as it will
     * return the same instance if the provided type is not a generic type.
     *
     * @return the wrapped type
     */
    protected static ClassNode wrapTypeIfNecessary(final ClassNode type) {
        return (type != null && isPrimitiveType(type) ? getWrapper(type) : type);
    }

    protected static boolean isClassInnerClassOrEqualTo(final ClassNode toBeChecked, final ClassNode start) {
        if (start == toBeChecked) return true;
        ClassNode outer = start.getOuterClass();
        if (outer != null) {
            return isClassInnerClassOrEqualTo(toBeChecked, outer);
        }
        return false;
    }

    private static boolean isNonStaticHelperMethod(final MethodNode method) {
        Parameter[] parameters = method.getParameters(); // check first param is "$self"
        if (parameters.length > 0 && parameters[0].getName().equals(Traits.THIS_OBJECT)) {
            return !method.getName().contains("$init$") && Traits.isTrait(method.getDeclaringClass().getOuterClass());
        }
        return false;
    }

    private static BinaryExpression assignX(final Expression lhs, final Expression rhs, final ASTNode pos) {
        BinaryExpression exp = (BinaryExpression) GeneralUtils.assignX(lhs, rhs);
        exp.setSourcePosition(pos);
        return exp;
    }

    //--------------------------------------------------------------------------

    public static class SignatureCodecFactory {
        public static SignatureCodec getCodec(final int version, final ClassLoader classLoader) {
            switch (version) {
              case 1:
                return new SignatureCodecVersion1(classLoader);
              default:
                return null;
            }
        }
    }

    // class only used to store setter information when an expression of type
    // a.x = foo or x=foo is found and that it corresponds to a setter call
    private static class SetterInfo {
        final ClassNode receiverType;
        final String name;
        final List<MethodNode> setters;

        private SetterInfo(final ClassNode receiverType, final String name, final List<MethodNode> setters) {
            this.receiverType = receiverType;
            this.setters = setters;
            this.name = name;
        }
    }

    /**
     * Wrapper for a Parameter so it can be treated like a VariableExpression
     * and tracked in the {@code ifElseForWhileAssignmentTracker}.
     */
    private class ParameterVariableExpression extends VariableExpression {

        private final Parameter parameter;

        ParameterVariableExpression(final Parameter parameter) {
            super(parameter);
            this.parameter = parameter;

            ClassNode inferredType = getNodeMetaData(INFERRED_TYPE);
            if (inferredType == null) {
                inferredType = typeCheckingContext.controlStructureVariables.get(parameter); // for/catch/closure
                if (inferredType == null) {
                    inferredType = getTypeFromClosureArguments(parameter); // @ClosureParams or SAM-type coercion
                }
                setNodeMetaData(INFERRED_TYPE, inferredType != null ? inferredType : parameter.getType()); // GROOVY-10651
            }
        }

        @Override
        public Map<?, ?> getMetaDataMap() {
            return parameter.getMetaDataMap();
        }

        @Override
        public void setMetaDataMap(final Map<?, ?> metaDataMap) {
            parameter.setMetaDataMap(metaDataMap);
        }
    }

    protected class VariableExpressionTypeMemoizer extends ClassCodeVisitorSupport {
        private final boolean onlySharedVariables;
        private final Map<VariableExpression, ClassNode> varOrigType;

        public VariableExpressionTypeMemoizer(final Map<VariableExpression, ClassNode> varOrigType) {
            this(varOrigType, false);
        }

        public VariableExpressionTypeMemoizer(final Map<VariableExpression, ClassNode> varOrigType, final boolean onlySharedVariables) {
            this.varOrigType = varOrigType;
            this.onlySharedVariables = onlySharedVariables;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return StaticTypeCheckingVisitor.this.getSourceUnit();
        }

        @Override
        public void visitVariableExpression(final VariableExpression expression) {
            Variable var = findTargetVariable(expression);
            if ((!onlySharedVariables || var.isClosureSharedVariable()) && var instanceof VariableExpression) {
                VariableExpression ve = (VariableExpression) var;
                ClassNode cn = ve.getNodeMetaData(INFERRED_TYPE);
                if (cn == null) cn = ve.getOriginType();
                varOrigType.put(ve, cn);
            }
            super.visitVariableExpression(expression);
        }
    }
}
