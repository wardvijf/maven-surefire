package org.apache.maven.surefire.junitplatform;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static org.apache.maven.surefire.report.SimpleReportEntry.ignored;
import static org.apache.maven.surefire.report.SimpleReportEntry.withException;

import java.util.Optional;

import org.apache.maven.surefire.report.PojoStackTraceWriter;
import org.apache.maven.surefire.report.RunListener;
import org.apache.maven.surefire.report.SimpleReportEntry;
import org.apache.maven.surefire.report.StackTraceWriter;
import org.apache.maven.surefire.report.TestSetReportEntry;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * @since 2.22.0
 */
final class RunListenerAdapter
    implements TestExecutionListener
{
    private final RunListener runListener;

    private volatile TestPlan testPlan;

    RunListenerAdapter( RunListener runListener )
    {
        this.runListener = runListener;
    }

    @Override
    public void testPlanExecutionStarted( TestPlan testPlan )
    {
        this.testPlan = testPlan;
    }

    @Override
    public void testPlanExecutionFinished( TestPlan testPlan )
    {
        this.testPlan = null;
    }

    @Override
    public void executionStarted( TestIdentifier testIdentifier )
    {
        if ( testIdentifier.isContainer()
                        && testIdentifier.getSource().filter( ClassSource.class::isInstance ).isPresent() )
        {
            runListener.testSetStarting( createTestSetReportEntry( testIdentifier ) );
        }
        else if ( testIdentifier.isTest() )
        {
            runListener.testStarting( createReportEntry( testIdentifier ) );
        }
    }

    @Override
    public void executionFinished( TestIdentifier testIdentifier, TestExecutionResult testExecutionResult )
    {
        boolean isClass = testIdentifier.isContainer()
                && testIdentifier.getSource().filter( ClassSource.class::isInstance ).isPresent();

        boolean isTest = testIdentifier.isTest();

        if ( isClass || isTest )
        {
            switch ( testExecutionResult.getStatus() )
            {
                case ABORTED:
                    TestSetReportEntry reportEntry = createReportEntry( testIdentifier, testExecutionResult );
                    if ( isTest )
                    {
                        runListener.testAssumptionFailure( reportEntry );
                    }
                    else
                    {
                        runListener.testSetCompleted( reportEntry );
                    }
                    break;
                case FAILED:
                    reportEntry = createReportEntry( testIdentifier, testExecutionResult );
                    if ( !isTest )
                    {
                        runListener.testSetCompleted( reportEntry );
                    }
                    else if ( testExecutionResult.getThrowable()
                            .filter( AssertionError.class::isInstance ).isPresent() )
                    {
                        runListener.testFailed( reportEntry );
                    }
                    else
                    {
                        runListener.testError( reportEntry );
                    }
                    break;
                default:
                    reportEntry = createReportEntry( testIdentifier );
                    if ( isTest )
                    {
                        runListener.testSucceeded( reportEntry );
                    }
                    else
                    {
                        runListener.testSetCompleted( reportEntry );
                    }
            }
        }
    }

    @Override
    public void executionSkipped( TestIdentifier testIdentifier, String reason )
    {
        String[] classMethodName = toClassMethodName( testIdentifier );
        String className = classMethodName[0];
        String methodName = classMethodName[1];
        runListener.testSkipped( ignored( className, methodName, reason ) );
    }

    private SimpleReportEntry createTestSetReportEntry( TestIdentifier testIdentifier )
    {
        String[] classMethodName = toClassMethodName( testIdentifier );
        String className = classMethodName[0];
        String methodName = classMethodName[1];
        return new SimpleReportEntry( className, methodName );
    }

    private SimpleReportEntry createReportEntry( TestIdentifier testIdentifier )
    {
        return createReportEntry( testIdentifier, (StackTraceWriter) null );
    }

    private SimpleReportEntry createReportEntry( TestIdentifier testIdentifier,
                                                 TestExecutionResult testExecutionResult )
    {
        return createReportEntry( testIdentifier, toStackTraceWriter( testIdentifier, testExecutionResult ) );
    }

    private SimpleReportEntry createReportEntry( TestIdentifier testIdentifier, StackTraceWriter stackTraceWriter )
    {
        String[] classMethodName = toClassMethodName( testIdentifier );
        String className = classMethodName[0];
        String methodName = classMethodName[1];
        return withException( className, methodName, stackTraceWriter );
    }

    private StackTraceWriter toStackTraceWriter( TestIdentifier testIdentifier,
                                                 TestExecutionResult testExecutionResult )
    {
        switch ( testExecutionResult.getStatus() )
        {
            case ABORTED:
            case FAILED:
                // Failed tests must have a StackTraceWriter, otherwise Surefire will fail
                return toStackTraceWriter( testIdentifier, testExecutionResult.getThrowable().orElse( null ) );
            default:
                return testExecutionResult.getThrowable().map( t -> toStackTraceWriter( testIdentifier, t ) )
                        .orElse( null );
        }
    }

    private StackTraceWriter toStackTraceWriter( TestIdentifier testIdentifier, Throwable throwable )
    {
        String[] classMethodName = toClassMethodName( testIdentifier );
        String className = classMethodName[0];
        String methodName = classMethodName[1];
        return new PojoStackTraceWriter( className, methodName, throwable );
    }

    /**
     * <ul>
     *     <li>[0] class name - used in stacktrace parser</li>
     *     <li>[1] class display name</li>
     *     <li>[2] method signature - used in stacktrace parser</li>
     *     <li>[3] method display name</li>
     * </ul>
     *
     * @param testIdentifier a class or method
     * @return 4 elements string array
     */
    private String[] toClassMethodName( TestIdentifier testIdentifier )
    {
        Optional<TestSource> testSource = testIdentifier.getSource();
        String display = testIdentifier.getDisplayName();

        if ( testSource.filter( MethodSource.class::isInstance ).isPresent() )
        {
            MethodSource methodSource = testSource.map( MethodSource.class::cast ).get();

            String source = testPlan.getParent( testIdentifier )
                    .map( this::toClassMethodName )
                    .map( s -> s[0] )
                    .orElse( methodSource.getClassName() );

            String method = methodSource.getMethodName();
            boolean useMethod = display.equals( method ) || display.equals( method + "()" );
            String name = useMethod ? method : display;

            return new String[] { source, name };
        }
        else if ( testSource.filter( ClassSource.class::isInstance ).isPresent() )
        {
            ClassSource classSource = testSource.map( ClassSource.class::cast ).get();
            String className = classSource.getClassName();
            String simpleClassName = className.substring( 1 + className.lastIndexOf( '.' ) );
            String source = display.equals( simpleClassName ) ? className : display;
            return new String[] { source, source };
        }
        else
        {
            String source = testPlan.getParent( testIdentifier )
                    .map( TestIdentifier::getDisplayName )
                    .orElse( display );
            return new String[] { source, display };
        }
    }
}
