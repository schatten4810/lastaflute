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

/**
 * The provider of JSON resource.
 * @author jflute
 */
public interface JsonResourceProvider {

    /**
     * Provide the parser of JSON which is actually used for parsing.
     * @return The instance for real parser of JSON. (NullAllowed: if null, use default)
     */
    RealJsonParser provideJsonParser();

    default boolean isSuppressNulls() {
        return false;
    }
}