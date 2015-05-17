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
package org.lastaflute.web.callback;

import org.lastaflute.web.response.ActionResponse;
import org.lastaflute.web.ruts.ActionRequestProcessor;
import org.lastaflute.web.ruts.GodHandableAction;

/**
 * The hook for action, which is called from {@link ActionRequestProcessor}. <br>
 * Methods that start with 'godHand' and 'callback' exist. <br >
 * You can creatively use like this:
 * <ul>
 *     <li>The 'godHand' methods are basically for super class by architect.</li>
 *     <li>The 'callback' methods are basically for concrete class by (many) developers.</li>
 * </ul>
 * The methods calling order is like this: <br>
 * (And you can see the details of this callback process by reading {@link GodHandableAction})
 * <pre>
 * try {
 *     godHandPrologue()
 *     hookBefore()
 *     *execute action
 * } catch (...) {
 *     godHandMonologue()
 * } finally {
 *     hookFinally()
 *     godHandEpilogue()
 * }
 * </pre>
 * @author jflute
 */
public interface ActionHook {

    // ===================================================================================
    //                                                                              Before
    //                                                                              ======
    /**
     * Callback process as God hand (means Framework process) for action prologue (preparing action). <br>
     * You should not implement or override this at concrete class because this is for super class. <br>
     * This method calling order is like this:
     * <pre>
     * try {
     *     godHandPrologue() *here
     *     hookBefore()
     *     *execute action
     * } catch (...) {
     *     godHandExceptionMonologue()
     * } finally {
     *     callbackFinally()
     *     godHandFinally()
     *     godHandEpilogue()
     * }
     * </pre>
     * @param runtimeMeta The meta of action execution which you can get the calling method. (NotNull)
     * @return The path to forward. (NotNull: skip action execute, EmptyAllowed: if empty, proceed to next step)
     */
    ActionResponse godHandPrologue(ActionRuntimeMeta runtimeMeta);

    /**
     * Callback process as God hand (means Framework process) before action execution and validation. <br>
     * You should not implement or override this at concrete class because this is for super class. <br>
     * This method calling order is like this:
     * <pre>
     * try {
     *     godHandPrologue()
     *     hookBefore() *here
     *     *execute action
     * } catch (...) {
     *     godHandExceptionMonologue()
     * } finally {
     *     callbackFinally()
     *     godHandFinally()
     *     godHandEpilogue()
     * }
     * </pre>
     * @param runtimeMeta The meta of action execution which you can get the calling method. (NotNull)
     * @return The path to forward. (NotNull: skip action execute, EmptyAllowed: if empty, proceed to next step)
     */
    ActionResponse hookBefore(ActionRuntimeMeta runtimeMeta);

    // ===================================================================================
    //                                                                          on Failure
    //                                                                          ==========
    /**
     * Callback process as God hand (means Framework process) for exception monologue (exception handling). <br>
     * You should not implement or override this at concrete class because this is for super class. <br>
     * It does not contain validation error, which is called on-success process. <br>
     * This method calling order is like this:
     * <pre>
     * try {
     *     godHandPrologue()
     *     hookBefore()
     *     *execute action
     * } catch (...) {
     *     godHandExceptionMonologue() *here
     * } finally {
     *     callbackFinally()
     *     godHandFinally()
     *     godHandEpilogue()
     * }
     * </pre>
     * @param runtimeMeta The meta of action execution which you can get the calling method. (NotNull)
     * @return The path to forward. (NotNull, EmptyAllowed: if empty, proceed to next step)
     */
    ActionResponse godHandMonologue(ActionRuntimeMeta runtimeMeta);

    // ===================================================================================
    //                                                                             Finally
    //                                                                             =======
    /**
     * Callback process as God hand (means Framework process) after action execution (success or not: finally). <br>
     * You should not implement or override this at concrete class because this is for super class. <br>
     * This method calling order is like this:
     * <pre>
     * try {
     *     godHandPrologue()
     *     hookBefore()
     *     *execute action
     * } catch (...) {
     *     godHandExceptionMonologue()
     * } finally {
     *     hookFinally() *here
     *     godHandEpilogue()
     * }
     * </pre>
     * @param runtimeMeta The meta of action execution which you can get the calling method. (NotNull)
     */
    void hookFinally(ActionRuntimeMeta runtimeMeta);

    /**
     * Callback process as God hand (means Framework process) for action epilogue (closing action). <br>
     * You should not implement or override this at concrete class because this is for super class. <br>
     * This method calling order is like this:
     * <pre>
     * try {
     *     godHandPrologue()
     *     hookBefore()
     *     *execute action
     * } catch (...) {
     *     godHandExceptionMonologue()
     * } finally {
     *     hookFinally()
     *     godHandEpilogue() *here
     * }
     * </pre>
     * @param runtimeMeta The meta of action execution which you can get the calling method. (NotNull)
     */
    void godHandEpilogue(ActionRuntimeMeta runtimeMeta);
}