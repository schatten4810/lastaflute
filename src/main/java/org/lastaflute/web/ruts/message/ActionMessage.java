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
package org.lastaflute.web.ruts.message;

import java.io.Serializable;

import org.dbflute.util.Srl;

/**
 * @author modified by jflute (originated in Struts)
 */
public class ActionMessage implements Serializable {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final long serialVersionUID = 1L;
    protected static final String BEGIN_MARK = "{";
    protected static final String END_MARK = "}";
    protected static final Object[] EMPTY_VALUES = new Object[0];

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String key;
    protected final Object[] values;
    protected final boolean resource;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public ActionMessage(String key, Object... values) {
        assertArgumentNotNull("key", key);
        assertArgumentNotNull("values", values);
        this.key = filterKey(key);
        this.values = filterValues(values);
        this.resource = true;
    }

    public ActionMessage(String key, boolean resource) { // basically for direct message
        assertArgumentNotNull("key", key);
        this.key = resource ? filterKey(key) : key;
        this.values = null;
        this.resource = resource;
    }

    protected String filterKey(String key) {
        return unquoteBracesIfQuoted(key);
    }

    protected Object[] filterValues(Object[] values) {
        final Object[] filtered = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            final Object val = values[i];
            filtered[i] = val instanceof String ? unquoteBracesIfQuoted((String) val) : val;
        }
        return filtered;
    }

    protected String unquoteBracesIfQuoted(String key) {
        final String beginMark = BEGIN_MARK;
        final String endMark = END_MARK;
        if (Srl.isQuotedAnything(key, beginMark, endMark)) {
            return Srl.unquoteAnything(key, beginMark, endMark); // remove Hibernate Validator's braces
        } else {
            return key;
        }
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    protected void assertArgumentNotNull(String variableName, Object value) {
        if (variableName == null) {
            throw new IllegalArgumentException("The variableName should not be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("The argument '" + variableName + "' should not be null.");
        }
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(key);
        sb.append("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                sb.append(values[i]);
                if (i < values.length - 1) { // don't append comma to last entry
                    sb.append(", ");
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public String getKey() {
        return key;
    }

    public Object[] getValues() {
        return values;
    }

    public boolean isResource() {
        return resource;
    }
}
