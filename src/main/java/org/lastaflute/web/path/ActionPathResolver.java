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
package org.lastaflute.web.path;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfCollectionUtil;
import org.dbflute.util.DfStringUtil;
import org.dbflute.util.Srl;
import org.lastaflute.core.direction.FwAssistantDirector;
import org.lastaflute.di.core.LaContainer;
import org.lastaflute.di.naming.NamingConvention;
import org.lastaflute.di.util.LdiStringUtil;
import org.lastaflute.web.UrlChain;
import org.lastaflute.web.direction.OptionalWebDirection;
import org.lastaflute.web.ruts.config.ActionExecute;
import org.lastaflute.web.ruts.config.ActionMapping;
import org.lastaflute.web.util.LaActionExecuteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The resolver of action.
 * @author jflute
 */
public class ActionPathResolver {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger LOG = LoggerFactory.getLogger(ActionPathResolver.class);

    /** The mark of redirect for SAStruts. */
    public static final String REDIRECT = ActionMapping.REDIRECT;

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    /** The assistant directory (AD) for framework. (NotNull: after initialization) */
    @Resource
    protected FwAssistantDirector assistantDirector;

    /** The container instance of Seasar for this class (not root but you can get root). (NotNull) */
    @Resource
    protected LaContainer container;

    /** The naming convention instance of Seasar. (NotNull) */
    @Resource
    protected NamingConvention namingConvention;

    /** The provider of action adjustment. (NotNull: after initialization) */
    protected ActionAdjustmentProvider actionAdjustmentProvider;

    // ===================================================================================
    //                                                                          Initialize
    //                                                                          ==========
    /**
     * Initialize this component. <br>
     * This is basically called by DI setting file.
     */
    @PostConstruct
    public synchronized void initialize() {
        final OptionalWebDirection direction = assistOptionalActionDirection();
        actionAdjustmentProvider = direction.assistActionAdjustmentProvider();
        showBootLogging();
    }

    protected OptionalWebDirection assistOptionalActionDirection() {
        return assistantDirector.assistOptionalWebDirection();
    }

    protected void showBootLogging() {
        if (LOG.isInfoEnabled()) {
            LOG.info("[Action Resolver]");
            LOG.info(" actionAdjustmentProvider: " + actionAdjustmentProvider);
        }
    }

    // ===================================================================================
    //                                                                 ActionPath Handling
    //                                                                 ===================
    /**
     * Handle the action path from the specified request path.
     * @param requestPath The request path to be analyzed. (NotNull)
     * @param handler The handler of the action path when the action is found. (NotNull)
     * @return Is it actually handled? (false if not found)
     * @throws Exception When the handler throws or internal process throws.
     */
    public boolean handleActionPath(String requestPath, ActionFoundPathHandler handler) throws Exception {
        assertArgumentNotNull("requestPath", requestPath);
        assertArgumentNotNull("handler", handler);
        final String customized = actionAdjustmentProvider.customizeActionMappingRequestPath(requestPath);
        return actuallyHandleActionPath(customized != null ? customized : requestPath, handler);
    }

