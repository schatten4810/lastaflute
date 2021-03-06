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
package org.lastaflute.web.servlet.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author jflute
 */
public class ResponseDownloadResource {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String fileName;
    protected String contentType;
    protected final Map<String, String[]> headerMap = new LinkedHashMap<String, String[]>(4);
    protected byte[] byteData;
    protected WritternStreamCall streamCall;
    protected Integer contentLength;
    protected boolean returnAsEmptyBody;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public ResponseDownloadResource(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("The argument 'fileName' should not be null.");
        }
        this.fileName = fileName;
    }

    // ===================================================================================
    //                                                                        Content Type
    //                                                                        ============
    public ResponseDownloadResource contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public ResponseDownloadResource contentTypeOctetStream() { // used as default
        contentType = "application/octet-stream";
        return this;
    }

    public ResponseDownloadResource contentTypeJpeg() {
        contentType = "image/jpeg";
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    // ===================================================================================
    //                                                                              Header
    //                                                                              ======
    public void header(String name, String[] values) {
        assertArgumentNotNull("name", name);
        assertArgumentNotNull("values", values);
        headerMap.put(name, values);
    }

    public void headerContentDispositionAttachment() { // used as default
        headerMap.put("Content-disposition", new String[] { "attachment; filename=\"" + fileName + "\"" });
    }

    public Map<String, String[]> getHeaderMap() {
        return headerMap;
    }

    // ===================================================================================
    //                                                                       Download Data
    //                                                                       =============
    public ResponseDownloadResource data(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("The argument 'data' should not be null.");
        }
        this.byteData = data;
        return this;
    }

    public ResponseDownloadResource stream(WritternStreamCall streamCall) {
        doStream(streamCall);
        return this;
    }

    public ResponseDownloadResource stream(WritternStreamCall streamCall, int contentLength) {
        doStream(streamCall);
        this.contentLength = contentLength;
        return this;
    }

    protected void doStream(WritternStreamCall streamCall) {
        if (streamCall == null) {
            throw new IllegalArgumentException("The argument 'streamCall' should not be null.");
        }
        if (byteData != null) {
            throw new IllegalStateException("The byte data already exists.");
        }
        this.streamCall = streamCall;
    }

    public byte[] getByteData() {
        return byteData;
    }

    public WritternStreamCall getStreamCall() {
        return streamCall;
    }

    public Integer getContentLength() {
        return contentLength;
    }

    // ===================================================================================
    //                                                                          Empty Body
    //                                                                          ==========
    public void asEmptyBody() {
        returnAsEmptyBody = true;
    }

    public boolean isReturnAsEmptyBody() {
        return returnAsEmptyBody;
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    protected void assertArgumentNotNull(String title, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("The argument '" + title + "' should not be null.");
        }
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        return "{" + fileName + ", " + contentType + ", " + headerMap + "}";
    }
}
