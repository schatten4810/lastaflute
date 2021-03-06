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
package org.lastaflute.web.validation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;

import javax.validation.Configuration;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.bootstrap.GenericBootstrap;
import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.Srl;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.hibernate.validator.spi.resourceloading.ResourceBundleLocator;
import org.lastaflute.core.magic.ThreadCacheContext;
import org.lastaflute.core.message.MessageManager;
import org.lastaflute.di.helper.beans.BeanDesc;
import org.lastaflute.di.helper.beans.PropertyDesc;
import org.lastaflute.di.helper.beans.factory.BeanDescFactory;
import org.lastaflute.web.api.ApiFailureResource;
import org.lastaflute.web.callback.ActionRuntime;
import org.lastaflute.web.response.ApiResponse;
import org.lastaflute.web.ruts.message.ActionMessage;
import org.lastaflute.web.ruts.message.ActionMessages;
import org.lastaflute.web.ruts.message.MessagesCreator;
import org.lastaflute.web.servlet.request.RequestManager;
import org.lastaflute.web.util.LaActionRuntimeUtil;
import org.lastaflute.web.validation.exception.ClientErrorByValidatorException;
import org.lastaflute.web.validation.exception.ValidationErrorException;

/**
 * @param <MESSAGES> The type of action messages.
 * @author jflute
 */
public class ActionValidator<MESSAGES extends ActionMessages> {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    public static final String JAVAX_CONSTRAINTS_PKG = NotNull.class.getPackage().getName();
    public static final String HIBERNATE_CONSTRAINTS_PKG = NotEmpty.class.getPackage().getName();

    public static final Class<?>[] DEFAULT_GROUPS = new Class<?>[] { Default.class };
    protected static final String ITEM_VARIABLE = "{item}";
    protected static final String LABELS_PREFIX = "labels.";

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final RequestManager requestManager;
    protected final MessagesCreator<MESSAGES> messageCreator;
    protected final Class<?>[] groups; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public ActionValidator(RequestManager requestManager, MessagesCreator<MESSAGES> noArgInLambda, Class<?>... groups) {
        assertArgumentNotNull("requestManager", requestManager);
        assertArgumentNotNull("noArgInLambda", noArgInLambda);
        assertArgumentNotNull("groups", groups);
        this.requestManager = requestManager;
        this.messageCreator = noArgInLambda;
        this.groups = groups;
    }

    // ===================================================================================
    //                                                                            Validate
    //                                                                            ========
    // -----------------------------------------------------
    //                                               General
    //                                               -------
    public ValidationSuccess validate(Object form, VaMore<MESSAGES> doValidateLambda, VaErrorHook validationErrorLambda) {
        return doValidate(form, doValidateLambda, validationErrorLambda);
    }

    protected ValidationSuccess doValidate(Object form, VaMore<MESSAGES> doValidateLambda, VaErrorHook validationErrorLambda) {
        assertArgumentNotNull("form", form);
        assertArgumentNotNull("doValidateLambda", doValidateLambda);
        assertArgumentNotNull("validationErrorLambda", validationErrorLambda);
        markValidationCalled();
        verifyClientError(form);
        final Set<ConstraintViolation<Object>> vioSet = hibernateValidate(form);
        @SuppressWarnings("unchecked")
        final MESSAGES messages = (MESSAGES) toActionMessages(form, vioSet);
        doValidateLambda.more(messages);
        if (!messages.isEmpty()) {
            throwValidationErrorException(messages, validationErrorLambda);
        }
        return createValidationSuccess(messages);
    }

    protected void markValidationCalled() {
        if (ThreadCacheContext.exists()) {
            ThreadCacheContext.markValidatorCalled();
        }
    }

    public void throwValidationError(MessagesCreator<MESSAGES> noArgInLambda, VaErrorHook validationErrorLambda) {
        throwValidationErrorException(noArgInLambda.provide(), validationErrorLambda);
    }

    protected void throwValidationErrorException(MESSAGES messages, VaErrorHook validationErrorLambda) {
        throw new ValidationErrorException(messages, validationErrorLambda);
    }

