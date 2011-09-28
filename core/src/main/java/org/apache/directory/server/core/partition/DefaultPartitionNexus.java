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
package org.apache.directory.server.core.partition;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import javax.naming.ConfigurationException;
import javax.naming.NameNotFoundException;
import javax.naming.directory.SearchControls;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DefaultCoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.BaseEntryFilteringCursor;
import org.apache.directory.server.core.filtering.CursorList;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.AddContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.CompareOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.GetMatchedNameOperationContext;
import org.apache.directory.server.core.interceptor.context.GetRootDSEOperationContext;
import org.apache.directory.server.core.interceptor.context.GetSuffixOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.ListSuffixOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RemoveContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.MultiException;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.codec.controls.CascadeControl;
import org.apache.directory.shared.ldap.codec.controls.ManageDsaITControl;
import org.apache.directory.shared.ldap.codec.controls.replication.syncDoneValue.SyncDoneValueControl;
import org.apache.directory.shared.ldap.codec.controls.replication.syncInfoValue.SyncInfoValueControl;
import org.apache.directory.shared.ldap.codec.controls.replication.syncRequestValue.SyncRequestValueControl;
import org.apache.directory.shared.ldap.codec.controls.replication.syncStateValue.SyncStateValueControl;
import org.apache.directory.shared.ldap.codec.search.controls.entryChange.EntryChangeControl;
import org.apache.directory.shared.ldap.codec.search.controls.pagedSearch.PagedResultsControl;
import org.apache.directory.shared.ldap.codec.search.controls.persistentSearch.PersistentSearchControl;
import org.apache.directory.shared.ldap.codec.search.controls.subentries.SubentriesControl;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.cursor.EmptyCursor;
import org.apache.directory.shared.ldap.cursor.SingletonCursor;
import org.apache.directory.shared.ldap.entry.DefaultServerAttribute;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeTypeException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchObjectException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.extended.NoticeOfDisconnect;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.UsageEnum;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.ldap.util.NamespaceTools;
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.directory.shared.ldap.util.tree.DnBranchNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A root {@link Partition} that contains all other partitions, and
 * routes all operations to the child partition that matches to its base suffixes.
 * It also provides some extended operations such as accessing rootDSE and
 * listing base suffixes.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927404 $, $Date: 2010-03-25 14:55:18 +0100 (Jeu, 25 mar 2010) $
 */
