/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.core.invocation;


import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.directory.server.core.interceptor.context.OperationContext;


/**
 * Keeps track of recursive {@link Invocation}s.  This stack assumes an invocation
 * occurs in the same thread since it is called first, so we manages stacks
 * for each invocation in {@link ThreadLocal}-like manner.  You can just use
 * {@link #getInstance()} to get current invocation stack.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 896599 $, $Date: 2010-01-06 19:26:43 +0100 (Mer, 06 jan 2010) $
 */
public final class InvocationStack
{
    // I didn't use ThreadLocal to release contexts explicitly.
    // It seems like JDK 1.5 supports explicit release by introducing
    // <tt>ThreadLocal.remove()</tt>, but we're still targetting 1.4.
    private static final Map<Thread, InvocationStack> stacks = 
        Collections.synchronizedMap( new IdentityHashMap<Thread, InvocationStack>() );

    private final Thread thread;
    private final List<OperationContext> stack = new ArrayList<OperationContext>();

    
    /**
     * Returns the invocation stack of current thread.
     */
    public static InvocationStack getInstance()
    {
        Thread currentThread = Thread.currentThread();
        InvocationStack ctx;
        ctx = stacks.get( currentThread );

        if ( ctx == null )
        {
            ctx = new InvocationStack( currentThread );
        }

        return ctx;
    }
    

    private InvocationStack( Thread currentThread )
    {
        thread = currentThread;
        stacks.put( currentThread, this );
    }


    /**
     * Returns an array of {@link Invocation}s.  0th element is the
     * latest invocation.
     */
    public OperationContext[] toArray()
    {
        OperationContext[] result = new OperationContext[stack.size()];
        result = stack.toArray( result );
        return result;
    }


    /**
     * Returns the latest invocation.
     */
    public OperationContext peek()
    {
        return stack.get( 0 );
    }


    /**
     * Returns true if the stack is empty false otherwise.
     */
    public boolean isEmpty()
    {
        return stack.isEmpty();
    }


    /**
     * Pushes the specified invocation to this stack.
     */
    public void push( OperationContext opContext )
    {
        stack.add( 0, opContext );
    }


    /**
     * Pops the latest invocation from this stack.  This stack is released
     * automatically if you pop all items from this stack.
     */
    public OperationContext pop()
    {
        OperationContext invocation = stack.remove( 0 );
        
        if ( stack.size() == 0 )
        {
            stacks.remove( thread );
        }

        return invocation;
    }
}
