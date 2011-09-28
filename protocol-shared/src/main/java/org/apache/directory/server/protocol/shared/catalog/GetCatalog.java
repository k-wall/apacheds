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

package org.apache.directory.server.protocol.shared.catalog;


import java.util.HashMap;
import java.util.Map;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.protocol.shared.store.DirectoryServiceOperation;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.DN;


/**
 * A Session operation for building a catalog.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $, $Date: 2010-03-24 19:39:54 +0100 (Mer, 24 mar 2010) $
 */
public class GetCatalog implements DirectoryServiceOperation
{
    private static final long serialVersionUID = -6657995003127926278L;


    /**
     * Note that the base is relative to the existing context.
     */
    public Object execute( CoreSession session, DN base ) throws Exception
    {
        String filter = "(objectClass=" + ApacheSchemaConstants.APACHE_CATALOG_ENTRY_OC + ")";

        EntryFilteringCursor list = session.search( 
            DN.EMPTY_DN, 
            SearchScope.SUBTREE, 
            FilterParser.parse( filter ), 
            AliasDerefMode.DEREF_ALWAYS,
            null );

        Map<String, String> catalog = new HashMap<String, String>();

        list.beforeFirst();
        
        while ( list.next() )
        {
            ServerEntry result = list.get();

            String name = null;
            EntryAttribute attribute = result.get( ApacheSchemaConstants.APACHE_CATALOGUE_ENTRY_NAME_AT );
            
            if ( attribute != null )
            {
                name = attribute.getString();
            }
            
            String basedn = null;
            attribute = result.get( ApacheSchemaConstants.APACHE_CATALOGUE_ENTRY_BASE_DN_AT );
            
            if ( attribute != null )
            {
                basedn = attribute.getString();
            }
            

            catalog.put( name, basedn );
        }

        return catalog;
    }
}