public class DefaultPartitionNexus  extends AbstractPartition implements PartitionNexus
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultPartitionNexus.class );

    /** Speedup for logs */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** the vendorName string proudly set to: Apache Software Foundation*/
    private static final String ASF = "Apache Software Foundation";

    /** the read only rootDSE attributes */
    private final ServerEntry rootDSE;

    /** The DirectoryService instance */
    private DirectoryService directoryService;
    
    /** The global schemaManager */
    private SchemaManager schemaManager;
    
    /** the partitions keyed by normalized suffix strings */
    private Map<String, Partition> partitions = new HashMap<String, Partition>();
    
    /** A structure to hold all the partitions */
    private DnBranchNode<Partition> partitionLookupTree = new DnBranchNode<Partition>();
    
    /** the system partition */
    private Partition system;

    /** the closed state of this partition */
    private boolean initialized;
    
   
    /**
     * Creates the root nexus singleton of the entire system.  The root DSE has
     * several attributes that are injected into it besides those that may
     * already exist.  As partitions are added to the system more namingContexts
     * attributes are added to the rootDSE.
     *
     * @see <a href="http://www.faqs.org/rfcs/rfc3045.html">Vendor Information</a>
     * @param rootDSE the root entry for the DSA
     * @throws javax.naming.Exception on failure to initialize
     */
    public DefaultPartitionNexus( ServerEntry rootDSE ) throws Exception
    {
        // setup that root DSE
        this.rootDSE = rootDSE;
        
        // Add the basic informations
        rootDSE.put( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, ServerDNConstants.CN_SCHEMA_DN );
        rootDSE.put( SchemaConstants.SUPPORTED_LDAP_VERSION_AT, "3" );
        rootDSE.put( SchemaConstants.SUPPORTED_FEATURES_AT, SchemaConstants.FEATURE_ALL_OPERATIONAL_ATTRIBUTES );
        rootDSE.put( SchemaConstants.SUPPORTED_EXTENSION_AT, NoticeOfDisconnect.EXTENSION_OID );

        // Add the supported controls
        rootDSE.put( 
            SchemaConstants.SUPPORTED_CONTROL_AT, 
            PersistentSearchControl.CONTROL_OID,
            EntryChangeControl.CONTROL_OID,
            SubentriesControl.CONTROL_OID,
            ManageDsaITControl.CONTROL_OID,
            CascadeControl.CONTROL_OID,
            PagedResultsControl.CONTROL_OID,
            // Replication controls
            SyncDoneValueControl.CONTROL_OID,
            SyncInfoValueControl.CONTROL_OID,
            SyncRequestValueControl.CONTROL_OID,
            SyncStateValueControl.CONTROL_OID 
        );

        // Add the objectClasses
        rootDSE.put( SchemaConstants.OBJECT_CLASS_AT,
            SchemaConstants.TOP_OC,
            SchemaConstants.EXTENSIBLE_OBJECT_OC );

        // Add the 'vendor' name and version infos
        rootDSE.put( SchemaConstants.VENDOR_NAME_AT, ASF );

        Properties props = new Properties();
        
        try
        {
            props.load( getClass().getResourceAsStream( "version.properties" ) );
        }
        catch ( IOException e )
        {
            LOG.error( I18n.err( I18n.ERR_33 ) );
        }

        rootDSE.put( SchemaConstants.VENDOR_VERSION_AT, props.getProperty( "apacheds.version", "UNKNOWN" ) );
    }

    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#initialize()
     */
    protected void doInit( ) throws Exception
    {
        // NOTE: We ignore ContextPartitionConfiguration parameter here.
        if ( initialized )
        {
            return;
        }
    
        //this.directoryService = directoryService;
        schemaManager = directoryService.getSchemaManager();
        
        // Initialize and normalize the localy used DNs
        DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN );
        adminDn.normalize( schemaManager.getNormalizerMapping() );
            
        initializeSystemPartition( directoryService );
        
        List<Partition> initializedPartitions = new ArrayList<Partition>();
        initializedPartitions.add( 0, this.system );

    
        try
        {
            for ( Partition partition : directoryService.getPartitions() )
            {
                partition.setSchemaManager( schemaManager );
                CoreSession adminSession = new DefaultCoreSession( 
                    new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), directoryService );
    
                AddContextPartitionOperationContext opCtx = 
                    new AddContextPartitionOperationContext( adminSession, partition );
                addContextPartition( opCtx );
                initializedPartitions.add( opCtx.getPartition() );
            }
            
            initialized = true;
        }
        finally
        {
            if ( !initialized )
            {
                Iterator<Partition> i = initializedPartitions.iterator();
                while ( i.hasNext() )
                {
                    Partition partition = i.next();
                    i.remove();
                    try
                    {
                        partition.destroy();
                    }
                    catch ( Exception e )
                    {
                        LOG.warn( "Failed to destroy a partition: " + partition.getSuffixDn(), e );
                    }
                    finally
                    {
                        unregister( partition );
                    }
                }
            }
        }
    }


    private Partition initializeSystemPartition( DirectoryService directoryService ) throws Exception
    {
        // initialize system partition first
        Partition override = directoryService.getSystemPartition();
        
        if ( override != null )
        {
            
            // ---------------------------------------------------------------
            // check a few things to make sure users configured it properly
            // ---------------------------------------------------------------

            if ( ! override.getId().equals( "system" ) )
            {
                throw new ConfigurationException( I18n.err( I18n.ERR_262, override.getId() ) );
            }
            

            system = override;
        }
        else
        {
        }

        system.initialize( );
        
        
        // Add root context entry for system partition
        DN systemSuffixDn = new DN( ServerDNConstants.SYSTEM_DN );
        systemSuffixDn.normalize( schemaManager.getNormalizerMapping() );
        ServerEntry systemEntry = new DefaultServerEntry( schemaManager, systemSuffixDn );

        // Add the ObjectClasses
        systemEntry.put( SchemaConstants.OBJECT_CLASS_AT,
            SchemaConstants.TOP_OC,
            SchemaConstants.ORGANIZATIONAL_UNIT_OC,
            SchemaConstants.EXTENSIBLE_OBJECT_OC
            );
        
        // Add some operational attributes
        systemEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN );
        systemEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
        systemEntry.add( SchemaConstants.ENTRY_CSN_AT, directoryService.getCSN().toString() );
        systemEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );
        systemEntry.put( NamespaceTools.getRdnAttribute( ServerDNConstants.SYSTEM_DN ),
            NamespaceTools.getRdnValue( ServerDNConstants.SYSTEM_DN ) );
        DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
        adminDn.normalize( schemaManager.getNormalizerMapping() );
        CoreSession adminSession = new DefaultCoreSession( 
            new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), directoryService );
        AddOperationContext addOperationContext = new AddOperationContext( adminSession, systemEntry );
        
        if ( !system.hasEntry( new EntryOperationContext( adminSession, systemEntry.getDn() ) ) )
        {
            system.add( addOperationContext );
        }
        
        String key = system.getSuffixDn().getName();
        
        if ( partitions.containsKey( key ) )
        {
            throw new ConfigurationException( I18n.err( I18n.ERR_263, key ) );
        }
        
        synchronized ( partitionLookupTree )
        {
            partitions.put( key, system );
            partitionLookupTree.add( system.getSuffixDn(), system );
            EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );
            
            if ( namingContexts == null )
            {
                namingContexts = new DefaultServerAttribute( 
                    schemaManager.lookupAttributeTypeRegistry( SchemaConstants.NAMING_CONTEXTS_AT ), 
                    system.getSuffixDn().getName() );
                rootDSE.put( namingContexts );
            }
            else
            {
                namingContexts.add( system.getSuffixDn().getName() );
            }
        }

        return system;
    }
    
    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#destroy()
     */
    protected synchronized void doDestroy()
    {
        if ( !initialized )
        {
            return;
        }

        // make sure this loop is not fail fast so all backing stores can
        // have an attempt at closing down and synching their cached entries
        for ( String suffix : new HashSet<String>( this.partitions.keySet() ) )
        {
            try
            {
                DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
                adminDn.normalize( schemaManager.getNormalizerMapping() );
                CoreSession adminSession = new DefaultCoreSession( 
                    new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), directoryService );
                removeContextPartition( new RemoveContextPartitionOperationContext( 
                    adminSession, new DN( suffix ) ) );
            }
            catch ( Exception e )
            {
                LOG.warn( "Failed to destroy a partition: " + suffix, e );
            }
        }

        initialized = false;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getId()
     */
    public String getId()
    {
        return "NEXUS";
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#setId(java.lang.String)
     */
    public void setId( String id )
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_264 ) );
    }


    /**
     * {@inheritDoc}
     */
    public SchemaManager getSchemaManager()
    {
        return schemaManager;
    }
    

    /**
     * {@inheritDoc}
     */
    public void setSchemaManager( SchemaManager schemaManager )
    {
        this.schemaManager = schemaManager;
    }
    
    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getSuffixDn()
     */
    public DN getSuffixDn()
    {
        return DN.EMPTY_DN;
    }

    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getSuffix()
     */
    public String getSuffix()
    {
        return StringTools.EMPTY;
    }
    
    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#setSuffix(java.lang.String)
     */
    public void setSuffix( String suffix )
    {
        throw new UnsupportedOperationException();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#isInitialized()
     */
    public boolean isInitialized()
    {
        return initialized;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#sync()
     */
    public void sync() throws Exception
    {
        MultiException error = null;

        for ( Partition partition : this.partitions.values() )
        {
            try
            {
                partition.sync();
            }
            catch ( Exception e )
            {
                LOG.warn( "Failed to flush partition data out.", e );
                if ( error == null )
                {
                    //noinspection ThrowableInstanceNeverThrown
                    error = new MultiException( I18n.err( I18n.ERR_265 ) );
                }

                // @todo really need to send this info to a monitor
                error.addThrowable( e );
            }
        }

        if ( error != null )
        {
            throw error;
        }
    }
    
    // ------------------------------------------------------------------------
    // DirectoryPartition Interface Method Implementations
    // ------------------------------------------------------------------------
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#add(org.apache.directory.server.core.interceptor.context.AddOperationContext)
     */
    public void add( AddOperationContext addContext ) throws Exception
    {
        Partition backend = getPartition( addContext.getDn() );
        backend.add( addContext );
    }
    
    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#bind(org.apache.directory.server.core.interceptor.context.BindOperationContext)
     */
    public void bind( BindOperationContext bindContext ) throws Exception
    {
        Partition partition = getPartition( bindContext.getDn() );
        partition.bind( bindContext );
    }

    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#compare(org.apache.directory.server.core.interceptor.context.CompareOperationContext)
     */
    public boolean compare( CompareOperationContext compareContext ) throws Exception
    {
        Partition partition = getPartition( compareContext.getDn() );
        //AttributeTypeRegistry registry = schemaManager.getAttributeTypeRegistry();
        
        // complain if we do not recognize the attribute being compared
        if ( !schemaManager.getAttributeTypeRegistry().contains( compareContext.getOid() ) )
        {
            throw new LdapInvalidAttributeTypeException( I18n.err( I18n.ERR_266, compareContext.getOid() ) );
        }

        AttributeType attrType = schemaManager.lookupAttributeTypeRegistry( compareContext.getOid() );
        
        EntryAttribute attr = partition.lookup( compareContext.newLookupContext( 
            compareContext.getDn() ) ).get( attrType.getName() );

        // complain if the attribute being compared does not exist in the entry
        if ( attr == null )
        {
            throw new LdapNoSuchAttributeException();
        }

        // see first if simple match without normalization succeeds
        if ( attr.contains( (Value<?>)compareContext.getValue()  ) )
        {
            return true;
        }

        // now must apply normalization to all values (attr and in request) to compare

        /*
         * Get ahold of the normalizer for the attribute and normalize the request
         * assertion value for comparisons with normalized attribute values.  Loop
         * through all values looking for a match.
         */
        Normalizer normalizer = attrType.getEquality().getNormalizer();
        Value<?> reqVal = normalizer.normalize( compareContext.getValue() );

        for ( Value<?> value:attr )
        {
            Value<?> attrValObj = normalizer.normalize( value );
            
            if ( attrValObj.equals( reqVal ) )
            {
                return true;
            }
        }

        return false;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#delete(org.apache.directory.server.core.interceptor.context.DeleteOperationContext)
     */
    public void delete( DeleteOperationContext deleteContext ) throws Exception
    {
        Partition backend = getPartition( deleteContext.getDn() );
        backend.delete( deleteContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#hasEntry(org.apache.directory.server.core.interceptor.context.EntryOperationContext)
     */
    public boolean hasEntry( EntryOperationContext opContext ) throws Exception
    {
        DN dn = opContext.getDn();
        
        if ( IS_DEBUG )
        {
            LOG.debug( "Check if DN '" + dn + "' exists." );
        }

        if ( dn.size() == 0 )
        {
            return true;
        }

        Partition backend = getPartition( dn );
        return backend.hasEntry( opContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#list(org.apache.directory.server.core.interceptor.context.ListOperationContext)
     */
    public EntryFilteringCursor list( ListOperationContext opContext ) throws Exception
    {
        Partition backend = getPartition( opContext.getDn() );
        return backend.list( opContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#lookup(org.apache.directory.server.core.interceptor.context.LookupOperationContext)
     */
    public ClonedServerEntry lookup( LookupOperationContext opContext ) throws Exception
    {
        DN dn = opContext.getDn();
        
        if ( dn.size() == 0 )
        {
            ClonedServerEntry retval = new ClonedServerEntry( rootDSE );
            Set<AttributeType> attributeTypes = rootDSE.getAttributeTypes();
     
            if ( opContext.getAttrsId() != null && ! opContext.getAttrsId().isEmpty() )
            {
                for ( AttributeType attributeType:attributeTypes )
                {
                    String oid = attributeType.getOid();
                    
                    if ( ! opContext.getAttrsId().contains( oid ) )
                    {
                        retval.removeAttributes( attributeType );
                    }
                }
                return retval;
            }
            else
            {
                return new ClonedServerEntry( rootDSE );
            }
        }

        Partition backend = getPartition( dn );
        return backend.lookup( opContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#lookup(java.lang.Long)
     */
    public ClonedServerEntry lookup( Long id ) throws Exception
    {
        // TODO not implemented until we can use id to figure out the partition using
        // the partition ID component of the 64 bit Long identifier
        throw new NotImplementedException();
    }

    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#modify(org.apache.directory.server.core.interceptor.context.ModifyOperationContext)
     */
    public void modify( ModifyOperationContext modifyContext ) throws Exception
    {
        // Special case : if we don't have any modification to apply, just return
        if ( modifyContext.getModItems().size() == 0 )
        {
            return;
        }
        
        Partition backend = getPartition( modifyContext.getDn() );
        backend.modify( modifyContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#move(org.apache.directory.server.core.interceptor.context.MoveOperationContext)
     */
    public void move( MoveOperationContext opContext ) throws Exception
    {
        Partition backend = getPartition( opContext.getDn() );
        backend.move( opContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#moveAndRename(org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext)
     */
    public void moveAndRename( MoveAndRenameOperationContext opContext ) throws Exception
    {
        Partition backend = getPartition( opContext.getDn() );
        backend.moveAndRename( opContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#rename(org.apache.directory.server.core.interceptor.context.RenameOperationContext)
     */
    public void rename( RenameOperationContext opContext ) throws Exception
    {
        Partition backend = getPartition( opContext.getDn() );
        backend.rename( opContext );
    }

    
    private EntryFilteringCursor searchRootDSE( SearchOperationContext searchOperationContext ) throws Exception
    {
        SearchControls searchControls = searchOperationContext.getSearchControls();
        
        String[] ids = searchControls.getReturningAttributes();

        // -----------------------------------------------------------
        // If nothing is asked for then we just return the entry asis.
        // We let other mechanisms filter out operational attributes.
        // -----------------------------------------------------------
        if ( ( ids == null ) || ( ids.length == 0 ) )
        {
            ServerEntry rootDSE = (ServerEntry)getRootDSE( null ).clone();
            return new BaseEntryFilteringCursor( new SingletonCursor<ServerEntry>( rootDSE ), searchOperationContext );
        }
        
        // -----------------------------------------------------------
        // Collect all the real attributes besides 1.1, +, and * and
        // note if we've seen these special attributes as well.
        // -----------------------------------------------------------

        Set<String> realIds = new HashSet<String>();
        boolean allUserAttributes = searchOperationContext.isAllUserAttributes();
        boolean allOperationalAttributes = searchOperationContext.isAllOperationalAttributes();
        boolean noAttribute = searchOperationContext.isNoAttributes();

        for ( String id:ids )
        {
            String idTrimmed = id.trim();
            
            try
            {
                realIds.add( schemaManager.getAttributeTypeRegistry().getOidByName( idTrimmed ) );
            }
            catch ( Exception e )
            {
                realIds.add( idTrimmed );
            }
        }

        // return nothing
        if ( noAttribute )
        {
            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, DN.EMPTY_DN );
            return new BaseEntryFilteringCursor( new SingletonCursor<ServerEntry>( serverEntry ), searchOperationContext );
        }
        
        // return everything
        if ( allUserAttributes && allOperationalAttributes )
        {
            ServerEntry rootDSE = (ServerEntry)getRootDSE( null ).clone();
            return new BaseEntryFilteringCursor( new SingletonCursor<ServerEntry>( rootDSE ), searchOperationContext );
        }
        
        ServerEntry serverEntry = new DefaultServerEntry( schemaManager, DN.EMPTY_DN );
        
        ServerEntry rootDSE = getRootDSE( new GetRootDSEOperationContext( searchOperationContext.getSession() ) );
        
        for ( EntryAttribute attribute:rootDSE )
        {
            AttributeType type = schemaManager.lookupAttributeTypeRegistry( attribute.getUpId() );
            
            if ( realIds.contains( type.getOid() ) )
            {
                serverEntry.put( attribute );
            }
            else if ( allUserAttributes && ( type.getUsage() == UsageEnum.USER_APPLICATIONS ) )
            {
                serverEntry.put( attribute );
            }
            else if ( allOperationalAttributes && ( type.getUsage() != UsageEnum.USER_APPLICATIONS ) )
            {
                serverEntry.put( attribute );
            }
        }

        return new BaseEntryFilteringCursor( new SingletonCursor<ServerEntry>( serverEntry ), searchOperationContext );
    }
    

    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#search(org.apache.directory.server.core.interceptor.context.SearchOperationContext)
     */
    public EntryFilteringCursor search( SearchOperationContext opContext ) throws Exception
    {
        DN base = opContext.getDn();
        SearchControls searchCtls = opContext.getSearchControls();
        ExprNode filter = opContext.getFilter();
        
        // TODO since we're handling the *, and + in the EntryFilteringCursor
        // we may not need this code: we need see if this is actually the 
        // case and remove this code.
        if ( base.size() == 0 )
        {
            // We are searching from the rootDSE. We have to distinguish three cases :
            // 1) The scope is OBJECT : we have to return the rootDSE entry, filtered
            // 2) The scope is ONELEVEL : we have to return all the Namin
            boolean isObjectScope = searchCtls.getSearchScope() == SearchControls.OBJECT_SCOPE;
            
            boolean isOnelevelScope = searchCtls.getSearchScope() == SearchControls.ONELEVEL_SCOPE;
            
            boolean isSublevelScope = searchCtls.getSearchScope() == SearchControls.SUBTREE_SCOPE;
            
            // test for (objectClass=*)
            boolean isSearchAll = false;
            
            // We have to be careful, as we may have a filter which is not a PresenceFilter
            if ( filter instanceof PresenceNode )
            {
                isSearchAll = ( ( PresenceNode ) filter ).getAttribute().equals( SchemaConstants.OBJECT_CLASS_AT_OID );
            }
    
            /*
             * if basedn is "", filter is "(objectclass=*)" and scope is object
             * then we have a request for the rootDSE
             */
            if ( ( filter instanceof PresenceNode)  && isObjectScope && isSearchAll )
            {
                return searchRootDSE( opContext );
            }
            else if ( isObjectScope && ( ! isSearchAll ) )
            {
                return new BaseEntryFilteringCursor( new EmptyCursor<ServerEntry>(), opContext );
            }
            else if( isOnelevelScope )
            {
                List<EntryFilteringCursor> cursors = new ArrayList<EntryFilteringCursor>();
                for ( Partition p : partitions.values() )
                {
                    opContext.setDn( p.getSuffixDn() );
                    opContext.setScope( SearchScope.OBJECT );
                    cursors.add( p.search( opContext ) );
                }
                
                return new CursorList( cursors, opContext );
            }
            else if ( isSublevelScope )
            {
                List<EntryFilteringCursor> cursors = new ArrayList<EntryFilteringCursor>();
                for ( Partition p : partitions.values() )
                {
                    ClonedServerEntry entry = p.lookup( new LookupOperationContext( directoryService.getAdminSession(), p.getSuffixDn() ) );
                    if( entry != null )
                    {
                        Partition backend = getPartition( entry.getDn() );
                        opContext.setDn( entry.getDn() );
                        cursors.add( backend.search( opContext ) );
                    }
                }
                
                // don't feed the above Cursors' list to a BaseEntryFilteringCursor it is skipping the naming context entry of each partition 
                return new CursorList( cursors, opContext );
            }
    
            // TODO : handle searches based on the RootDSE
            throw new LdapNoSuchObjectException();
        }
    
        base.normalize( schemaManager.getNormalizerMapping() );
        Partition backend = getPartition( base );
        return backend.search( opContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#unbind(org.apache.directory.server.core.interceptor.context.UnbindOperationContext)
     */
    public void unbind( UnbindOperationContext unbindContext ) throws Exception
    {
        Partition partition = getPartition( unbindContext.getDn() );
        partition.unbind( unbindContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getRootDSE(org.apache.directory.server.core.interceptor.context.GetRootDSEOperationContext)
     */
    public ClonedServerEntry getRootDSE( GetRootDSEOperationContext getRootDSEContext )
    {
        return new ClonedServerEntry( rootDSE );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#addContextPartition(org.apache.directory.server.core.interceptor.context.AddContextPartitionOperationContext)
     */
    public synchronized void addContextPartition( AddContextPartitionOperationContext opContext ) throws Exception
    {
        Partition partition = opContext.getPartition();

        // Turn on default indices
        String key = partition.getSuffixDn().getNormName();
        
        if ( partitions.containsKey( key ) )
        {
            throw new ConfigurationException( I18n.err( I18n.ERR_263, key ) );
        }

        if ( ! partition.isInitialized() )
        {
            partition.initialize( );
        }
        
        synchronized ( partitionLookupTree )
        {
            DN partitionSuffix = partition.getSuffixDn();
            
            if ( partitionSuffix == null )
            {
                throw new ConfigurationException( I18n.err( I18n.ERR_267, partition.getId() ) );
            }
            
            partitions.put( partitionSuffix.getNormName(), partition );
            partitionLookupTree.add( partition.getSuffixDn(), partition );

            EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );

            if ( namingContexts == null )
            {
                namingContexts = new DefaultServerAttribute( 
                    schemaManager.lookupAttributeTypeRegistry( SchemaConstants.NAMING_CONTEXTS_AT ), partitionSuffix.getName() );
                rootDSE.put( namingContexts );
            }
            else
            {
                namingContexts.add( partitionSuffix.getName() );
            }
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#removeContextPartition(org.apache.directory.server.core.interceptor.context.RemoveContextPartitionOperationContext)
     */
    public synchronized void removeContextPartition( RemoveContextPartitionOperationContext removeContextPartition ) throws Exception
    {
        // Get the Partition name. It's a DN.
        String key = removeContextPartition.getDn().getNormName();
        
        // Retrieve this partition from the aprtition's table
        Partition partition = partitions.get( key );
        
        if ( partition == null )
        {
            String msg = I18n.err( I18n.ERR_34, key );
            LOG.error( msg );
            throw new NameNotFoundException( msg );
        }
        
        String partitionSuffix = partition.getSuffixDn().getName();

        // Retrieve the namingContexts from the RootDSE : the partition
        // suffix must be present in those namingContexts
        EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );
        
        if ( namingContexts != null )
        {
            if ( namingContexts.contains( partitionSuffix ) )
            {
                namingContexts.remove( partitionSuffix );
            }
            else
            {
                String msg = I18n.err( I18n.ERR_35, key );
                LOG.error( msg );
                throw new NameNotFoundException( msg );
            }
        }

        // Update the partition tree
        partitionLookupTree.remove( partition );
        partitions.remove( key );
        partition.destroy();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getSystemPartition()
     */
    public Partition getSystemPartition()
    {
        return system;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getPartition(org.apache.directory.shared.ldap.name.DN)
     */
    public Partition getPartition( DN dn ) throws Exception
    {
        Partition parent = partitionLookupTree.getParentElement( dn );
        
        if ( parent == null )
        {
            throw new LdapNoSuchObjectException( I18n.err( I18n.ERR_268, dn ) );
        }
        else
        {
            return parent;
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getMatchedName(org.apache.directory.server.core.interceptor.context.GetMatchedNameOperationContext)
     */
    public DN getMatchedName( GetMatchedNameOperationContext matchedNameContext ) throws Exception
    {
        DN dn = ( DN ) matchedNameContext.getDn().clone();
        
        while ( dn.size() > 0 )
        {
            if ( hasEntry( new EntryOperationContext( matchedNameContext.getSession(), dn ) ) )
            {
                return dn;
            }

            dn.remove( dn.size() - 1 );
        }

        return dn;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#getSuffix(org.apache.directory.server.core.interceptor.context.GetSuffixOperationContext)
     */
    public DN getSuffix( GetSuffixOperationContext getSuffixContext ) throws Exception
    {
        Partition backend = getPartition( getSuffixContext.getDn() );
        return backend.getSuffixDn();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#listSuffixes(org.apache.directory.server.core.interceptor.context.ListSuffixOperationContext)
     */
    public Set<String> listSuffixes( ListSuffixOperationContext emptyContext ) throws Exception
    {
        return Collections.unmodifiableSet( partitions.keySet() );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#registerSupportedExtensions(java.util.Set)
     */
    public void registerSupportedExtensions( Set<String> extensionOids ) throws Exception
    {
        EntryAttribute supportedExtension = rootDSE.get( SchemaConstants.SUPPORTED_EXTENSION_AT );

        if ( supportedExtension == null )
        {
            rootDSE.set( SchemaConstants.SUPPORTED_EXTENSION_AT );
            supportedExtension = rootDSE.get( SchemaConstants.SUPPORTED_EXTENSION_AT );
        }

        for ( String extensionOid : extensionOids )
        {
            supportedExtension.add( extensionOid );
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.partition.PartitionNexus#registerSupportedSaslMechanisms(java.util.Set)
     */
    public void registerSupportedSaslMechanisms( Set<String> supportedSaslMechanisms ) throws Exception
    {
        EntryAttribute supportedSaslMechanismsAttribute = rootDSE.get( SchemaConstants.SUPPORTED_SASL_MECHANISMS_AT );

        if ( supportedSaslMechanismsAttribute == null )
        {
            rootDSE.set( SchemaConstants.SUPPORTED_SASL_MECHANISMS_AT );
            supportedSaslMechanismsAttribute = rootDSE.get( SchemaConstants.SUPPORTED_SASL_MECHANISMS_AT );
        }

        for ( String saslMechanism : supportedSaslMechanisms )
        {
            supportedSaslMechanismsAttribute.add( saslMechanism );
        }
    }


    /**
     * Unregisters an ContextPartition with this BackendManager.  Called for each
     * registered Backend right befor it is to be stopped.  This prevents
     * protocol server requests from reaching the Backend and effectively puts
     * the ContextPartition's naming context offline.
     *
     * Operations against the naming context should result in an LDAP BUSY
     * result code in the returnValue if the naming context is not online.
     *
     * @param partition ContextPartition component to unregister with this
     * BackendNexus.
     * @throws Exception if there are problems unregistering the partition
     */
    private void unregister( Partition partition ) throws Exception
    {
        EntryAttribute namingContexts = rootDSE.get( SchemaConstants.NAMING_CONTEXTS_AT );
        
        if ( namingContexts != null )
        {
            namingContexts.remove( partition.getSuffixDn().getName() );
        }
        
        partitions.remove( partition.getSuffixDn().getName() );
    }


    /**
     * @return the directoryService
     */
    public DirectoryService getDirectoryService()
    {
        return directoryService;
    }


    /**
     * @param directoryService the directoryService to set
     */
    public void setDirectoryService( DirectoryService directoryService )
    {
        this.directoryService = directoryService;
    }
}
