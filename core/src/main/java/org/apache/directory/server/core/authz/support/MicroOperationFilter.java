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
package org.apache.directory.server.core.authz.support;


import java.util.Collection;
import java.util.Iterator;

import javax.naming.NamingException;

import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.shared.ldap.aci.ACITuple;
import org.apache.directory.shared.ldap.aci.MicroOperation;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * An {@link ACITupleFilter} that discard tuples which doesn't contain any
 * related {@link MicroOperation}s. (18.8.3.4, X.501) 
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $, $Date: 2010-03-24 19:39:54 +0100 (Mer, 24 mar 2010) $
 *
 */
public class MicroOperationFilter implements ACITupleFilter
{
    public Collection<ACITuple> filter( 
            SchemaManager schemaManager, 
            Collection<ACITuple> tuples, 
            OperationScope scope, 
            OperationContext opContext,
            Collection<DN> userGroupNames, 
            DN userName, 
            ServerEntry userEntry, 
            AuthenticationLevel authenticationLevel,
            DN entryName, 
            String attrId, 
            Value<?> attrValue, 
            ServerEntry entry, 
            Collection<MicroOperation> microOperations,
            ServerEntry entryView )
        throws NamingException
    {
        if ( tuples.size() == 0 )
        {
            return tuples;
        }

        for ( Iterator<ACITuple> i = tuples.iterator(); i.hasNext(); )
        {
            ACITuple tuple = i.next();

            /*
             * The ACITuple must contain all the MicroOperations specified within the
             * microOperations argument.  Just matching a single microOperation is not
             * enough.  All must be matched to retain the ACITuple.
             */

            boolean retain = true;
            
            for ( MicroOperation microOp:microOperations )
            {
                if ( !tuple.getMicroOperations().contains( microOp ) )
                {
                    retain = false;
                    break;
                }
            }

            if ( !retain )
            {
                i.remove();
            }
        }

        return tuples;
    }

}