    protected ValidationSuccess createValidationSuccess(MESSAGES messages) {
        return new ValidationSuccess(messages);
    }

    // -----------------------------------------------------
    //                                               for API
    //                                               -------
    public ValidationSuccess validateApi(Object form, VaMore<MESSAGES> doValidateLambda) {
        return doValidate(form, doValidateLambda, () -> hookApiValidationError());
    }

    public void throwValidationErrorApi(MessagesCreator<MESSAGES> noArgInLambda) {
        throwValidationErrorException(noArgInLambda.provide(), () -> hookApiValidationError());
    }

    protected ApiResponse hookApiValidationError() { // for API
        final ApiFailureResource resource = createApiFailureResource(requestManager.errors().get(), requestManager);
        return requestManager.getApiManager().handleValidationError(resource);
    }

    protected ApiFailureResource createApiFailureResource(OptionalThing<ActionMessages> errors, RequestManager requestManager) {
        return new ApiFailureResource(getActionRuntime(), errors, requestManager);
    }

    protected ActionRuntime getActionRuntime() {
        return LaActionRuntimeUtil.getActionRuntime();
    }

    // ===================================================================================
    //                                                                 Hibernate Validator
    //                                                                 ===================
    protected Set<ConstraintViolation<Object>> hibernateValidate(Object form) {
        return comeOnHibernateValidator().validate(form, groups);
    }

    // -----------------------------------------------------
    //                                    Validator Settings
    //                                    ------------------
    protected Validator comeOnHibernateValidator() {
        final Configuration<?> configure = newGenericBootstrap().configure();
        configure.messageInterpolator(newResourceBundleMessageInterpolator());
        return configure.buildValidatorFactory().getValidator();
    }

    protected GenericBootstrap newGenericBootstrap() {
        return Validation.byDefaultProvider();
    }

    protected ResourceBundleMessageInterpolator newResourceBundleMessageInterpolator() {
        return new ResourceBundleMessageInterpolator(newResourceBundleLocator());
    }

    // -----------------------------------------------------
    //                                       Resource Bundle
    //                                       ---------------
    protected ResourceBundleLocator newResourceBundleLocator() {
        return new ResourceBundleLocator() {
            public ResourceBundle getResourceBundle(Locale locale) {
                return newHookedResourceBundle(locale);
            }
        };
    }

    protected ResourceBundle newHookedResourceBundle(Locale locale) {
        return new HookedResourceBundle(requestManager.getMessageManager(), locale);
    }

    protected static class HookedResourceBundle extends ResourceBundle {

        protected final MessageManager messageManager;
        protected final Locale locale;

        public HookedResourceBundle(MessageManager messageManager, Locale locale) {
            this.messageManager = messageManager;
            this.locale = locale;
        }

        @Override
        protected Object handleGetObject(String key) {
            // if null (and token), find it from annotation attributes
            // (at heart, want to know whether token or not)
            final String realKey = filterMessageKey(key);
            final OptionalThing<String> opt = messageManager.findMessage(locale, realKey);
            checkMainMessageNotFound(opt, key);
            return opt.orElse(null);
        }

        protected String filterMessageKey(String key) {
            final String javaxPackage = "javax.validation.";
            final String hibernatePackage = "org.hibernate.validator.";
            final String lastaflutePackage = "org.lastaflute.validator.";
            final String realKey;
            if (key.startsWith(javaxPackage)) {
                realKey = Srl.substringFirstRear(key, javaxPackage);
            } else if (key.startsWith(hibernatePackage)) {
                realKey = Srl.substringFirstRear(key, hibernatePackage);
            } else if (key.startsWith(lastaflutePackage)) {
                realKey = Srl.substringFirstRear(key, lastaflutePackage);
            } else {
                realKey = key;
            }
            return realKey;
        }

        protected void checkMainMessageNotFound(OptionalThing<String> opt, String key) {
            if (mightBeMainMessage(key)) {
                opt.get(); // throw
            }
        }

        protected boolean mightBeMainMessage(String key) {
            return key.contains(".");
        }

