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
package org.apache.directory.server.core.operational;


import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilter;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.DefaultServerAttribute;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.ServerModification;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.AVA;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 

/**
 * An {@link Interceptor} that adds or modifies the default attributes
 * of entries. There are four default attributes for now;
 * <tt>'creatorsName'</tt>, <tt>'createTimestamp'</tt>, <tt>'modifiersName'</tt>,
 * and <tt>'modifyTimestamp'</tt>.
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927839 $, $Date: 2010-03-26 14:25:10 +0100 (Ven, 26 mar 2010) $
 */
public class OperationalAttributeInterceptor extends BaseInterceptor
{
    /** The LoggerFactory used by this Interceptor */
    private static Logger LOG = LoggerFactory.getLogger( OperationalAttributeInterceptor.class );

    private final EntryFilter DENORMALIZING_SEARCH_FILTER = new EntryFilter()
    {
        public boolean accept( SearchingOperationContext operation, ClonedServerEntry serverEntry ) 
            throws Exception
        {
            if ( operation.getSearchControls().getReturningAttributes() == null )
            {
                return true;
            }
            
            return filterDenormalized( serverEntry );
        }
    };

    /**
     * the database search result filter to register with filter service
     */
    private final EntryFilter SEARCH_FILTER = new EntryFilter()
    {
        public boolean accept( SearchingOperationContext operation, ClonedServerEntry entry )
            throws Exception
        {
            return operation.getSearchControls().getReturningAttributes() != null 
                || filterOperationalAttributes( entry );
        }
    };


    private DirectoryService service;

    private DN subschemaSubentryDn;
    
    /** The schemaManager */
    private SchemaManager schemaManager;
    
    private static AttributeType CREATE_TIMESTAMP_ATTRIBUTE_TYPE;
    private static AttributeType MODIFIERS_NAME_ATTRIBUTE_TYPE;
    private static AttributeType MODIFY_TIMESTAMP_ATTRIBUTE_TYPE;


    /**
     * Creates the operational attribute management service interceptor.
     */
    public OperationalAttributeInterceptor()
    {
    }


    public void init( DirectoryService directoryService ) throws Exception
    {
        service = directoryService;
        schemaManager = directoryService.getSchemaManager();

        // stuff for dealing with subentries (garbage for now)
        Value<?> subschemaSubentry = service.getPartitionNexus()
                .getRootDSE( null ).get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).get();
        subschemaSubentryDn = new DN( subschemaSubentry.getString() );
        subschemaSubentryDn.normalize( schemaManager.getNormalizerMapping() );
        
