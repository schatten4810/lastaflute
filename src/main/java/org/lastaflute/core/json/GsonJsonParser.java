/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.lastaflute.core.json;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.dbflute.util.DfCollectionUtil;
import org.dbflute.util.DfReflectionUtil;
import org.lastaflute.core.json.adapter.BooleanGsonAdaptable;
import org.lastaflute.core.json.adapter.DBFluteGsonAdaptable;
import org.lastaflute.core.json.adapter.Java8TimeGsonAdaptable;
import org.lastaflute.core.json.adapter.NumberGsonAdaptable;
import org.lastaflute.core.json.adapter.StringGsonAdaptable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author jflute
 */
public class GsonJsonParser implements RealJsonParser // adapters here
        , StringGsonAdaptable, NumberGsonAdaptable, Java8TimeGsonAdaptable, BooleanGsonAdaptable, DBFluteGsonAdaptable {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final JsonMappingOption option;
    protected final Gson gson;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public GsonJsonParser(Consumer<GsonBuilder> oneArgLambda, Consumer<JsonMappingOption> opLambda) {
        option = createOption(opLambda); // should be before creating Gson
        gson = createGson(oneArgLambda); // using option variable
    }

    protected JsonMappingOption createOption(Consumer<JsonMappingOption> opLambda) {
        final JsonMappingOption option = new JsonMappingOption();
        opLambda.accept(option);
        return option;
    }

    protected Gson createGson(Consumer<GsonBuilder> settings) {
        final GsonBuilder builder = newGsonBuilder();
        setupDefaultSettings(builder);
        setupYourSettings(builder);
        acceptGsonSettings(settings, builder);
        return builder.create();
    }

    protected GsonBuilder newGsonBuilder() {
        return new GsonBuilder();
    }

    // -----------------------------------------------------
    //                                      Default Settings
    //                                      ----------------
    protected void setupDefaultSettings(GsonBuilder builder) {
        registerStringAdapter(builder);
        registerNumberAdapter(builder);
        registerJava8TimeAdapter(builder);
        registerBooleanAdapter(builder);
        registerUtilDateFormat(builder);
        registerDBFluteAdapter(builder);
    }

    protected void registerStringAdapter(GsonBuilder builder) {
        builder.registerTypeAdapterFactory(createStringTypeAdapterFactory());
    }

    protected void registerNumberAdapter(GsonBuilder builder) { // to show property path in exception message
        createNumberFactoryList().forEach(factory -> builder.registerTypeAdapterFactory(factory));
    }

    protected void registerJava8TimeAdapter(GsonBuilder builder) { // until supported by Gson
        builder.registerTypeAdapterFactory(createDateTimeTypeAdapterFactory());
    }

    protected void registerBooleanAdapter(GsonBuilder builder) { // to adjust boolean expression flexibly
        builder.registerTypeAdapterFactory(createBooleanTypeAdapterFactory());
    }

    protected void registerUtilDateFormat(GsonBuilder builder) {
        builder.setDateFormat("yyyy-MM-dd'T'HH:mm:ss"); // same as local date-time
    }

    protected void registerDBFluteAdapter(GsonBuilder builder) {
        builder.registerTypeAdapterFactory(createClassificationTypeAdapterFactory());
    }

    // -----------------------------------------------------
    //                                         Your Settings
    //                                         -------------
    protected void setupYourSettings(GsonBuilder builder) { // you can override
    }

    // -----------------------------------------------------
    //                                         Gson Settings
    //                                         -------------
    protected void acceptGsonSettings(Consumer<GsonBuilder> settings, GsonBuilder builder) {
        settings.accept(builder);
    }

    // ===================================================================================
    //                                                                      JSON Interface
    //                                                                      ==============
    @Override
    public <BEAN> BEAN fromJson(String json, Class<BEAN> beanType) { // are not null, already checked
        final BEAN bean = gson.fromJson(json, beanType); // if empty JSON, new-only instance
        return bean != null ? bean : newEmptyInstance(beanType);
    }

    @Override
    public <BEAN> BEAN fromJsonParameteried(String json, ParameterizedType parameterizedType) {
        final BEAN bean = gson.fromJson(json, parameterizedType); // if empty JSON, new-only instance
        if (bean != null) {
            return bean;
        } else { // e.g. empty string JSON
            final Class<?> rawClass = DfReflectionUtil.getRawClass(parameterizedType.getRawType()); // null allowed?
            if (List.class.equals(rawClass)) {
                @SuppressWarnings("unchecked")
                final BEAN emptyList = (BEAN) DfCollectionUtil.newArrayListSized(2); // empty but mutable for coherence
                return emptyList;
            } else if (Map.class.equals(rawClass)) {
                @SuppressWarnings("unchecked")
                final BEAN emptyList = (BEAN) DfCollectionUtil.newHashMapSized(2); // empty but mutable for coherence
                return emptyList;
            } else {
                return newEmptyInstance(parameterizedType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected <BEAN> BEAN newEmptyInstance(ParameterizedType parameterizedType) {
        final Class<?> rawClass = DfReflectionUtil.getRawClass(parameterizedType);
        if (rawClass == null) {
            throw new IllegalStateException("Cannot get raw type from the parameterized type: " + parameterizedType);
        }
        return (BEAN) newEmptyInstance(rawClass);
    }

    @SuppressWarnings("unchecked")
    protected <BEAN> BEAN newEmptyInstance(Class<BEAN> beanType) {
        return (BEAN) DfReflectionUtil.newInstance(beanType);
    }

    @Override
    public String toJson(Object bean) { // is not null, already checked
        return gson.toJson(bean);
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    @Override
    public JsonMappingOption getGsonOption() {
        return option;
    }
}