    protected boolean actuallyHandleActionPath(String requestPath, ActionFoundPathHandler handler) throws Exception {
        final String[] names = LdiStringUtil.split(requestPath, "/"); // e.g. [sea, land] if /sea/land/
        final LaContainer root = container.getRoot(); // because actions are in root
        if (names.length == 0) { // root action, / => rootAction
            final String rootAction = "rootAction";
            if (root.hasComponentDef(rootAction)) {
                if (doHandleActionPath(requestPath, handler, rootAction, null)) {
                    return true;
                }
            }
        }
        StringBuilder prefixSb = null; // lazy loaded, "" => sea => seaLand
        List<String> previousList = null; // lazy loaded, (empty) => [sea] => [sea, land]
        for (int index = 0; index < names.length; index++) {
            final String currentName = names[index];
            final int nextIndex = index + 1;
            if (index == 0) { // first loop
                // /sea/ => seaAction
                final String directAction = buildActionName(null, currentName);
                if (root.hasComponentDef(directAction)) {
                    if (doHandleActionPath(requestPath, handler, directAction, buildParamPath(names, nextIndex))) {
                        return true;
                    }
                }
                // /sea/ => sea_seaAction
                final String wholePkgAction = buildActionName(currentName + "_", currentName);
                if (root.hasComponentDef(wholePkgAction)) {
                    if (doHandleActionPath(requestPath, handler, wholePkgAction, buildParamPath(names, nextIndex))) {
                        return true;
                    }
                }
            } else { // second or more loop
                // _/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/_/
                // e.g. process of outer and nested loop
                // 
                // second : /sea/land/
                //   first  : seaLandAction
                //   more   : sea_seaLandAction
                //   whole  : sea_land_seaLandAction
                // 
                // third : /sea/land/iks/
                //   first  : seaLandIksAction
                //   second : sea_seaLandIksAction
                //   more   : sea_land_seaLandIksAction
                //   whole  : sea_land_iks_seaLandIksAction
                // 
                // fourth : /sea/land/iks/amba
                //   first  : seaLandIksAmbaAction
                //   second : sea_seaLandIksAmbaAction
                //   third  : sea_land_seaLandIksAmbaAction
                //   more   : sea_land_iks_seaLandIksAmbaAction
                //   whole  : sea_land_iks_amba_seaLandIksAmbaAction
                // _/_/_/_/_/_/_/_/_/_/
                final String classPrefix = prefixSb.toString() + initCap(currentName); // seaLand, seaLandIks
                StringBuilder pkgSb = null; // lazy loaded
                for (String previous : previousList) { // always one or more loop
                    final String actionName = buildActionName(pkgSb != null ? pkgSb.toString() : null, classPrefix);
                    if (root.hasComponentDef(actionName)) {
                        if (doHandleActionPath(requestPath, handler, actionName, buildParamPath(names, nextIndex))) {
                            return true;
                        }
                    }
                    pkgSb = pkgSb != null ? pkgSb : new StringBuilder(requestPath.length());
                    pkgSb.append(previous).append("_");
                }
                final String morePkgActionName = buildActionName(pkgSb.toString(), classPrefix);
                if (root.hasComponentDef(morePkgActionName)) {
                    if (doHandleActionPath(requestPath, handler, morePkgActionName, buildParamPath(names, nextIndex))) {
                        return true;
                    }
                }
                pkgSb.append(currentName).append("_"); // sea_land_, sea_land_iks_, ...
                final String wholePkgActionName = buildActionName(pkgSb.toString(), classPrefix);
                if (root.hasComponentDef(wholePkgActionName)) {
                    if (doHandleActionPath(requestPath, handler, wholePkgActionName, buildParamPath(names, nextIndex))) {
                        return true;
                    }
                }
            }
            prefixSb = prefixSb != null ? prefixSb : new StringBuilder(requestPath.length());
            prefixSb.append(index == 0 ? currentName : initCap(currentName));
            previousList = previousList != null ? previousList : new ArrayList<String>(4);
            previousList.add(currentName);
        }
        return false;
    }

    protected String buildActionName(String pkg, String classPrefix) {
        return (pkg != null ? pkg : "") + classPrefix + namingConvention.getActionSuffix();
    }

