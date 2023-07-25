/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.date;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.xpack.esql.expression.function.scalar.AbstractScalarFunctionTestCase;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;
import org.hamcrest.Matcher;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;

public class DateParseTests extends AbstractScalarFunctionTestCase {
    @Override
    protected TestCase getSimpleTestCase() {
        List<TypedData> typedData = List.of(
            new TypedData(new BytesRef("2023-05-05"), DataTypes.KEYWORD, "first"),
            new TypedData(new BytesRef("yyyy-MM-dd"), DataTypes.KEYWORD, "second")
        );
        return new TestCase(Source.EMPTY, typedData, equalTo(1683244800000L));
    }

    @Override
    protected Matcher<Object> resultMatcher(List<Object> data, DataType dataType) {
        return equalTo(1683244800000L);
    }

    @Override
    protected String expectedEvaluatorSimpleToString() {
        return "DateParseEvaluator[val=Attribute[channel=0], formatter=Attribute[channel=1], zoneId=Z]";
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new DateParse(source, args.get(0), args.size() > 1 ? args.get(1) : null);
    }

    @Override
    protected List<ArgumentSpec> argSpec() {
        return List.of(required(strings()), optional(strings()));
    }

    @Override
    protected DataType expectedType(List<DataType> argTypes) {
        return DataTypes.DATETIME;
    }
}
