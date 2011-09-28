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


import java.util.ArrayList;
import java.util.Collection;

import javax.naming.NamingException;

import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.shared.ldap.aci.ACITuple;
import org.apache.directory.shared.ldap.aci.MicroOperation;
import org.apache.directory.shared.ldap.aci.ProtectedItem;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * An {@link ACITupleFilter} that chooses the tuples with the most specific
 * protected item. (18.8.4.3, X.501)
 * <p>
 * If more than one tuple remains, choose the tuples with the most specific
 * protected item. If the protected item is an attribute and there are tuples 
 * that specify the attribute type explicitly, discard all other tuples. If
 * the protected item is an attribute value, and there are tuples that specify
 * the attribute value explicitly, discard all other tuples. A protected item
 * which is a rangeOfValues is to be treated as specifying an attribute value
 * explicitly.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $, $Date: 2010-03-24 19:39:54 +0100 (Mer, 24 mar 2010) $
 */
public class MostSpecificProtectedItemFilter implements ACITupleFilter
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
        if ( tuples.size() <= 1 )
        {
            return tuples;
        }

        Collection<ACITuple> filteredTuples = new ArrayList<ACITuple>();

        // If the protected item is an attribute and there are tuples that
        // specify the attribute type explicitly, discard all other tuples.
        for ( ACITuple tuple:tuples )
        {
            for ( ProtectedItem item:tuple.getProtectedItems() )
            {
                if ( item instanceof ProtectedItem.AttributeType || item instanceof ProtectedItem.AllAttributeValues
                    || item instanceof ProtectedItem.SelfValue || item instanceof ProtectedItem.AttributeValue )
                {
                    filteredTuples.add( tuple );
                    break;
                }
            }
        }

        if ( filteredTuples.size() > 0 )
        {
            return filteredTuples;
        }

        // If the protected item is an attribute value, and there are tuples
        // that specify the attribute value explicitly, discard all other tuples.
        // A protected item which is a rangeOfValues is to be treated as
        // specifying an attribute value explicitly. 
        for ( ACITuple tuple:tuples )
        {
            for ( ProtectedItem item:tuple.getProtectedItems() )
            {
                if ( item instanceof ProtectedItem.RangeOfValues )
                {
                    filteredTuples.add( tuple );
                }
            }
        }

        if ( filteredTuples.size() > 0 )
        {
            return filteredTuples;
        }

        return tuples;
    }
}
