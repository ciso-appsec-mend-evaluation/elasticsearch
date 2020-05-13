/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.eql.action;

import org.elasticsearch.Version;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.searchafter.SearchAfterBuilder;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.xpack.eql.action.RequestDefaults.FETCH_SIZE;
import static org.elasticsearch.xpack.eql.action.RequestDefaults.FIELD_EVENT_CATEGORY;
import static org.elasticsearch.xpack.eql.action.RequestDefaults.FIELD_IMPLICIT_JOIN_KEY;
import static org.elasticsearch.xpack.eql.action.RequestDefaults.FIELD_TIMESTAMP;

public class EqlSearchRequest extends ActionRequest implements IndicesRequest.Replaceable, ToXContent {

    public static long MIN_KEEP_ALIVE = TimeValue.timeValueMinutes(1).millis();
    public static TimeValue DEFAULT_KEEP_ALIVE = TimeValue.timeValueDays(5);

    private String[] indices;
    private IndicesOptions indicesOptions = IndicesOptions.fromOptions(false,
        false, true, false);

    private QueryBuilder filter = null;
    private String timestampField = FIELD_TIMESTAMP;
    private String eventCategoryField = FIELD_EVENT_CATEGORY;
    private String implicitJoinKeyField = FIELD_IMPLICIT_JOIN_KEY;
    private int fetchSize = FETCH_SIZE;
    private SearchAfterBuilder searchAfterBuilder;
    private String query;
    private boolean isCaseSensitive = false;

    // Async settings
    private TimeValue waitForCompletionTimeout = null;
    private TimeValue keepAlive = DEFAULT_KEEP_ALIVE;
    private boolean keepOnCompletion;

    static final String KEY_FILTER = "filter";
    static final String KEY_TIMESTAMP_FIELD = "timestamp_field";
    static final String KEY_EVENT_CATEGORY_FIELD = "event_category_field";
    static final String KEY_IMPLICIT_JOIN_KEY_FIELD = "implicit_join_key_field";
    static final String KEY_SIZE = "size";
    static final String KEY_SEARCH_AFTER = "search_after";
    static final String KEY_QUERY = "query";
    static final String KEY_WAIT_FOR_COMPLETION_TIMEOUT = "wait_for_completion_timeout";
    static final String KEY_KEEP_ALIVE = "keep_alive";
    static final String KEY_KEEP_ON_COMPLETION = "keep_on_completion";
    static final String KEY_CASE_SENSITIVE = "case_sensitive";

    static final ParseField FILTER = new ParseField(KEY_FILTER);
    static final ParseField TIMESTAMP_FIELD = new ParseField(KEY_TIMESTAMP_FIELD);
    static final ParseField EVENT_CATEGORY_FIELD = new ParseField(KEY_EVENT_CATEGORY_FIELD);
    static final ParseField IMPLICIT_JOIN_KEY_FIELD = new ParseField(KEY_IMPLICIT_JOIN_KEY_FIELD);
    static final ParseField SIZE = new ParseField(KEY_SIZE);
    static final ParseField SEARCH_AFTER = new ParseField(KEY_SEARCH_AFTER);
    static final ParseField QUERY = new ParseField(KEY_QUERY);
    static final ParseField WAIT_FOR_COMPLETION_TIMEOUT = new ParseField(KEY_WAIT_FOR_COMPLETION_TIMEOUT);
    static final ParseField KEEP_ALIVE = new ParseField(KEY_KEEP_ALIVE);
    static final ParseField KEEP_ON_COMPLETION = new ParseField(KEY_KEEP_ON_COMPLETION);
    static final ParseField CASE_SENSITIVE = new ParseField(KEY_CASE_SENSITIVE);

    private static final ObjectParser<EqlSearchRequest, Void> PARSER = objectParser(EqlSearchRequest::new);

    public EqlSearchRequest() {
        super();
    }