        @Override
        public Enumeration<String> getKeys() {
            throw new IllegalStateException("Should not be called: getKeys()");
        }
    }

    // ===================================================================================
    //                                                                     Action Messages
    //                                                                     ===============
    protected ActionMessages toActionMessages(Object form, Set<ConstraintViolation<Object>> vioSet) {
        final TreeMap<String, Object> orderedMap = prepareOrderedMap(form, vioSet);
        final ActionMessages messages = prepareActionMessages();
        for (Entry<String, Object> entry : orderedMap.entrySet()) {
            final Object holder = entry.getValue();
            if (holder instanceof ConstraintViolation) {
                @SuppressWarnings("unchecked")
                final ConstraintViolation<Object> vio = (ConstraintViolation<Object>) holder;
                registerActionMessage(messages, vio);
            } else if (holder instanceof List<?>) {
                @SuppressWarnings("unchecked")
                final List<ConstraintViolation<Object>> vioList = ((List<ConstraintViolation<Object>>) holder);
                for (ConstraintViolation<Object> vio : vioList) {
                    registerActionMessage(messages, vio);
                }
            } else {
                throw new IllegalStateException("Unknown type of holder: " + holder);
            }
        }
        return messages;
    }

    // -----------------------------------------------------
    //                                           Ordered Map
    //                                           -----------
    // basically for batch display
    protected TreeMap<String, Object> prepareOrderedMap(Object form, Set<ConstraintViolation<Object>> vioSet) {
        final Map<String, Object> vioPropMap = new HashMap<>(vioSet.size());
        for (ConstraintViolation<Object> vio : vioSet) {
            final String propertyPath = extractPropertyPath(vio);
            final boolean nested = propertyPath.contains(".");
            final String propertyName = nested ? Srl.substringFirstFront(propertyPath, ".") : propertyPath;
            Object holder = vioPropMap.get(propertyName);
            if (holder == null) {
                holder = nested ? new ArrayList<>(4) : vio; // direct holder for performance
                vioPropMap.put(propertyName, holder);
            } else if (holder instanceof ConstraintViolation<?>) {
                @SuppressWarnings("unchecked")
                final ConstraintViolation<Object> existing = ((ConstraintViolation<Object>) holder);
                final List<Object> listHolder = new ArrayList<>(4);
                listHolder.add(existing);
                listHolder.add(vio);
                vioPropMap.put(propertyName, listHolder); // override
            }
            if (holder instanceof List<?>) {
                @SuppressWarnings("unchecked")
                final List<Object> listHolder = (List<Object>) holder;
                listHolder.add(vio);
            }
        }
        final BeanDesc beanDesc = BeanDescFactory.getBeanDesc(form.getClass());
        final int pdSize = beanDesc.getPropertyDescSize();
        final Map<String, Integer> priorityMap = new HashMap<>(vioPropMap.size());
        for (int i = 0; i < pdSize; i++) {
            final PropertyDesc pd = beanDesc.getPropertyDesc(i);
            final String propertyName = pd.getPropertyName();
            if (vioPropMap.containsKey(propertyName)) {
                priorityMap.put(propertyName, i);
            }
        }
        final TreeMap<String, Object> orderedMap = new TreeMap<String, Object>((key1, key2) -> {
            final String rootProperty1 = Srl.substringFirstFront(key1, "[", ".");
            final String rootProperty2 = Srl.substringFirstFront(key2, "[", ".");
            final Integer priority1 = priorityMap.getOrDefault(rootProperty1, Integer.MAX_VALUE);
            final Integer priority2 = priorityMap.getOrDefault(rootProperty2, Integer.MAX_VALUE);
            if (priority1 > priority2) {
                return 1;
            } else if (priority2 > priority1) {
                return -1;
            } else { /* same group */
                return key1.compareTo(key2);
            }
        });
        orderedMap.putAll(vioPropMap);
        return orderedMap;
    }

    // -----------------------------------------------------
    //                                       Messages Assist
    //                                       ---------------
    protected MESSAGES prepareActionMessages() {
        return messageCreator.provide();
    }

