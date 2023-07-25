/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.math;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.DoubleBlock;
import org.elasticsearch.xpack.esql.expression.function.scalar.AbstractScalarFunctionTestCase;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;
import org.hamcrest.Matcher;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;

public class TauTests extends AbstractScalarFunctionTestCase {
    @Override
    protected TestCase getSimpleTestCase() {
        return new TestCase(Source.EMPTY, List.of(new TypedData(1, DataTypes.INTEGER, "foo")), equalTo(Tau.TAU));
    }

    @Override
    protected Matcher<Object> resultMatcher(List<Object> data, DataType dataType) {
        return equalTo(Tau.TAU);
    }

    @Override
    protected String expectedEvaluatorSimpleToString() {
        return "LiteralsEvaluator[block=6.283185307179586]";
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new Tau(Source.EMPTY);
    }

    @Override
    protected List<ArgumentSpec> argSpec() {
        return List.of();
    }

    @Override
    protected DataType expectedType(List<DataType> argTypes) {
        return DataTypes.DOUBLE;
    }

    @Override
    protected void assertSimpleWithNulls(List<Object> data, Block value, int nullBlock) {
        assertThat(((DoubleBlock) value).asVector().getDouble(0), equalTo(Tau.TAU));
    }
}
