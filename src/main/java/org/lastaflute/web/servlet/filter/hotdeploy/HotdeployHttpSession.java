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
package org.lastaflute.web.servlet.filter.hotdeploy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import org.lastaflute.di.core.smart.hot.HotdeployUtil;
import org.lastaflute.di.exception.IORuntimeException;
import org.lastaflute.di.exception.SessionObjectNotSerializableRuntimeException;
import org.lastaflute.di.helper.log.SLogger;

/**
 * @author modified by jflute (originated in Seasar)
 */
@SuppressWarnings("deprecation")
public class HotdeployHttpSession implements HttpSession {

    private static final SLogger logger = SLogger.getLogger(HotdeployHttpSession.class);

    protected final HotdeployHttpServletRequest request;
    protected final HttpSession originalSession;
    protected final Map<String, Object> attributes = new HashMap<String, Object>();
    protected boolean active = true;

    public HotdeployHttpSession(final HttpSession originalSession) {
        this(null, originalSession);
    }

    public HotdeployHttpSession(final HotdeployHttpServletRequest request, final HttpSession originalSession) {
        this.request = request;
        this.originalSession = originalSession;
    }

    public void flush() {
        if (active) {
            for (Iterator<Entry<String, Object>> it = attributes.entrySet().iterator(); it.hasNext();) {
                final Entry<String, Object> entry = (Entry<String, Object>) it.next();
                try {
                    originalSession.setAttribute((String) entry.getKey(), new SerializedObjectHolder(entry.getValue()));
                } catch (final IllegalStateException e) {
                    return;
                } catch (final Exception e) {
                    logger.log("ESSR0017", new Object[] { e }, e);
                }
            }
        }
    }

    public Object getAttribute(final String name) {
        assertActive();
        if (attributes.containsKey(name)) {
            return attributes.get(name);
        }
        Object value = originalSession.getAttribute(name);
        if (value instanceof SerializedObjectHolder) {
            value = ((SerializedObjectHolder) value).getDeserializedObject(name);
            if (value != null) {
                attributes.put(name, value);
            } else {
                originalSession.removeAttribute(name);
            }
        }
        return value;
    }

    public void setAttribute(final String name, final Object value) {
        assertActive();
        if (value == null) {
            originalSession.setAttribute(name, value);
            return;
        }
        if (!(value instanceof Serializable)) {
            throw new SessionObjectNotSerializableRuntimeException(value.getClass());
        }
        attributes.put(name, value);
        originalSession.setAttribute(name, value);
    }

    public void removeAttribute(final String name) {
        attributes.remove(name);
        originalSession.removeAttribute(name);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Enumeration getAttributeNames() {
        return originalSession.getAttributeNames();
    }

    public long getCreationTime() {
        return originalSession.getCreationTime();
    }

    public String getId() {
        return originalSession.getId();
    }

    public long getLastAccessedTime() {
        return originalSession.getLastAccessedTime();
    }

    public int getMaxInactiveInterval() {
        return originalSession.getMaxInactiveInterval();
    }

    public ServletContext getServletContext() {
        return originalSession.getServletContext();
    }

    public HttpSessionContext getSessionContext() {
        return originalSession.getSessionContext();
    }

    public Object getValue(final String name) {
        return getAttribute(name);
    }

    public String[] getValueNames() {
        return originalSession.getValueNames();
    }

    public void invalidate() {
        originalSession.invalidate();
        if (request != null) {
            request.invalidateSession();
        }
        active = false;
    }

    public boolean isNew() {
        return originalSession.isNew();
    }

    public void putValue(final String name, final Object value) {
        setAttribute(name, value);
    }

    public void removeValue(final String name) {
        removeAttribute(name);
    }

    public void setMaxInactiveInterval(final int interval) {
        originalSession.setMaxInactiveInterval(interval);
    }

    public static class SerializedObjectHolder implements Serializable {

        private static final long serialVersionUID = 1L;

        protected byte[] bytes;

        public SerializedObjectHolder(final Object sessionObject) {
            try {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(sessionObject);
                oos.close();
                bytes = baos.toByteArray();
            } catch (final NotSerializableException e) {
                throw new SessionObjectNotSerializableRuntimeException(e);
            } catch (final IOException e) {
                throw new IORuntimeException(e);
            }
        }

        public Object getDeserializedObject(final String name) {
            try {
                return HotdeployUtil.deserializeInternal(bytes);
            } catch (final Exception e) {
                logger.log("ISSR0008", new Object[] { name }, e);
                return null;
            }
        }
    }

    protected void assertActive() {
        if (!active) {
            throw new IllegalStateException("session invalidated");
        }
    }
}