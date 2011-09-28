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
package org.apache.directory.server.core.authn;


import javax.naming.Context;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.partition.DefaultPartitionNexus;
import org.apache.directory.shared.ldap.name.DN;


/**
 * Authenticates users who access {@link DefaultPartitionNexus}.
 * <p>
 * {@link Authenticator}s are registered to and configured by
 * {@link AuthenticationInterceptor} interceptor.
 * <p>
 * {@link AuthenticationInterceptor} authenticates users by calling
 * {@link #authenticate(DN,ServerContext)}, and then {@link Authenticator}
 * checks JNDI {@link Context} environment properties
 * ({@link Context#SECURITY_PRINCIPAL} and {@link Context#SECURITY_CREDENTIALS})
 * of current {@link Context}.
 *
 * @see AbstractAuthenticator
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 918766 $
 */
public interface Authenticator
{
    /**
     * Returns the type of this authenticator (e.g. <tt>'simple'</tt>,
     * <tt>'none'</tt>,...).
     */
    String getAuthenticatorType();


    /**
     * Called by {@link AuthenticationInterceptor} to indicate that this
     * authenticator is being placed into service.
     */
    public void init( DirectoryService directoryService ) throws Exception;


    /**
     * Called by {@link AuthenticationInterceptor} to indicate that this
     * authenticator is being removed from service.
     */
    void destroy();

    
    /**
     * Callback used to respond to password changes by invalidating a password
     * cache if implemented.  This is an additional feature of an authenticator
     * which need not be implemented: empty implementation is sufficient.  This
     * is called on every del, modify, and modifyRdn operation.
     * 
     * @param bindDn the already normalized distinguished name of the bind principal
     */
    void invalidateCache( DN bindDn );

    
    /**
     * Performs authentication and returns the principal if succeeded.
     */
    public LdapPrincipal authenticate( BindOperationContext opContext ) throws Exception;
}
