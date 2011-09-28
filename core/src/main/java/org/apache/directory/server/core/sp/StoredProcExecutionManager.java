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
package org.apache.directory.server.core.sp;


import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.naming.directory.SearchControls;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.AttributeTypeOptions;


/**
 * A Factory type class which holds a registry of supported {@link StoredProcEngineConfig}s. A container reference
 * as the base for Stored Procedure storage on the DIT is also handled by this class.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$ $Date$
 */
public class StoredProcExecutionManager
{
    private final static Set<AttributeTypeOptions> EMPTY_ATTRIBS = Collections.emptySet();
    
    private final String storedProcContainer;

    private final List<StoredProcEngineConfig> storedProcEngineConfigs;


    /**
     * Creates a {@link StoredProcExecutionManager} instance.
     * 
     * @param storedProcContainer The base of the DIT subtree used for storing stored procedure units.
     * @param storedProcEngineConfigs A list of {@link StoredProcEngineConfig}s to register different {@link StoredProcEngine}s with this manager.
     */
    public StoredProcExecutionManager( final String storedProcContainer, final List<StoredProcEngineConfig> storedProcEngineConfigs )
    {
        this.storedProcContainer = storedProcContainer;
        this.storedProcEngineConfigs = storedProcEngineConfigs;
    }
    
    /**
     * Finds and returns a stored procedure unit entry whose identifier name
     * is extracted from fullSPName.
     * 
     * @param rootDSE A handle on the root DSE to be used for searching the SP Unit over.
     * @param fullSPName Full name of the Stored Procedure including the unit name.
     * @return The entry associated with the SP Unit.
     * @throws Exception If the unit cannot be located or any other error occurs.
     */
    public ClonedServerEntry findStoredProcUnit( CoreSession session, String fullSPName ) throws Exception
    {
        SearchControls controls = new SearchControls();
        controls.setReturningAttributes( SchemaConstants.ALL_USER_ATTRIBUTES_ARRAY );
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        String spUnitName = StoredProcUtils.extractStoredProcUnitName( fullSPName );
        
        AttributeType at = session.getDirectoryService()
            .getSchemaManager().lookupAttributeTypeRegistry( "storedProcUnitName" );
        ExprNode filter = new EqualityNode<String>( "storedProcUnitName", new StringValue( at, spUnitName ) );
        DN dn = new DN( storedProcContainer );
        EntryFilteringCursor results = session.search( dn, SearchScope.SUBTREE, filter, 
            AliasDerefMode.DEREF_ALWAYS, EMPTY_ATTRIBS );
        if ( results.first() )
        {
            ClonedServerEntry entry = results.get();
            results.close();
            return entry;
        }
        
        return null;
    }


    /**
     * Initializes and returns a {@link StoredProcEngine} instance which can operate on spUnitEntry
     * considering its specific stored procedure language.
     * 
     * @param spUnitEntry The entry which a {@link StoredProcEngine} type will be mathched with respect to the language identifier.
     * @return A {@link StoredProcEngine} associated with spUnitEntry.
     * @throws LdapException If no {@link StoredProcEngine} that can be associated with the language identifier in spUnitEntry can be found.
     */
    public StoredProcEngine getStoredProcEngineInstance( ClonedServerEntry spUnitEntry ) throws LdapException
    {
        String spLangId = ( String ) spUnitEntry.getOriginalEntry().get( "storedProcLangId" ).getString();

        for ( StoredProcEngineConfig engineConfig : storedProcEngineConfigs )
        {
            if ( engineConfig.getStoredProcLangId().equalsIgnoreCase( spLangId ) )
            {
                Class<? extends StoredProcEngine> engineType = engineConfig.getStoredProcEngineType();
                StoredProcEngine engine;
                
                try
                {
                    engine = engineType.newInstance();
                }
                catch ( InstantiationException e )
                {
                    LdapException ne = new LdapException( e.getMessage() );
                    ne.initCause( e );
                    throw ne;
                }
                catch ( IllegalAccessException e )
                {
                    LdapException ne = new LdapException( e.getMessage() );
                    ne.initCause( e );
                    throw ne;
                }
                
                engine.setSPUnitEntry( (ServerEntry)spUnitEntry.getOriginalEntry() );
                return engine;
            }

        }

        throw new LdapException( I18n.err( I18n.ERR_294, spLangId ) );

    }

}
