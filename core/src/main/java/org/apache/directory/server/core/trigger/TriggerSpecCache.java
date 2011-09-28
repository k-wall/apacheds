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
package org.apache.directory.server.core.trigger;


import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DefaultCoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.NormalizerMappingResolver;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.normalizers.OidNormalizer;
import org.apache.directory.shared.ldap.trigger.TriggerSpecification;
import org.apache.directory.shared.ldap.trigger.TriggerSpecificationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A cache for Trigger Specifications which responds to specific events to
 * perform cache house keeping as trigger subentries are added, deleted
 * and modified.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev:$
 */
public class TriggerSpecCache
{
    /** the attribute id for prescriptive trigger: prescriptiveTrigger */
    private static final String PRESCRIPTIVE_TRIGGER_ATTR = "prescriptiveTriggerSpecification";

    /** the logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( TriggerSpecCache.class );

    /** a map of strings to TriggerSpecification collections */
    private final Map<String, List<TriggerSpecification>> triggerSpecs = new HashMap<String, List<TriggerSpecification>>();
    /** a handle on the partition nexus */
    private final PartitionNexus nexus;
    /** a normalizing TriggerSpecification parser */
    private final TriggerSpecificationParser triggerSpecParser;


    /**
     * Creates a TriggerSpecification cache.
     *
     * @param directoryService the directory service core
     * @throws NamingException with problems initializing cache
     */
    public TriggerSpecCache( DirectoryService directoryService ) throws Exception
    {
        this.nexus = directoryService.getPartitionNexus();
        final SchemaManager schemaManager = directoryService.getSchemaManager();

        triggerSpecParser = new TriggerSpecificationParser( new NormalizerMappingResolver()
            {
                public Map<String, OidNormalizer> getNormalizerMapping() throws Exception
                {
                    return schemaManager.getNormalizerMapping();
                }
            });
        initialize( directoryService );
    }


    private void initialize( DirectoryService directoryService ) throws Exception
    {
        // search all naming contexts for trigger subentenries
        // generate TriggerSpecification arrays for each subentry
        // add that subentry to the hash
        Set<String> suffixes = nexus.listSuffixes( null );
        
        for ( String suffix:suffixes )
        {
            DN baseDn = new DN( suffix );
            ExprNode filter = new EqualityNode<String>( SchemaConstants.OBJECT_CLASS_AT, 
                    new StringValue( ApacheSchemaConstants.TRIGGER_EXECUTION_SUBENTRY_OC ) );
            SearchControls ctls = new SearchControls();
            ctls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            
            DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            adminDn.normalize( directoryService.getSchemaManager().getNormalizerMapping() );
            CoreSession adminSession = new DefaultCoreSession( 
                new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), directoryService );

            SearchOperationContext searchOperationContext = new SearchOperationContext( adminSession, baseDn,
                filter, ctls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.DEREF_ALWAYS );
            
            EntryFilteringCursor results = nexus.search( searchOperationContext );
            
            while ( results.next() )
            {
                ClonedServerEntry resultEntry = results.get();
                DN subentryDn = resultEntry.getDn();
                EntryAttribute triggerSpec = resultEntry.get( PRESCRIPTIVE_TRIGGER_ATTR );
                
                if ( triggerSpec == null )
                {
                    LOG.warn( "Found triggerExecutionSubentry '" + subentryDn + "' without any " + PRESCRIPTIVE_TRIGGER_ATTR );
                    continue;
                }

                DN normSubentryName = subentryDn.normalize( directoryService.getSchemaManager()
                    .getNormalizerMapping() );
                subentryAdded( normSubentryName, resultEntry );
            }
            
            results.close();
        }
    }


    private boolean hasPrescriptiveTrigger( ServerEntry entry ) throws Exception
    {
        // only do something if the entry contains prescriptiveTrigger
        EntryAttribute triggerSpec = entry.get( PRESCRIPTIVE_TRIGGER_ATTR );

        return triggerSpec != null;
    }


    public void subentryAdded( DN normName, ServerEntry entry ) throws Exception
    {
        // only do something if the entry contains prescriptiveTrigger
        EntryAttribute triggerSpec = entry.get( PRESCRIPTIVE_TRIGGER_ATTR );
        
        if ( triggerSpec == null )
        {
            return;
        }
        
        List<TriggerSpecification> subentryTriggerSpecs = new ArrayList<TriggerSpecification>();
        
        for ( Value<?> value:triggerSpec )
        {
            TriggerSpecification item = null;

            try
            {
                item = triggerSpecParser.parse( value.getString() );
                subentryTriggerSpecs.add( item );
            }
            catch ( ParseException e )
            {
                String msg = I18n.err( I18n.ERR_73, item );
                LOG.error( msg, e );
            }
            
        }
        
        triggerSpecs.put( normName.getNormName(), subentryTriggerSpecs );
    }


    public void subentryDeleted( DN normName, ServerEntry entry ) throws Exception
    {
        if ( !hasPrescriptiveTrigger( entry ) )
        {
            return;
        }

        triggerSpecs.remove( normName.toString() );
    }


    public void subentryModified( ModifyOperationContext opContext, ServerEntry entry ) throws Exception
    {
        if ( !hasPrescriptiveTrigger( entry ) )
        {
            return;
        }

        DN normName = opContext.getDn();
        List<Modification> mods = opContext.getModItems();

        boolean isTriggerSpecModified = false;

        for ( Modification mod : mods )
        {
            isTriggerSpecModified |= mod.getAttribute().contains( PRESCRIPTIVE_TRIGGER_ATTR );
        }
        
        if ( isTriggerSpecModified )
        {
            subentryDeleted( normName, entry );
            subentryAdded( normName, entry );
        }
    }


    public List<TriggerSpecification> getSubentryTriggerSpecs( String subentryDn )
    {
        List<TriggerSpecification> subentryTriggerSpecs = triggerSpecs.get( subentryDn );
        if ( subentryTriggerSpecs == null )
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList( subentryTriggerSpecs );
    }


    public void subentryRenamed( DN oldName, DN newName )
    {
        triggerSpecs.put( newName.getNormName(), triggerSpecs.remove( oldName.getNormName() ) );
    }
}
