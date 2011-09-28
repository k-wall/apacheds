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
package org.apache.directory.server.core.interceptor.context;


import org.apache.directory.server.core.CoreSession;
import org.apache.directory.shared.ldap.codec.MessageTypeEnum;
import org.apache.directory.shared.ldap.message.internal.InternalUnbindRequest;


/**
 * A Unbind context used for Interceptors. It contains all the informations
 * needed for the unbind operation, and used by all the interceptors
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class UnbindOperationContext extends AbstractOperationContext
{
    /**
     * Creates a new instance of UnbindOperationContext.
     */
    public UnbindOperationContext( CoreSession session )
    {
        super( session, session.getEffectivePrincipal().getClonedName() );
    }
    

    public UnbindOperationContext( CoreSession session, InternalUnbindRequest unbindRequest )
    {
        super( session, session.getEffectivePrincipal().getClonedName() );
        this.setRequestControls( unbindRequest.getControls() );
    }

    
    /**
     * @return the operation name
     */
    public String getName()
    {
        return MessageTypeEnum.UNBIND_REQUEST.name();
    }

    
    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return "UnbindContext for DN '" + getDn().getName() + "'";
    }
}