    public EqlSearchRequest(StreamInput in) throws IOException {
        super(in);
        indices = in.readStringArray();
        indicesOptions = IndicesOptions.readIndicesOptions(in);
        filter = in.readOptionalNamedWriteable(QueryBuilder.class);
        timestampField = in.readString();
        eventCategoryField = in.readString();
        implicitJoinKeyField = in.readString();
        fetchSize = in.readVInt();
        searchAfterBuilder = in.readOptionalWriteable(SearchAfterBuilder::new);
        query = in.readString();
        if (in.getVersion().onOrAfter(Version.V_8_0_0)) { // TODO: Remove after backport
            this.waitForCompletionTimeout = in.readOptionalTimeValue();
            this.keepAlive = in.readOptionalTimeValue();
            this.keepOnCompletion = in.readBoolean();
        }
        isCaseSensitive = in.readBoolean();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;

        if (indices == null) {
            validationException = addValidationError("indices is null", validationException);
        } else {
            for (String index : indices) {
                if (index == null) {
                    validationException = addValidationError("index is null", validationException);
                    break;
                }
            }
        }

        if (indicesOptions == null) {
            validationException = addValidationError("indicesOptions is null", validationException);
        }

        if (query == null || query.isEmpty()) {
            validationException = addValidationError("query is null or empty", validationException);
        }

        if (timestampField == null || timestampField.isEmpty()) {
            validationException = addValidationError("@timestamp field is null or empty", validationException);
        }

        if (eventCategoryField == null || eventCategoryField.isEmpty()) {
            validationException = addValidationError("event category field is null or empty", validationException);
        }

        if (implicitJoinKeyField == null || implicitJoinKeyField.isEmpty()) {
            validationException = addValidationError("implicit join key field is null or empty", validationException);
        }

        if (fetchSize <= 0) {
            validationException = addValidationError("size must be greater than 0", validationException);
        }

        if (keepAlive != null  && keepAlive.getMillis() < MIN_KEEP_ALIVE) {
            validationException =
                addValidationError("[keep_alive] must be greater than 1 minute, got:" + keepAlive.toString(), validationException);
        }

        return validationException;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (filter != null) {
            builder.field(KEY_FILTER, filter);
        }
        builder.field(KEY_TIMESTAMP_FIELD, timestampField());
        builder.field(KEY_EVENT_CATEGORY_FIELD, eventCategoryField());
        if (implicitJoinKeyField != null) {
            builder.field(KEY_IMPLICIT_JOIN_KEY_FIELD, implicitJoinKeyField());
        }
        builder.field(KEY_SIZE, fetchSize());

        if (searchAfterBuilder != null) {
            builder.array(SEARCH_AFTER.getPreferredName(), searchAfterBuilder.getSortValues());
        }

        builder.field(KEY_QUERY, query);
        if (waitForCompletionTimeout != null) {
            builder.field(KEY_WAIT_FOR_COMPLETION_TIMEOUT, waitForCompletionTimeout);
        }
        if (keepAlive != null) {
            builder.field(KEY_KEEP_ALIVE, keepAlive);
        }
        builder.field(KEY_KEEP_ON_COMPLETION, keepOnCompletion);
        builder.field(KEY_CASE_SENSITIVE, isCaseSensitive);

        return builder;
    }

    public static EqlSearchRequest fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    protected static <R extends EqlSearchRequest> ObjectParser<R, Void> objectParser(Supplier<R> supplier) {
        ObjectParser<R, Void> parser = new ObjectParser<>("eql/search", false, supplier);
        parser.declareObject(EqlSearchRequest::filter,
            (p, c) -> AbstractQueryBuilder.parseInnerQueryBuilder(p), FILTER);
        parser.declareString(EqlSearchRequest::timestampField, TIMESTAMP_FIELD);
        parser.declareString(EqlSearchRequest::eventCategoryField, EVENT_CATEGORY_FIELD);
        parser.declareString(EqlSearchRequest::implicitJoinKeyField, IMPLICIT_JOIN_KEY_FIELD);
        parser.declareInt(EqlSearchRequest::fetchSize, SIZE);
        parser.declareField(EqlSearchRequest::setSearchAfter, SearchAfterBuilder::fromXContent, SEARCH_AFTER,
            ObjectParser.ValueType.OBJECT_ARRAY);
        parser.declareString(EqlSearchRequest::query, QUERY);
        parser.declareField(EqlSearchRequest::waitForCompletionTimeout,
            (p, c) -> TimeValue.parseTimeValue(p.text(), KEY_WAIT_FOR_COMPLETION_TIMEOUT), WAIT_FOR_COMPLETION_TIMEOUT,
            ObjectParser.ValueType.VALUE);
        parser.declareField(EqlSearchRequest::keepAlive,
            (p, c) -> TimeValue.parseTimeValue(p.text(), KEY_KEEP_ALIVE), KEEP_ALIVE, ObjectParser.ValueType.VALUE);
        parser.declareBoolean(EqlSearchRequest::keepOnCompletion, KEEP_ON_COMPLETION);
        parser.declareBoolean(EqlSearchRequest::isCaseSensitive, CASE_SENSITIVE);
        return parser;
    }

    @Override
    public EqlSearchRequest indices(String... indices) {
        this.indices = indices;
        return this;
    }

    public QueryBuilder filter() { return this.filter; }

    public EqlSearchRequest filter(QueryBuilder filter) {
        this.filter = filter;
        return this;
    }

    public String timestampField() { return this.timestampField; }