    protected void registerActionMessage(ActionMessages messages, ConstraintViolation<Object> vio) {
        final String propertyPath = extractPropertyPath(vio);
        final String message = filterMessage(extractMessage(vio), propertyPath);
        messages.add(propertyPath, newDirectActionMessage(message));
    }

    protected String extractPropertyPath(ConstraintViolation<Object> vio) {
        return vio.getPropertyPath().toString();
    }

    protected String extractMessage(ConstraintViolation<Object> vio) {
        return vio.getMessage();
    }

    protected ActionMessage newDirectActionMessage(String msg) {
        return new ActionMessage(msg, false);
    }

    protected String filterMessage(String message, String propertyPath) {
        final String itemVariable = getItemVariable(message, propertyPath);
        if (message.contains(itemVariable)) {
            final Locale userLocale = requestManager.getUserLocale();
            final String labelKey = buildLabelKey(propertyPath);
            final String itemName = requestManager.getMessageManager().findMessage(userLocale, labelKey).orElseGet(() -> {
                return getDefaultItem(message, propertyPath);
            });
            return Srl.replace(message, itemVariable, itemName);
        } else {
            return message;
        }
    }

    protected String getItemVariable(String message, String propertyPath) {
        return ITEM_VARIABLE; // as default
    }

    protected String getDefaultItem(String message, String propertyPath) {
        return propertyPath; // as default
    }

    protected String buildLabelKey(String propertyPath) {
        return LABELS_PREFIX + propertyPath; // as default
    }

    // ===================================================================================
    //                                                                        Client Error
    //                                                                        ============
    protected void verifyClientError(Object form) {
        final Set<ConstraintViolation<Object>> clientErrorSet = clientErrorHibernateValidate(form);
        if (!clientErrorSet.isEmpty()) {
            throwClientErrorByValidatorException(form, clientErrorSet);
        }
    }

    protected Set<ConstraintViolation<Object>> clientErrorHibernateValidate(Object form) {
        return comeOnHibernateValidator().validate(form, ClientError.class);
    }

    protected void throwClientErrorByValidatorException(Object form, Set<ConstraintViolation<Object>> clientErrorSet) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Client Error detected by validator:");
        @SuppressWarnings("unchecked")
        final MESSAGES messages = (MESSAGES) toActionMessages(form, clientErrorSet);
        messages.toPropertySet().forEach(property -> {
            final Iterator<ActionMessage> ite = messages.nonAccessByIteratorOf(property);
            while (ite.hasNext()) {
                sb.append("\n ").append(property).append(" ").append((ActionMessage) ite.next());
            }
        });
        final String msg = sb.toString();
        throw new ClientErrorByValidatorException(msg, messages);
    }

    // ===================================================================================
    //                                                                        Assist Logic
    //                                                                        ============
    public static boolean isValidatorAnnotation(Class<?> annoType) { // as utility
        final String annoName = annoType.getName();
        return Srl.startsWith(annoName, JAVAX_CONSTRAINTS_PKG, HIBERNATE_CONSTRAINTS_PKG) // normal annotations
                || isNestedBeanAnnotation(annoType) // has validation nested bean
                || isRequiredAnnotation(annoType); // LastaFlute provides
    }

    public static boolean isNestedBeanAnnotation(Class<?> annoType) {
        return annoType.equals(Valid.class);
    }

    public static boolean hasNestedBeanAnnotation(Field field) {
        return field.getAnnotation(Valid.class) != null;
    }

    public static boolean isRequiredAnnotation(Class<?> annoType) {
        return annoType.equals(Required.class);
    }

    public static boolean hasRequiredAnnotation(Field field) {
        return field.getAnnotation(Required.class) != null;
    }

    // ===================================================================================
    //                                                                       Assert Helper
    //                                                                       =============
    protected void assertArgumentNotNull(String variableName, Object value) {
        if (variableName == null) {
            String msg = "The value should not be null: variableName=null value=" + value;
            throw new IllegalArgumentException(msg);
        }
        if (value == null) {
            String msg = "The value should not be null: variableName=" + variableName;
            throw new IllegalArgumentException(msg);
        }
    }
}