    protected String buildParamPath(String[] names, int index) {
        final int length = names.length;
        if (index >= length) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = index; i < length; i++) {
            if (i != index) {
                sb.append('/');
            }
            sb.append(names[i]);
        }
        return sb.toString(); // e.g. 3 when /member/list/3/
    }

    protected boolean doHandleActionPath(String requestPath, ActionFoundPathHandler handler, String actionName, String paramPath)
            throws Exception {
        final boolean emptyParam = paramPath == null || paramPath.isEmpty();
        final ActionExecute configByParam = !emptyParam ? findExecuteConfig(actionName, paramPath).orElse(null) : null;
        if (emptyParam || configByParam != null) { // certainly hit
            return handler.handleActionPath(requestPath, actionName, paramPath, configByParam);
        }
        return false;
    }

    protected OptionalThing<ActionExecute> findExecuteConfig(String actionName, String paramPath) {
        return LaActionExecuteUtil.findActionExecute(actionName, paramPath);
    }

    // ===================================================================================
    //                                                                  ActionPath Convert
    //                                                                  ==================
    /**
     * Convert to URL string to move the action. <br>
     * This method is to build URL string by manually so normally you don't use directly from your action.
     * @param actionType The class type of action that it redirects to. (NotNull)
     * @param redirect Do you redirect to the action?
     * @param chain The chain of URL to build additional info on URL. (NullAllowed)
     * @return The URL string to move to the action. (NotNull)
     */
    public String toActionUrl(Class<?> actionType, boolean redirect, UrlChain chain) {
        final String actionPath = resolveActionPath(actionType);
        final List<Object> getParamList = extractGetParamList(chain);
        final StringBuilder sb = new StringBuilder();
        sb.append(actionPath);
        buildUrlParts(sb, chain);
        buildGetParam(sb, actionPath, getParamList);
        buildHashOnUrl(sb, chain);
        buildRedirectMark(sb, redirect, getParamList);
        return sb.toString();
    }

    protected List<Object> extractGetParamList(UrlChain chain) {
        final Object[] paramsOnGet = chain != null ? chain.getParamsOnGet() : null;
        final List<Object> getParamList;
        if (paramsOnGet != null) {
            getParamList = DfCollectionUtil.newArrayList(paramsOnGet);
        } else {
            getParamList = DfCollectionUtil.emptyList();
        }
        return getParamList;
    }

    protected void buildUrlParts(StringBuilder sb, UrlChain chain) {
        final Object[] urlParts = chain != null ? chain.getUrlParts() : null;
        if (urlParts != null) {
            for (Object param : urlParts) {
                if (param != null) {
                    sb.append(param).append("/");
                }
            }
        }
    }

    protected void buildGetParam(StringBuilder sb, String actionPath, List<Object> getParamList) {
        int index = 0;
        for (Object param : getParamList) {
            if (index == 0) { // first loop
                sb.append("?");
            } else {
                if (index % 2 == 0) {
                    sb.append("&");
                } else if (index % 2 == 1) {
                    sb.append("=");
                } else { // no way
                    String msg = "no way: url=" + actionPath + " get-params=" + getParamList;
                    throw new IllegalStateException(msg);
                }
            }
            sb.append(param != null ? param : "");
            ++index;
        }
    }

    protected void buildHashOnUrl(StringBuilder sb, UrlChain chain) {
        final Object hash = chain != null ? chain.getHashOnUrl() : null;
        if (hash != null) {
            sb.append("#").append(hash);
        }
    }

    protected void buildRedirectMark(StringBuilder sb, boolean redirect, List<Object> getParamList) {
        if (redirect) {
            sb.append(!getParamList.isEmpty() ? "&" : "?");
            sb.append(REDIRECT);
        }
    }

    // -----------------------------------------------------
    //                                    Resolve ActionPath
    //                                    ------------------
    public String resolveActionPath(Class<?> actionType) {
        final String delimiter = "/";
        return delimiter + decamelize(toSimpleActionName(actionType), delimiter) + delimiter;
    }

    protected String toSimpleActionName(Class<?> actionType) {
        final String componentName = namingConvention.fromClassNameToComponentName(actionType.getName());
        return removeRearActionSuffixIfNeeds(Srl.substringLastRear(componentName, "_"));
    }

    protected String removeRearActionSuffixIfNeeds(String path) {
        final String actionSuffix = namingConvention.getActionSuffix();
        return path.endsWith(actionSuffix) ? path.substring(0, path.length() - actionSuffix.length()) : path;
    }

    protected String decamelize(String simpleName, String delimiter) {
        return Srl.decamelize(simpleName, delimiter).toLowerCase(); // seaLand => SEA/LAND => sea/land
    }

    // ===================================================================================
    //                                                              ActionPath Calculation
    //                                                              ======================
    public String calculateActionPathByJspPath(String requestPath) {
        final String lastPathElement = substringLastRear(requestPath, "/");
        if (!lastPathElement.contains(".")) { // no JSP
            return requestPath;
        }
        // basically JSP here
        final String frontPathElement = substringLastFront(requestPath, "/");
        final String fileNameNoExt = substringLastFront(lastPathElement, ".");
        final String pathBase = frontPathElement + "/";
        if (!fileNameNoExt.contains("_")) { // e.g. list.jsp
            return pathBase; // e.g. /member/ (submit name is needed in this case)
        }
        // the file name has package prefix here
        // e.g. /member/member_list.jsp or /member/list/member_purchase_list.jsp
        final List<String> wordList = splitList(fileNameNoExt, "_"); // e.g. [member, list] or [member, purchase, list]
        if (wordList.size() < 2) { // no way (just in case)
            return pathBase;
        }
        final String firstHit = resolveJspActionPath(requestPath, frontPathElement, pathBase, wordList);
        if (firstHit != null) {
            return firstHit; // e.g. /member/list/ (from /member/member_list.jsp)
        }
        final List<String> retryList = prepareJspRetryWordList(requestPath, wordList);
        if (retryList != null && !retryList.isEmpty()) { // e.g. [member, list] (from sp_member_list.jsp)
            final String retryHit = resolveJspActionPath(requestPath, frontPathElement, pathBase, retryList);
            if (retryHit != null) {
                return retryHit; // e.g. /member/list/ (from /member/sp_member_list.jsp)
            }
        }
        // e.g. /member/purchase_list.jsp
        return pathBase; // e.g. /member/ (submit name is needed in this case)
    }

    protected String resolveJspActionPath(String requestPath, String frontPathElement, String pathBase, List<String> wordList) {
        String previousSuffix = "";
        for (int i = 0; i < wordList.size(); i++) {
            // e.g. 1st: '' + '/' + member, 2nd: /member + '/' + purchase
            final String pathSuffix = previousSuffix + "/" + wordList.get(i);
            final boolean nextLoopLast = wordList.size() == i + 2;
            if (nextLoopLast && frontPathElement.endsWith(pathSuffix)) {
                // e.g. 1st: /member/list/, 2nd: /member/purchase/list/
                final String lastElement = wordList.get(i + 1);
                final String resolvedPath;
                if (lastElement.equals("index")) { // e.g. /member/list/member_list_index.jsp
                    resolvedPath = pathBase;
                } else {
                    resolvedPath = pathBase + lastElement + "/";
                }
                return resolvedPath;
            }
            previousSuffix = pathSuffix;
        }
        return null;
    }

    protected List<String> prepareJspRetryWordList(String requestPath, List<String> wordList) {
        return actionAdjustmentProvider.prepareHtmlRetryWordList(requestPath, wordList);
    }

    // ===================================================================================
    //                                                                 Redirect Adjustment
    //                                                                 ===================
    /**
     * Convert the request path (or URL) to redirect path for SAStruts. e.g. /member/list/?redirect=true
     * @param requestPath The path of request. e.g. /member/list/ (NotNull)
     * @return The request path (or URL) with redirect mark. (NotNull)
     */
    public String toRedirectPath(String requestPath) {
        final String delimiter = requestPath.contains("?") ? "&" : "?";
        return requestPath + delimiter + REDIRECT;
    }

    /**
     * Convert the request path (or URL) to SSL redirect path for SAStruts. <br>
     * e.g. https://...com/member/list/?redirect=true
     * @param requestPath The path (or URL) of request. e.g. http://...com/member/list/ (NotNull)
     * @return The request path (or URL) with redirect mark. (NotNull)
     */
    public String toSslRedirectPath(String requestPath) {
        return toRedirectPath(requestPath.replaceFirst("http:", "https:"));
    }

    /**
     * Convert the request path (or URL) to non-SSL redirect path for SAStruts. <br>
     * e.g. http://...com/member/list/?redirect=true
     * @param requestPath The path (or URL) of request. e.g. https://...com/member/list/ (NotNull)
     * @return The request path (or URL) with redirect mark. (NotNull)
     */
    public String toNonSslRedirectPath(String requestPath) {
        return toRedirectPath(requestPath.replaceFirst("https:", "http:"));
    }

    /**
     * Remove the redirect mark from redirect path for SAStruts. <br>
     * e.g. /member/list/?redirect=true -&gt; /member/list/
     * @param redirectPath The path to redirect for SAStruts. e.g. /member/list/?redirect=true (NotNull)
     * @return The plain redirect path without redirect mark. (NotNull)
     */
    public String removeRedirectMark(String redirectPath) {
        final String redirectMark = REDIRECT;
        if (redirectPath.endsWith(redirectMark)) {
            final String removed = substringLastFront(redirectPath, redirectMark);
            return removed.substring(0, removed.length() - 1); // and remove delimiter
        }
        return redirectPath;
    }

    // ===================================================================================
    //                                                                    Expected Routing
    //                                                                    ================
    public String prepareExpectedRoutingMessage(String requestPath) { // for debug
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("/= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = *No routing action:\n");
        sb.append("e.g. expected actions for ").append(requestPath).append("\n");
        final String customizedPath = actionAdjustmentProvider.customizeActionMappingRequestPath(requestPath);
        final List<String> nameList = buildExpectedRoutingActionList(customizedPath != null ? customizedPath : requestPath);
        for (String name : nameList) {
            sb.append("  web.").append(name).append("\n");
        }
        sb.append("  (and so on...)\n");
        sb.append("= = = = = = = = = =/");
        return sb.toString();
    }

    protected List<String> buildExpectedRoutingActionList(String requestPath) {
        final List<String> tokenList;
        {
            final String trimmedPath = trim(requestPath, "/"); // /member/list/ -> member/list
            final List<String> splitList = splitList(trimmedPath, "/"); // [member, list]
            tokenList = new ArrayList<String>(splitList.size()); // removed empty elements
            for (String element : splitList) {
                if (element.trim().length() == 0) {
                    continue; // e.g. /member//list/
                }
                tokenList.add(element);
            }
        }
        // e.g. / or /123/ or /123/foo/
        if (tokenList.isEmpty() || mayBeParameterToken(tokenList.get(0))) {
            final List<String> nameList = new ArrayList<String>(1);
            nameList.add("IndexAction#index()");
            return nameList;
        }
        // e.g. /foo/ or /foo/123/ or /foo/123/bar/
        //   web.FooAction#index()
        //   web.IndexAction#foo()
        //   web.foo.FooIndexAction#index()
        // e.g. /foo/bar/ or /foo/bar/123/ or /foo/bar/123/qux/
        //   foo.FooBarAction#index()
        //   foo.FooAction#bar()
        //   foo.bar.FooBarIndexAction#index()
        final StringBuilder namedActionSb = new StringBuilder();
        final StringBuilder methodActionSb = new StringBuilder();
        final StringBuilder wholePkgActionSb = new StringBuilder();
        final StringBuilder pkgPrefix = new StringBuilder();
        boolean existsMethodAction = false;
        boolean existsWholePkgAction = false;
        for (int index = 0; index < tokenList.size(); index++) {
            final String current = tokenList.get(index);
            if (index == 0) {
                namedActionSb.append(current).append(".");
                methodActionSb.append(current).append(".");
            }
            final boolean beforeLastLoop = index < tokenList.size() - 1;
            final String next = beforeLastLoop ? tokenList.get(index + 1) : null;
            final boolean nextParam = next != null ? mayBeParameterToken(next) : false;
            final String capElement = initCap(current);
            if (beforeLastLoop && !nextParam) { // before last action token
                wholePkgActionSb.append(current).append(".");
                pkgPrefix.append(capElement);
            } else { // last action token here (last loop or next token is parameter)
                // web.SeaAction#index() or web.sea.SeaLandAction#index()
                namedActionSb.append(pkgPrefix).append(capElement).append("Action#index()");

                if (index > 0) {
                    // web.sea.SeaAction#land()
                    methodActionSb.append(pkgPrefix).append("Action#");
                    methodActionSb.append(current).append("()");
                    existsMethodAction = true;

                    // web.land.sea.LandSeaAction#index()
                    pkgPrefix.append(capElement);
                    wholePkgActionSb.append(current).append(".").append(pkgPrefix).append("Action#index()");
                    existsWholePkgAction = true;
                }
                break;
            }
        }
        final List<String> nameList = new ArrayList<String>(3);
        nameList.add(namedActionSb.toString());
        if (existsMethodAction) {
            nameList.add(methodActionSb.toString());
        }
        if (existsWholePkgAction) {
            nameList.add(wholePkgActionSb.toString());
        }
        return nameList;
    }

    protected boolean mayBeParameterToken(String token) {
        if (isFirstCharNumber(token)) { // e.g. 123 or 4ab
            return true;
        }
        if (DfStringUtil.containsAny(token, ".", "%", "?", "&")) { // e.g. a.b or %2d or ?foo=bar or ...
            return true;
        }
        return false;
    }

    protected boolean isFirstCharNumber(String token) {
        return "0123456789+-".contains(token.substring(0, 1));
    }

    // ===================================================================================
    //                                                                      General Helper
    //                                                                      ==============
    protected String replace(String str, String fromStr, String toStr) {
        return DfStringUtil.replace(str, fromStr, toStr);
    }

    protected String substringFirstFront(String str, String... delimiters) {
        return DfStringUtil.substringFirstFront(str, delimiters);
    }

    protected String substringFirstRear(String str, String... delimiters) {
        return DfStringUtil.substringFirstRear(str, delimiters);
    }

    protected String substringLastFront(String str, String... delimiters) {
        return DfStringUtil.substringLastFront(str, delimiters);
    }

    protected String substringLastRear(String str, String... delimiters) {
        return DfStringUtil.substringLastRear(str, delimiters);
    }

    protected List<String> splitList(String str, String delimiter) {
        return DfStringUtil.splitList(str, delimiter);
    }

    protected String initCap(String str) {
        return DfStringUtil.initCap(str);
    }

    protected String initUncap(String str) {
        return DfStringUtil.initUncap(str);
    }

    protected String trim(String str, String trimStr) {
        return DfStringUtil.trim(str, trimStr);
    }

    // ===================================================================================
    //                                                                       Assist Helper
    //                                                                       =============
    protected void assertArgumentNotNull(String variableName, Object value) {
        if (variableName == null) {
            throw new IllegalArgumentException("The variableName should not be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("The argument '" + variableName + "' should not be null.");
        }
    }
}