    public EqlSearchRequest timestampField(String timestampField) {
        this.timestampField = timestampField;
        return this;
    }

    public String eventCategoryField() { return this.eventCategoryField; }

    public EqlSearchRequest eventCategoryField(String eventCategoryField) {
        this.eventCategoryField = eventCategoryField;
        return this;
    }

    public String implicitJoinKeyField() { return this.implicitJoinKeyField; }

    public EqlSearchRequest implicitJoinKeyField(String implicitJoinKeyField) {
        this.implicitJoinKeyField = implicitJoinKeyField;
        return this;
    }

    public int fetchSize() { return this.fetchSize; }

    public EqlSearchRequest fetchSize(int size) {
        this.fetchSize = size;
        return this;
    }

    public Object[] searchAfter() {
        if (searchAfterBuilder == null) {
            return null;
        }
        return searchAfterBuilder.getSortValues();
    }

    public EqlSearchRequest searchAfter(Object[] values) {
        this.searchAfterBuilder = new SearchAfterBuilder().setSortValues(values);
        return this;
    }

    private EqlSearchRequest setSearchAfter(SearchAfterBuilder builder) {
        this.searchAfterBuilder = builder;
        return this;
    }

    public String query() { return this.query; }

    public EqlSearchRequest query(String query) {
        this.query = query;
        return this;
    }

    public TimeValue waitForCompletionTimeout() {
        return waitForCompletionTimeout;
    }

    public EqlSearchRequest waitForCompletionTimeout(TimeValue waitForCompletionTimeout) {
        this.waitForCompletionTimeout = waitForCompletionTimeout;
        return this;
    }

    public TimeValue keepAlive() {
        return keepAlive;
    }

    public EqlSearchRequest keepAlive(TimeValue keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public boolean keepOnCompletion() {
        return keepOnCompletion;
    }

    public EqlSearchRequest keepOnCompletion(boolean keepOnCompletion) {
        this.keepOnCompletion = keepOnCompletion;
        return this;
    }

    public boolean isCaseSensitive() { return this.isCaseSensitive; }

    public EqlSearchRequest isCaseSensitive(boolean isCaseSensitive) {
        this.isCaseSensitive = isCaseSensitive;
        return this;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringArrayNullable(indices);
        indicesOptions.writeIndicesOptions(out);
        out.writeOptionalNamedWriteable(filter);
        out.writeString(timestampField);
        out.writeString(eventCategoryField);
        out.writeString(implicitJoinKeyField);
        out.writeVInt(fetchSize);
        out.writeOptionalWriteable(searchAfterBuilder);
        out.writeString(query);
        if (out.getVersion().onOrAfter(Version.V_8_0_0)) { // TODO: Remove after backport
            out.writeOptionalTimeValue(waitForCompletionTimeout);
            out.writeOptionalTimeValue(keepAlive);
            out.writeBoolean(keepOnCompletion);
        }
        out.writeBoolean(isCaseSensitive);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EqlSearchRequest that = (EqlSearchRequest) o;
        return fetchSize == that.fetchSize &&
                Arrays.equals(indices, that.indices) &&
                Objects.equals(indicesOptions, that.indicesOptions) &&
                Objects.equals(filter, that.filter) &&
                Objects.equals(timestampField, that.timestampField) &&
                Objects.equals(eventCategoryField, that.eventCategoryField) &&
                Objects.equals(implicitJoinKeyField, that.implicitJoinKeyField) &&
                Objects.equals(searchAfterBuilder, that.searchAfterBuilder) &&
                Objects.equals(query, that.query) &&
                Objects.equals(waitForCompletionTimeout, that.waitForCompletionTimeout) &&
                Objects.equals(keepAlive, that.keepAlive) &&
                Objects.equals(isCaseSensitive, that.isCaseSensitive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            Arrays.hashCode(indices),
            indicesOptions,
            filter,
            fetchSize,
            timestampField, eventCategoryField,
            implicitJoinKeyField,
            searchAfterBuilder,
            query,
            waitForCompletionTimeout,
            keepAlive,
            isCaseSensitive);
    }

    @Override
    public String[] indices() {
        return indices;
    }

    public EqlSearchRequest indicesOptions(IndicesOptions indicesOptions) {
        this.indicesOptions = indicesOptions;
        return this;
    }

    @Override
    public IndicesOptions indicesOptions() {
        return indicesOptions;
    }

    @Override
    public Task createTask(long id, String type, String action, TaskId parentTaskId, Map<String, String> headers) {
        return new EqlSearchTask(id, type, action, getDescription(), parentTaskId, headers, null, null);
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("indices[");
        Strings.arrayToDelimitedString(indices, ",", sb);
        sb.append("], ");
        sb.append(query);
        return sb.toString();
    }
}
