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
package org.lastaflute.web.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfCollectionUtil;
import org.dbflute.util.DfTypeUtil;
import org.lastaflute.web.aspect.RomanticActionCustomizer;

/**
 * @param <BEAN> The type of JSON bean.
 * @author jflute
 */
public class JsonResponse<BEAN> implements ApiResponse {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    protected static final Object DUMMY = new Object();
    protected static final JsonResponse<?> INSTANCE_OF_EMPTY = new JsonResponse<Object>(DUMMY).asEmpty();
    protected static final JsonResponse<?> INSTANCE_OF_SKIP = new JsonResponse<Object>(DUMMY).asSkip();

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final BEAN jsonBean;
    protected String callback;
    protected Map<String, String> headerMap; // lazy loaded (for when no use)
    protected Integer httpStatus;

    protected boolean forcedlyJavaScript;
    protected boolean empty;
    protected boolean skip;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    /**
     * Construct JSON response. <br>
     * This needs {@link RomanticActionCustomizer} in your customizer.dicon.
     * <pre>
     * <span style="color: #3F7E5E">// e.g. normal JSON response</span>
     * return new JsonResponse(bean);
     * 
     * <span style="color: #3F7E5E">// e.g. JSONP response</span>
     * return new JsonResponse(bean).asJsonp("callback");
     * </pre>
     * @param jsonObj The JSON object to send response. (NotNull)
     */
    public JsonResponse(BEAN jsonObj) {
        if (jsonObj == null) {
            throw new IllegalArgumentException("The argument 'jsonObj' should not be null.");
        }
        this.jsonBean = jsonObj;
    }

    // ===================================================================================
    //                                                                              Header
    //                                                                              ======
    @Override
    public JsonResponse<BEAN> header(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException("The argument 'name' should not be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("The argument 'value' should not be null.");
        }
        prepareHeaderMap().put(name, value);
        return this;
    }

    @Override
    public Map<String, String> getHeaderMap() {
        return headerMap != null ? Collections.unmodifiableMap(headerMap) : DfCollectionUtil.emptyMap();
    }

    protected Map<String, String> prepareHeaderMap() {
        if (headerMap == null) {
            headerMap = new LinkedHashMap<String, String>(4);
        }
        return headerMap;
    }

    // ===================================================================================
    //                                                                         HTTP Status
    //                                                                         ===========
    @Override
    public JsonResponse<BEAN> httpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return this;
    }

    @Override
    public Integer getHttpStatus() {
        return httpStatus;
    }

    // ===================================================================================
    //                                                                              Option
    //                                                                              ======
    public JsonResponse<BEAN> asJsonp(String callback) {
        this.callback = callback;
        return this;
    }

    public JsonResponse<BEAN> forcedlyJavaScript() {
        forcedlyJavaScript = true;
        return this;
    }

    @SuppressWarnings("unchecked")
    public static <OBJ> JsonResponse<OBJ> empty() { // user interface
        return (JsonResponse<OBJ>) INSTANCE_OF_EMPTY;
    }

    protected JsonResponse<BEAN> asEmpty() { // internal use
        empty = true;
        return this;
    }

    @SuppressWarnings("unchecked")
    public static <OBJ> JsonResponse<OBJ> skip() { // user interface
        return (JsonResponse<OBJ>) INSTANCE_OF_SKIP;
    }

    protected JsonResponse<BEAN> asSkip() { // internal use
        skip = true;
        return this;
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final String classTitle = DfTypeUtil.toClassTitle(this);
        final String jsonExp = jsonBean != null ? DfTypeUtil.toClassTitle(jsonBean) : null;
        final String callbackExp = callback != null ? ", callback=" + callback : "";
        final String forcedlyJSExp = forcedlyJavaScript ? ", JavaScript" : "";
        final String emptyExp = empty ? ", empty" : "";
        final String skipExp = skip ? ", skip" : "";
        return classTitle + ":{" + jsonExp + callbackExp + forcedlyJSExp + emptyExp + skipExp + "}";
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public BEAN getJsonBean() {
        return jsonBean;
    }

    public OptionalThing<String> getCallback() {
        final Class<? extends Object> beanType = jsonBean.getClass();
        return OptionalThing.ofNullable(callback, () -> {
            throw new IllegalStateException("Not found the callback in the JSON response: " + beanType);
        });
    }

    public boolean isForcedlyJavaScript() {
        return forcedlyJavaScript;
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    @Override
    public boolean isSkip() {
        return skip;
    }
}