        CREATE_TIMESTAMP_ATTRIBUTE_TYPE = schemaManager.lookupAttributeTypeRegistry( SchemaConstants.CREATE_TIMESTAMP_AT );
        MODIFIERS_NAME_ATTRIBUTE_TYPE = schemaManager.lookupAttributeTypeRegistry( SchemaConstants.MODIFIERS_NAME_AT );
        MODIFY_TIMESTAMP_ATTRIBUTE_TYPE = schemaManager.lookupAttributeTypeRegistry( SchemaConstants.MODIFY_TIMESTAMP_AT );
    }


    public void destroy()
    {
    }


    /**
     * Adds extra operational attributes to the entry before it is added.
     * 
     * We add those attributes :
     * - creatorsName
     * - createTimestamp
     * - entryCSN
     * - entryUUID 
     */
    public void add( NextInterceptor nextInterceptor, AddOperationContext opContext )
        throws Exception
    {
        String principal = getPrincipal().getName();
        
        ServerEntry entry = opContext.getEntry();

        /*
         * @TODO : This code was probably created while working on Mitosis. Most probably dead code. Commented. 
         * Check JIRA DIRSERVER-1416
        if ( opContext.getEntry().containsAttribute( CREATE_TIMESTAMP_ATTRIBUTE_TYPE ) )
        {
            // As we already have a CreateTimeStamp value in the context, use it, but only if
            // the principal is admin
            if ( opContext.getSession().getAuthenticatedPrincipal().getName().equals( 
                ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED ))
            {
                entry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            }
            else
            {
                String message = "The CreateTimeStamp attribute cannot be created by a user";
                LOG.error( message );
                throw new LdapSchemaViolationException( message, ResultCodeEnum.INSUFFICIENT_ACCESS_RIGHTS );
            }
        }
        else
        {
            entry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
        }
        */
        
        // Add the UUID and the entryCSN. The UUID is stored as a byte[] representation of 
        // its String value
        // @TODO : If we are using replication, those four OAs may be already present.
        // We have to deal with this as soon as we have the replication working again
        
        // Check that we don't have an entryUUID AT in the incoming entry, as it's a NO-USER-MODIFICATION AT
        // Of course, we will allow if for replication (see above @TODO)
        boolean isAdmin = opContext.getSession().getAuthenticatedPrincipal().getName().equals( 
            ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
        
        if ( entry.containsAttribute( SchemaConstants.ENTRY_UUID_AT ) )
        {
            if ( !isAdmin )
            {
                // Wrong !
                String message = I18n.err( I18n.ERR_30, SchemaConstants.ENTRY_UUID_AT );
                LOG.error( message );
                throw new LdapSchemaViolationException( ResultCodeEnum.INSUFFICIENT_ACCESS_RIGHTS, message );
            }
        }
        else
        {
            entry.put( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );
        }
            
        if ( entry.containsAttribute( SchemaConstants.ENTRY_CSN_AT ) )
        {
            if ( !isAdmin )
            {
                // Wrong !
                String message =  I18n.err( I18n.ERR_30, SchemaConstants.ENTRY_CSN_AT );
                LOG.error( message );
                throw new LdapSchemaViolationException( ResultCodeEnum.INSUFFICIENT_ACCESS_RIGHTS, message );
            }
        }
        else
        {
            entry.put( SchemaConstants.ENTRY_CSN_AT, service.getCSN().toString() );
        }
        
        entry.put( SchemaConstants.CREATORS_NAME_AT, principal );
        entry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
        
        nextInterceptor.add( opContext );
    }


    public void modify( NextInterceptor nextInterceptor, ModifyOperationContext opContext )
        throws Exception
    {
        // We must check that the user hasn't injected either the modifiersName
        // or the modifyTimestamp operational attributes : they are not supposed to be
        // added at this point.
        // If so, remove them, and if there are no more attributes, simply return.
        // otherwise, inject those values into the list of modifications
        List<Modification> mods = opContext.getModItems();
        
        for ( Modification modification: mods )
        {
            AttributeType attributeType = modification.getAttribute().getAttributeType();
            
            if ( attributeType.equals( MODIFIERS_NAME_ATTRIBUTE_TYPE ) )
            {
                String message = I18n.err( I18n.ERR_31 );
                LOG.error( message );
                throw new LdapSchemaViolationException( ResultCodeEnum.INSUFFICIENT_ACCESS_RIGHTS, message );
            }

            if ( attributeType.equals( MODIFY_TIMESTAMP_ATTRIBUTE_TYPE ) )
            {
                String message = I18n.err( I18n.ERR_32 );
                LOG.error( message );
                throw new LdapSchemaViolationException( ResultCodeEnum.INSUFFICIENT_ACCESS_RIGHTS, message );
            }
        }
        
        // Inject the ModifiersName AT if it's not present
        EntryAttribute attribute = new DefaultServerAttribute( 
            MODIFIERS_NAME_ATTRIBUTE_TYPE, 
            getPrincipal().getName());

        Modification modifiersName = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, attribute );

        mods.add( modifiersName );
        
        // Inject the ModifyTimestamp AT if it's not present
        attribute = new DefaultServerAttribute( 
            MODIFY_TIMESTAMP_ATTRIBUTE_TYPE,
            DateUtils.getGeneralizedTime() );
        
        Modification timestamp = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, attribute );

        mods.add( timestamp );
        
        // Go down in the chain
        nextInterceptor.modify( opContext );
        
        if ( opContext.getDn().getNormName().equals( subschemaSubentryDn.getNormName() ) ) 
        {
            return;
        }

        // -------------------------------------------------------------------
        // Add the operational attributes for the modifier first
        // -------------------------------------------------------------------
        // TODO : Why can't we add those elements on teh original modifications ???
        // Or into the context ?
        /*
        List<Modification> modItemList = new ArrayList<Modification>(2);
        
        AttributeType modifiersNameAt = atRegistry.lookup( SchemaConstants.MODIFIERS_NAME_AT );
        ServerAttribute attribute = new DefaultServerAttribute( 
            modifiersNameAt, 
            getPrincipal().getName());

        Modification modifiers = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, attribute );
        modItemList.add( modifiers );
        
        AttributeType modifyTimeStampAt = atRegistry.lookup( SchemaConstants.MODIFY_TIMESTAMP_AT );
        attribute = new DefaultServerAttribute( 
            modifyTimeStampAt,
            DateUtils.getGeneralizedTime() );
        
        Modification timestamp = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, attribute );
        modItemList.add( timestamp );

        // -------------------------------------------------------------------
        // Make the modify() call happen
        // -------------------------------------------------------------------
        ModifyOperationContext newModify = new ModifyOperationContext( opContext.getSession(), 
            opContext.getDn(), modItemList );
        newModify.setEntry( opContext.getAlteredEntry() );
        service.getPartitionNexus().modify( newModify );
        */
    }


    public void rename( NextInterceptor nextInterceptor, RenameOperationContext opContext )
        throws Exception
    {
        nextInterceptor.rename( opContext );

        DN newDn = opContext.getNewDn();
        
        // add operational attributes after call in case the operation fails
        ServerEntry serverEntry = new DefaultServerEntry( schemaManager, newDn );
        serverEntry.put( SchemaConstants.MODIFIERS_NAME_AT, getPrincipal().getName() );
        serverEntry.put( SchemaConstants.MODIFY_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );

        List<Modification> items = ModifyOperationContext.createModItems( serverEntry, ModificationOperation.REPLACE_ATTRIBUTE );

        ModifyOperationContext newModify = new ModifyOperationContext( opContext.getSession(), newDn, items );
        newModify.setEntry( opContext.getAlteredEntry() );
        
        service.getPartitionNexus().modify( newModify );
    }


    public void move( NextInterceptor nextInterceptor, MoveOperationContext opContext ) throws Exception
    {
        nextInterceptor.move( opContext );

        // add operational attributes after call in case the operation fails
        ServerEntry serverEntry = new DefaultServerEntry( schemaManager, opContext.getDn() );
        serverEntry.put( SchemaConstants.MODIFIERS_NAME_AT, getPrincipal().getName() );
        serverEntry.put( SchemaConstants.MODIFY_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );

        List<Modification> items = ModifyOperationContext.createModItems( serverEntry, ModificationOperation.REPLACE_ATTRIBUTE );


        ModifyOperationContext newModify = 
            new ModifyOperationContext( opContext.getSession(), opContext.getParent(), items );
        
        service.getPartitionNexus().modify( newModify );
    }


    public void moveAndRename( NextInterceptor nextInterceptor, MoveAndRenameOperationContext opContext )
        throws Exception
    {
        nextInterceptor.moveAndRename( opContext );

        // add operational attributes after call in case the operation fails
        ServerEntry serverEntry = new DefaultServerEntry( schemaManager, opContext.getDn() );
        serverEntry.put( SchemaConstants.MODIFIERS_NAME_AT, getPrincipal().getName() );
        serverEntry.put( SchemaConstants.MODIFY_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );

        List<Modification> items = ModifyOperationContext.createModItems( serverEntry, ModificationOperation.REPLACE_ATTRIBUTE );

        ModifyOperationContext newModify = 
            new ModifyOperationContext( opContext.getSession(), opContext.getParent(), items );
        
        service.getPartitionNexus().modify( newModify );
    }


    public ClonedServerEntry lookup( NextInterceptor nextInterceptor, LookupOperationContext opContext ) throws Exception
    {
        ClonedServerEntry result = nextInterceptor.lookup( opContext );
        
        if ( result == null )
        {
            return null;
        }

        if ( opContext.getAttrsId() == null )
        {
            filterOperationalAttributes( result );
        }
        else if ( ( opContext.getAllOperational() == null ) || ( opContext.getAllOperational() == false ) )
        {
            filter( opContext, result );
        }
        
        denormalizeEntryOpAttrs( result );
        return result;
    }


    public EntryFilteringCursor list( NextInterceptor nextInterceptor, ListOperationContext opContext ) throws Exception
    {
        EntryFilteringCursor cursor = nextInterceptor.list( opContext );
        cursor.addEntryFilter( SEARCH_FILTER );
        return cursor;
    }


    public EntryFilteringCursor search( NextInterceptor nextInterceptor, SearchOperationContext opContext ) throws Exception
    {
        EntryFilteringCursor cursor = nextInterceptor.search( opContext );
        
        if ( opContext.isAllOperationalAttributes() || 
             ( opContext.getReturningAttributes() != null && ! opContext.getReturningAttributes().isEmpty() ) )
        {
            if ( service.isDenormalizeOpAttrsEnabled() )
            {
                cursor.addEntryFilter( DENORMALIZING_SEARCH_FILTER );
            }
                
            return cursor;
        }

        cursor.addEntryFilter( SEARCH_FILTER );
        return cursor;
    }


    /**
     * Filters out the operational attributes within a search results attributes.  The attributes are directly
     * modified.
     *
     * @param attributes the resultant attributes to filter
     * @return true always
     * @throws Exception if there are failures in evaluation
     */
    private boolean filterOperationalAttributes( ServerEntry attributes ) throws Exception
    {
        Set<AttributeType> removedAttributes = new HashSet<AttributeType>();

        // Build a list of attributeType to remove
        for ( AttributeType attributeType:attributes.getAttributeTypes() )
        {
            if ( attributeType.getUsage() != UsageEnum.USER_APPLICATIONS )
            {
                removedAttributes.add( attributeType );
            }
        }
        
        // Now remove the attributes which are not USERs
        for ( AttributeType attributeType:removedAttributes )
        {
            attributes.removeAttributes( attributeType );
        }
        
        return true;
    }


    private void filter( LookupOperationContext lookupContext, ServerEntry entry ) throws Exception
    {
        DN dn = lookupContext.getDn();
        List<String> ids = lookupContext.getAttrsId();
        
        // still need to protect against returning op attrs when ids is null
        if ( ids == null || ids.isEmpty() )
        {
            filterOperationalAttributes( entry );
            return;
        }

        Set<AttributeType> attributeTypes = entry.getAttributeTypes();

        if ( dn.size() == 0 )
        {
            for ( AttributeType attributeType:attributeTypes )
            {
                if ( !ids.contains( attributeType.getOid() ) )
                {
                    entry.removeAttributes( attributeType );
                }
            }
        }

        denormalizeEntryOpAttrs( entry );
        
        // do nothing past here since this explicity specifies which
        // attributes to include - backends will automatically populate
        // with right set of attributes using ids array
    }

    
    public void denormalizeEntryOpAttrs( ServerEntry entry ) throws Exception
    {
        if ( service.isDenormalizeOpAttrsEnabled() )
        {
            EntryAttribute attr = entry.get( SchemaConstants.CREATORS_NAME_AT );

            if ( attr != null )
            {
                DN creatorsName = new DN( attr.getString() );
                
                attr.clear();
                attr.add( denormalizeTypes( creatorsName ).getName() );
            }
            
            attr = entry.get( SchemaConstants.MODIFIERS_NAME_AT );
            
            if ( attr != null )
            {
                DN modifiersName = new DN( attr.getString() );

                attr.clear();
                attr.add( denormalizeTypes( modifiersName ).getName() );
            }

            attr = entry.get( ApacheSchemaConstants.SCHEMA_MODIFIERS_NAME_AT );
            
            if ( attr != null )
            {
                DN modifiersName = new DN( attr.getString() );

                attr.clear();
                attr.add( denormalizeTypes( modifiersName ).getName() );
            }
        }
    }

    
    /**
     * Does not create a new DN but alters existing DN by using the first
     * short name for an attributeType definition.
     * 
     * @param dn the normalized distinguished name
     * @return the distinuished name denormalized
     * @throws Exception if there are problems denormalizing
     */
    public DN denormalizeTypes( DN dn ) throws Exception
    {
        DN newDn = new DN();
        
        for ( int ii = 0; ii < dn.size(); ii++ )
        {
            RDN rdn = dn.getRdn( ii );
            if ( rdn.size() == 0 )
            {
                newDn.add( new RDN() );
                continue;
            }
            else if ( rdn.size() == 1 )
            {
                String name = schemaManager.lookupAttributeTypeRegistry( rdn.getNormType() ).getName();
                String value = rdn.getAtav().getNormValue().getString(); 
                newDn.add( new RDN( name, name, value, value ) );
                continue;
            }

            // below we only process multi-valued rdns
            StringBuffer buf = new StringBuffer();
        
            for ( Iterator<AVA> atavs = rdn.iterator(); atavs.hasNext(); /**/ )
            {
                AVA atav = atavs.next();
                String type = schemaManager.lookupAttributeTypeRegistry( rdn.getNormType() ).getName();
                buf.append( type ).append( '=' ).append( atav.getNormValue() );
                
                if ( atavs.hasNext() )
                {
                    buf.append( '+' );
                }
            }
            
            newDn.add( new RDN(buf.toString()) );
        }
        
        return newDn;
    }


    private boolean filterDenormalized( ServerEntry entry ) throws Exception
    {
        denormalizeEntryOpAttrs( entry );
        return true;
    }
}