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
package org.apache.directory.server.core;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.naming.directory.Attributes;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authz.AciAuthorizationInterceptor;
import org.apache.directory.server.core.authz.DefaultAuthorizationInterceptor;
import org.apache.directory.server.core.changelog.ChangeLog;
import org.apache.directory.server.core.changelog.ChangeLogEvent;
import org.apache.directory.server.core.changelog.ChangeLogInterceptor;
import org.apache.directory.server.core.changelog.DefaultChangeLog;
import org.apache.directory.server.core.changelog.Tag;
import org.apache.directory.server.core.changelog.TaggableSearchableChangeLogStore;
import org.apache.directory.server.core.collective.CollectiveAttributeInterceptor;
import org.apache.directory.server.core.event.EventInterceptor;
import org.apache.directory.server.core.event.EventService;
import org.apache.directory.server.core.exception.ExceptionInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.InterceptorChain;
import org.apache.directory.server.core.interceptor.context.AddContextPartitionOperationContext;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.RemoveContextPartitionOperationContext;
import org.apache.directory.server.core.journal.DefaultJournal;
import org.apache.directory.server.core.journal.Journal;
import org.apache.directory.server.core.journal.JournalInterceptor;
import org.apache.directory.server.core.normalization.NormalizationInterceptor;
import org.apache.directory.server.core.operational.OperationalAttributeInterceptor;
import org.apache.directory.server.core.partition.DefaultPartitionNexus;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.referral.ReferralInterceptor;
import org.apache.directory.server.core.replication.ReplicationConfiguration;
import org.apache.directory.server.core.schema.DefaultSchemaService;
import org.apache.directory.server.core.schema.SchemaInterceptor;
import org.apache.directory.server.core.schema.SchemaService;
import org.apache.directory.server.core.security.TlsKeyGenerator;
import org.apache.directory.server.core.subtree.SubentryInterceptor;
import org.apache.directory.server.core.trigger.TriggerInterceptor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.csn.Csn;
import org.apache.directory.shared.ldap.csn.CsnFactory;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.client.DefaultClientEntry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.ldif.ChangeType;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.ldif.LdifReader;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.normalizers.OidNormalizer;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Default implementation of {@link DirectoryService}.
 * 
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DefaultDirectoryService implements DirectoryService
{
    /** The logger */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultDirectoryService.class );
    
    private SchemaService schemaService;
    
    /** A reference on the SchemaManager */
    private SchemaManager schemaManager;
    
    /** the root nexus */
    private DefaultPartitionNexus partitionNexus;

    /** whether or not server is started for the first time */
    private boolean firstStart;

    /** The interceptor (or interceptor chain) for this service */
    private InterceptorChain interceptorChain;

    /** whether or not this instance has been shutdown */
    private boolean started;

    /** the change log service */
    private ChangeLog changeLog;
    
    /** the journal service */
    private Journal journal;
    
    /** 
     * the interface used to perform various operations on this 
     * DirectoryService
     */
    private OperationManager operationManager = new DefaultOperationManager( this );

    /** the distinguished name of the administrative user */
    private DN adminDn;
    
    /** session used as admin for internal operations */
    private CoreSession adminSession;
    
    /** The referral manager */
    private ReferralManager referralManager;
    
    /** A flag to tell if the userPassword attribute's value must be hidden */
    private boolean passwordHidden = false;
    
    /** The service's CSN factory */
    private CsnFactory csnFactory;
    
    /** The directory instance replication ID */
    private int replicaId;
    
    /** The replication configuration structure */
    private ReplicationConfiguration replicationConfig;

    /** remove me after implementation is completed */
    private static final String PARTIAL_IMPL_WARNING =
            "WARNING: the changelog is only partially operational and will revert\n" +
            "state without consideration of who made the original change.  All reverting " +
            "changes are made by the admin user.\n Furthermore the used controls are not at " +
            "all taken into account";

    
    /** The delay to wait between each sync on disk */
    private long syncPeriodMillis;
    
    /** The default delay to wait between sync on disk : 15 seconds */
    private static final long DEFAULT_SYNC_PERIOD = 15000;

    /** */
    private Thread workerThread;
    
    /** The sync worker thread */
    private SynchWorker worker = new SynchWorker();

   
    /** The default timeLimit : 100 entries */
    public static final int MAX_SIZE_LIMIT_DEFAULT = 100;

    /** The default timeLimit : 10 seconds */
    public static final int MAX_TIME_LIMIT_DEFAULT = 10000;

    /** The instance Id */
    private String instanceId;
    
    /** The server working directory */
    private File workingDirectory = new File( "server-work" );
    
    /** 
     * A flag used to shutdown the VM when stopping the server. Useful
     * when the server is standalone. If the server is embedded, we don't
     * want to shutdown the VM
     */
    private boolean exitVmOnShutdown = true; // allow by default
    
    /** A flag used to indicate that a shutdown hook has been installed */
    private boolean shutdownHookEnabled = true; // allow by default
    
    /** Manage anonymous access to entries other than the RootDSE */
    private boolean allowAnonymousAccess = true; // allow by default
    
    /** Manage the basic access control checks */
    private boolean accessControlEnabled; // off by default
    
    /** Manage the operational attributes denormalization */
    private boolean denormalizeOpAttrsEnabled; // off by default
    
    /** The list of declared interceptors */
    private List<Interceptor> interceptors;
    
    /** The System partition */
    private Partition systemPartition;
    
    /** The set of all declared partitions */
    private Set<Partition> partitions = new HashSet<Partition>();
    
    /** A list of LDIF entries to inject at startup */
    private List<? extends LdifEntry> testEntries = new ArrayList<LdifEntry>(); // List<Attributes>
    
    /** The event service */
    private EventService eventService;
    
    /** The maximum size for an incoming PDU */
    private int maxPDUSize = Integer.MAX_VALUE;


    
    /**
     * The synchronizer thread. It flush data on disk periodically.
     */
    class SynchWorker implements Runnable
    {
        final Object lock = new Object();
        
        /** A flag to stop the thread */
        boolean stop;


        /**
         * The main loop
         */
        public void run()
        {
            while ( !stop )
            {
                synchronized ( lock )
                {
                    try
                    {
                        lock.wait( syncPeriodMillis );
                    }
                    catch ( InterruptedException e )
                    {
                        LOG.warn( "SynchWorker failed to wait on lock.", e );
                    }
                }

                try
                {
                    partitionNexus.sync();
                }
                catch ( Exception e )
                {
                    LOG.error( I18n.err( I18n.ERR_74 ), e );
                }
            }
        }
    }
    
    
    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------
    
    
    /**
     * Creates a new instance of the directory service.
     */
    public DefaultDirectoryService() throws Exception
    {
        setDefaultInterceptorConfigurations();
        changeLog = new DefaultChangeLog();
        journal = new DefaultJournal();
        syncPeriodMillis = DEFAULT_SYNC_PERIOD;
        csnFactory = new CsnFactory( replicaId );
        schemaService = new DefaultSchemaService();
    }


    // ------------------------------------------------------------------------
    // C O N F I G U R A T I O N   M E T H O D S
    // ------------------------------------------------------------------------
    
    
    public void setInstanceId( String instanceId )
    {
        this.instanceId = instanceId;
    }


    public String getInstanceId()
    {
        return instanceId;
    }


    /**
     * Gets the {@link Partition}s used by this DirectoryService.
     *
     * @return the set of partitions used
     */
    public Set<? extends Partition> getPartitions()
    {
        Set<Partition> cloned = new HashSet<Partition>();
        cloned.addAll( partitions );
        return cloned;
    }


    /**
     * Sets {@link Partition}s used by this DirectoryService.
     *
     * @param partitions the partitions to used
     */
    public void setPartitions( Set<? extends Partition> partitions )
    {
        Set<Partition> cloned = new HashSet<Partition>();
        cloned.addAll( partitions );
        Set<String> names = new HashSet<String>();
        
        for ( Partition partition : cloned )
        {
            String id = partition.getId();
            
            if ( names.contains( id ) )
            {
                LOG.warn( "Encountered duplicate partition {} identifier.", id );
            }
            
            names.add( id );
        }

        this.partitions = cloned;
    }


    /**
     * Returns <tt>true</tt> if access control checks are enabled.
     *
     * @return true if access control checks are enabled, false otherwise
     */
    public boolean isAccessControlEnabled()
    {
        return accessControlEnabled;
    }


    /**
     * Sets whether to enable basic access control checks or not.
     *
     * @param accessControlEnabled true to enable access control checks, false otherwise
     */
    public void setAccessControlEnabled( boolean accessControlEnabled )
    {
        this.accessControlEnabled = accessControlEnabled;
    }


    /**
     * Returns <tt>true</tt> if anonymous access is allowed on entries besides the RootDSE.
     * If the access control subsystem is enabled then access to some entries may not be
     * allowed even when full anonymous access is enabled.
     *
     * @return true if anonymous access is allowed on entries besides the RootDSE, false
     * if anonymous access is allowed to all entries.
     */
    public boolean isAllowAnonymousAccess()
    {
        return allowAnonymousAccess;
    }


    /**
     * Sets whether to allow anonymous access to entries other than the RootDSE.  If the
     * access control subsystem is enabled then access to some entries may not be allowed
     * even when full anonymous access is enabled.
     *
     * @param enableAnonymousAccess true to enable anonymous access, false to disable it
     */
    public void setAllowAnonymousAccess( boolean enableAnonymousAccess )
    {
        this.allowAnonymousAccess = enableAnonymousAccess;
    }


    /**
     * Returns interceptors in the server.
     *
     * @return the interceptors in the server.
     */
    public List<Interceptor> getInterceptors()
    {
        List<Interceptor> cloned = new ArrayList<Interceptor>();
        cloned.addAll( interceptors );
        return cloned;
    }


    /**
     * Sets the interceptors in the server.
     *
     * @param interceptors the interceptors to be used in the server.
     */
    public void setInterceptors( List<Interceptor> interceptors ) 
    {
        Set<String> names = new HashSet<String>();
        
        for ( Interceptor interceptor : interceptors )
        {
            String name = interceptor.getName();
            
            if ( names.contains( name ) )
            {
                LOG.warn( "Encountered duplicate definitions for {} interceptor", interceptor.getName() );
            }
            names.add( name );
        }

        this.interceptors = interceptors;
    }


    /**
     * Returns test directory entries({@link LdifEntry}) to be loaded while
     * bootstrapping.
     *
     * @return test entries to load during bootstrapping
     */
    public List<LdifEntry> getTestEntries()
    {
        List<LdifEntry> cloned = new ArrayList<LdifEntry>();
        cloned.addAll( testEntries );
        return cloned;
    }


    /**
     * Sets test directory entries({@link Attributes}) to be loaded while
     * bootstrapping.
     *
     * @param testEntries the test entries to load while bootstrapping
     */
    public void setTestEntries( List<? extends LdifEntry> testEntries )
    {
        //noinspection MismatchedQueryAndUpdateOfCollection
        List<LdifEntry> cloned = new ArrayList<LdifEntry>();
        cloned.addAll( testEntries );
        this.testEntries = testEntries;
    }


    /**
     * Returns working directory (counterpart of <tt>var/lib</tt>) where partitions are
     * stored by default.
     *
     * @return the directory where partition's are stored.
     */
    public File getWorkingDirectory()
    {
        return workingDirectory;
    }


    /**
     * Sets working directory (counterpart of <tt>var/lib</tt>) where partitions are stored
     * by default.
     *
     * @param workingDirectory the directory where the server's partitions are stored by default.
     */
    public void setWorkingDirectory( File workingDirectory )
    {
        this.workingDirectory = workingDirectory;
    }


    public void setShutdownHookEnabled( boolean shutdownHookEnabled )
    {
        this.shutdownHookEnabled = shutdownHookEnabled;
    }


    public boolean isShutdownHookEnabled()
    {
        return shutdownHookEnabled;
    }


    public void setExitVmOnShutdown( boolean exitVmOnShutdown )
    {
        this.exitVmOnShutdown = exitVmOnShutdown;
    }


    public boolean isExitVmOnShutdown()
    {
        return exitVmOnShutdown;
    }


    public void setSystemPartition( Partition systemPartition )
    {
        this.systemPartition = systemPartition;
    }


    public Partition getSystemPartition()
    {
        return systemPartition;
    }


    /**
     * return true if the operational attributes must be normalized when returned
     */
    public boolean isDenormalizeOpAttrsEnabled()
    {
        return denormalizeOpAttrsEnabled;
    }


    /**
     * Sets whether the operational attributes are denormalized when returned
     * @param denormalizeOpAttrsEnabled The flag value
     */
    public void setDenormalizeOpAttrsEnabled( boolean denormalizeOpAttrsEnabled )
    {
        this.denormalizeOpAttrsEnabled = denormalizeOpAttrsEnabled;
    }


    /**
     * {@inheritDoc}
     */
    public ChangeLog getChangeLog()
    {
        return changeLog;
    }


    /**
     * {@inheritDoc}
     */
    public Journal getJournal()
    {
        return journal;
    }


    /**
     * {@inheritDoc}
     */
    public void setChangeLog( ChangeLog changeLog )
    {
        this.changeLog = changeLog;
    }


    /**
     * {@inheritDoc}
     */
    public void setJournal( Journal journal )
    {
        this.journal = journal;
    }


    public void addPartition( Partition partition ) throws Exception
    {
        partition.setSchemaManager( schemaManager );
        partitions.add( partition );

        if ( ! started )
        {
            return;
        }

        AddContextPartitionOperationContext addPartitionCtx = 
            new AddContextPartitionOperationContext( adminSession, partition );
        partitionNexus.addContextPartition( addPartitionCtx );
    }


    public void removePartition( Partition partition ) throws Exception
    {
        partitions.remove( partition );

        if ( ! started )
        {
            return;
        }

        RemoveContextPartitionOperationContext removePartitionCtx =
                new RemoveContextPartitionOperationContext( adminSession, partition.getSuffixDn() );
        partitionNexus.removeContextPartition( removePartitionCtx );
    }


    // ------------------------------------------------------------------------
    // BackendSubsystem Interface Method Implementations
    // ------------------------------------------------------------------------


    private void setDefaultInterceptorConfigurations()
    {
        // Set default interceptor chains
        List<Interceptor> list = new ArrayList<Interceptor>();

        list.add( new NormalizationInterceptor() );
        list.add( new AuthenticationInterceptor() );
        list.add( new ReferralInterceptor() );
        list.add( new AciAuthorizationInterceptor() );
        list.add( new DefaultAuthorizationInterceptor() );
        list.add( new ExceptionInterceptor() );
        list.add( new ChangeLogInterceptor() );
        list.add( new OperationalAttributeInterceptor() );
        list.add( new SchemaInterceptor() );
        list.add( new SubentryInterceptor() );
        list.add( new CollectiveAttributeInterceptor() );
        list.add( new EventInterceptor() );
        list.add( new TriggerInterceptor() );
        list.add( new JournalInterceptor() );

        setInterceptors( list );
    }

    
    public CoreSession getAdminSession()
    {
        return adminSession;
    }
    
    
    public CoreSession getSession() 
    {
        return new DefaultCoreSession( new LdapPrincipal(), this );
    }
    
    
    public CoreSession getSession( LdapPrincipal principal )
    {
        return new DefaultCoreSession( principal, this );
    }
    
    
    public CoreSession getSession( DN principalDn, byte[] credentials ) 
        throws Exception
    {
        if ( ! started )
        {
            throw new IllegalStateException( "Service has not started." );
        }

        BindOperationContext bindContext = new BindOperationContext( null );
        bindContext.setCredentials( credentials );
        bindContext.setDn( principalDn );
        operationManager.bind( bindContext );
        
        return bindContext.getSession();
    }
    
    
    public CoreSession getSession( DN principalDn, byte[] credentials, String saslMechanism, String saslAuthId ) 
        throws Exception
    {
        if ( ! started )
        {
            throw new IllegalStateException( "Service has not started." );
        }

        BindOperationContext bindContext = new BindOperationContext( null );
        bindContext.setCredentials( credentials );
        bindContext.setDn( principalDn );
        bindContext.setSaslMechanism( saslMechanism );
        operationManager.bind( bindContext );
        
        return bindContext.getSession();
    }


    public long revert() throws Exception
    {
        if ( changeLog == null || ! changeLog.isEnabled() )
        {
            throw new IllegalStateException( I18n.err( I18n.ERR_310 ) );
        }

        Tag latest = changeLog.getLatest();
        
        if ( null != latest )
        {
            if ( latest.getRevision() < changeLog.getCurrentRevision() )
            {
                return revert( latest.getRevision() );
            }
            else
            {
                LOG.info( "Ignoring request to revert without changes since the latest tag." );
                return changeLog.getCurrentRevision();
            }
        }

        throw new IllegalStateException( I18n.err( I18n.ERR_311 ) );
    }


    /**
     * We handle the ModDN/ModRDN operation for the revert here. 
     */
    private void moddn( DN oldDn, DN newDn, boolean delOldRdn ) throws Exception
    {
        if ( oldDn.size() == 0 )
        {
            throw new LdapNoPermissionException( I18n.err( I18n.ERR_312 ) );
        }

        // calculate parents
        DN oldBase = ( DN ) oldDn.clone();
        oldBase.remove( oldDn.size() - 1 );
        DN newBase = ( DN ) newDn.clone();
        newBase.remove( newDn.size() - 1 );

        // Compute the RDN for each of the DN
        RDN newRdn = newDn.getRdn( newDn.size() - 1 );
        RDN oldRdn = oldDn.getRdn( oldDn.size() - 1 );

        /*
         * We need to determine if this rename operation corresponds to a simple
         * RDN name change or a move operation.  If the two names are the same
         * except for the RDN then it is a simple modifyRdn operation.  If the
         * names differ in size or have a different baseDN then the operation is
         * a move operation.  Furthermore if the RDN in the move operation 
         * changes it is both an RDN change and a move operation.
         */
        if ( ( oldDn.size() == newDn.size() ) && oldBase.equals( newBase ) )
        {
            adminSession.rename( oldDn, newRdn, delOldRdn );
        }
        else
        {
            DN target = ( DN ) newDn.clone();
            target.remove( newDn.size() - 1 );

            if ( newRdn.equals( oldRdn ) )
            {
                adminSession.move( oldDn, target );
            }
            else
            {
                adminSession.moveAndRename( oldDn, target, new RDN( newRdn ), delOldRdn );
            }
        }
    }
    
    
    public long revert( long revision ) throws Exception
    {
        if ( changeLog == null || ! changeLog.isEnabled() )
        {
            throw new IllegalStateException( I18n.err( I18n.ERR_310 ) );
        }

        if ( revision < 0 )
        {
            throw new IllegalArgumentException( I18n.err( I18n.ERR_239 ) );
        }

        if ( revision >= changeLog.getChangeLogStore().getCurrentRevision() )
        {
            throw new IllegalArgumentException( I18n.err( I18n.ERR_314 ) );
        }

        Cursor<ChangeLogEvent> cursor = changeLog.getChangeLogStore().findAfter( revision );

        /*
         * BAD, BAD, BAD!!!
         *
         * No synchronization no nothing.  Just getting this to work for now
         * so we can revert tests.  Any production grade use of this feature
         * needs to synchronize on all changes while the revert is in progress.
         *
         * How about making this operation transactional?
         *
         * First of all just stop using JNDI and construct the operations to
         * feed into the interceptor pipeline.
         * 
         * TODO review this code.
         */

        try
        {
            LOG.warn( PARTIAL_IMPL_WARNING );
            cursor.afterLast();
            
            while ( cursor.previous() ) // apply ldifs in reverse order
            {
                ChangeLogEvent event = cursor.get();
                List<LdifEntry> reverses = event.getReverseLdifs();
                
                for ( LdifEntry reverse:reverses )
                {
                    switch( reverse.getChangeType().getChangeType() )
                    {
                        case ChangeType.ADD_ORDINAL :
                            adminSession.add( 
                                new DefaultServerEntry( schemaManager, reverse.getEntry() ), true ); 
                            break;
                            
                        case ChangeType.DELETE_ORDINAL :
                            adminSession.delete( reverse.getDn(), true );
                            break;
                            
                        case ChangeType.MODIFY_ORDINAL :
                            List<Modification> mods = reverse.getModificationItems();
    
                            adminSession.modify( reverse.getDn(), mods, true );
                            break;
                            
                        case ChangeType.MODDN_ORDINAL :
                            // NO BREAK - both ModDN and ModRDN handling is the same
                        
                        case ChangeType.MODRDN_ORDINAL :
                            DN forwardDn = event.getForwardLdif().getDn();
                            DN reverseDn = reverse.getDn();
                            
                            moddn( reverseDn, forwardDn, reverse.isDeleteOldRdn() );
    
                            break;
                            
                        default:
                            LOG.error( I18n.err( I18n.ERR_75 ) );
                            throw new NotImplementedException( I18n.err( I18n.ERR_76, reverse.getChangeType() ) );
                    }
                }
            }
        }
        catch ( IOException e )
        {
            String message = I18n.err( I18n.ERR_77, revision );
            LOG.error( message );
            throw new LdapException( message );
        }

        return changeLog.getCurrentRevision();
    }

    
    public OperationManager getOperationManager()
    {
        return operationManager;
    }
    

    /**
     * @throws Exception if the LDAP server cannot be started
     */
    public synchronized void startup() throws Exception
    {
        if ( started )
        {
            return;
        }

        if ( shutdownHookEnabled )
        {
            Runtime.getRuntime().addShutdownHook( new Thread( new Runnable()
            {
                public void run()
                {
                    try
                    {
                        shutdown();
                    }
                    catch ( Exception e )
                    {
                        LOG.warn( "Failed to shut down the directory service: "
                            + DefaultDirectoryService.this.instanceId, e );
                    }
                }
            }, "ApacheDS Shutdown Hook (" + instanceId + ')' ) );

            LOG.info( "ApacheDS shutdown hook has been registered with the runtime." );
        }
        else if ( LOG.isWarnEnabled() )
        {
            LOG.warn( "ApacheDS shutdown hook has NOT been registered with the runtime."
                + "  This default setting for standalone operation has been overriden." );
        }

        initialize();
        showSecurityWarnings();
        
        // Start the sync thread if required
        if ( syncPeriodMillis > 0 )
        {
            workerThread = new Thread( worker, "SynchWorkerThread" );
            workerThread.start();
        }

        
        started = true;

        if ( !testEntries.isEmpty() )
        {
            createTestEntries();
        }
    }


    public synchronized void sync() throws Exception
    {
        if ( !started )
        {
            return;
        }

        this.changeLog.sync();
        this.partitionNexus.sync();
    }


    public synchronized void shutdown() throws Exception
    {
        if ( !started )
        {
            return;
        }

        // --------------------------------------------------------------------
        // Shutdown the changelog
        // --------------------------------------------------------------------
        changeLog.sync();
        changeLog.destroy();
        
        // --------------------------------------------------------------------
        // Shutdown the journal if enabled
        // --------------------------------------------------------------------
        if ( journal.isEnabled() )
        {
            journal.destroy();
        }

        // --------------------------------------------------------------------
        // Shutdown the partition
        // --------------------------------------------------------------------

        partitionNexus.sync();
        partitionNexus.destroy();
        
        // --------------------------------------------------------------------
        // Shutdown the sync thread
        // --------------------------------------------------------------------
        if ( workerThread != null )
        {
            worker.stop = true;
            
            synchronized ( worker.lock )
            {
                worker.lock.notify();
            }
    
            while ( workerThread.isAlive() )
            {
                LOG.info( "Waiting for SynchWorkerThread to die." );
                workerThread.join( 500 );
            }
        }

        
        // --------------------------------------------------------------------
        // And shutdown the server
        // --------------------------------------------------------------------
        interceptorChain.destroy();
        started = false;
        setDefaultInterceptorConfigurations();
    }

    
    /**
     * @return The referral manager
     */
    public ReferralManager getReferralManager()
    {
        return referralManager;
    }

    /**
     * Set the referralManager
     * @param referralManager The initialized referralManager
     */
    public void setReferralManager( ReferralManager referralManager )
    {
        this.referralManager = referralManager;
    }
    
    
    /**
     * @return the SchemaManager
     */
    public SchemaManager getSchemaManager()
    {
        return schemaManager;
    }
    
    
    /**
     * Set the SchemaManager instance.
     *
     * @param schemaManager The schemaManager
     */
    public void setSchemaManager( SchemaManager schemaManager )
    {
        this.schemaManager = schemaManager;
    }


    public SchemaService getSchemaService()
    {
        return schemaService;
    }


    public void setSchemaService( SchemaService schemaService )
    {
        this.schemaService = schemaService;
    }


    public DefaultPartitionNexus getPartitionNexus()
    {
        return partitionNexus;
    }


    public InterceptorChain getInterceptorChain()
    {
        return interceptorChain;
    }


    public boolean isFirstStart()
    {
        return firstStart;
    }


    public boolean isStarted()
    {
        return started;
    }


    public ServerEntry newEntry( DN dn ) 
    {
        return new DefaultServerEntry( schemaManager, dn );
    }
    

    /**
     * Returns true if we had to create the bootstrap entries on the first
     * start of the server.  Otherwise if all entries exist, meaning none
     * had to be created, then we are not starting for the first time.
     *
     * @return true if the bootstrap entries had to be created, false otherwise
     * @throws Exception if entries cannot be created
     */
    private boolean createBootstrapEntries() throws Exception
    {
        boolean firstStart = false;
        
        // -------------------------------------------------------------------
        // create admin entry
        // -------------------------------------------------------------------

        /*
         * If the admin entry is there, then the database was already created
         */
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, adminDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, adminDn );
            
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, 
                                SchemaConstants.TOP_OC,
                                SchemaConstants.PERSON_OC,
                                SchemaConstants.ORGANIZATIONAL_PERSON_OC,
                                SchemaConstants.INET_ORG_PERSON_OC );

            serverEntry.put( SchemaConstants.UID_AT, PartitionNexus.ADMIN_UID );
            serverEntry.put( SchemaConstants.USER_PASSWORD_AT, PartitionNexus.ADMIN_PASSWORD_BYTES );
            serverEntry.put( SchemaConstants.DISPLAY_NAME_AT, "Directory Superuser" );
            serverEntry.put( SchemaConstants.CN_AT, "system administrator" );
            serverEntry.put( SchemaConstants.SN_AT, "administrator" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.put( SchemaConstants.DISPLAY_NAME_AT, "Directory Superuser" );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            TlsKeyGenerator.addKeyPair( serverEntry );
            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        // -------------------------------------------------------------------
        // create system users area
        // -------------------------------------------------------------------

        Map<String,OidNormalizer> oidsMap = schemaManager.getNormalizerMapping();
        DN userDn = new DN( ServerDNConstants.USERS_SYSTEM_DN );
        userDn.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, userDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, userDn );
            
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, 
                                SchemaConstants.TOP_OC,
                                SchemaConstants.ORGANIZATIONAL_UNIT_OC );

            serverEntry.put( SchemaConstants.OU_AT, "users" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        // -------------------------------------------------------------------
        // create system groups area
        // -------------------------------------------------------------------

        DN groupDn = new DN( ServerDNConstants.GROUPS_SYSTEM_DN );
        groupDn.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, groupDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, groupDn );
            
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, 
                                SchemaConstants.TOP_OC,
                                SchemaConstants.ORGANIZATIONAL_UNIT_OC );

            serverEntry.put( SchemaConstants.OU_AT, "groups" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        // -------------------------------------------------------------------
        // create administrator group
        // -------------------------------------------------------------------

        DN name = new DN( ServerDNConstants.ADMINISTRATORS_GROUP_DN );
        name.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, name ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, name );
            
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, 
                                SchemaConstants.TOP_OC,
                                SchemaConstants.GROUP_OF_UNIQUE_NAMES_OC );

            serverEntry.put( SchemaConstants.CN_AT, "Administrators" );
            serverEntry.put( SchemaConstants.UNIQUE_MEMBER_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );

            // TODO - confirm if we need this at all since the 
            // group cache on initialization after this stage will
            // search the directory for all the groups anyway
            
//            Interceptor authzInterceptor = interceptorChain.get( AciAuthorizationInterceptor.class.getName() );
//            
//            if ( authzInterceptor == null )
//            {
//                LOG.error( "The Authorization service is null : this is not allowed" );
//                throw new NamingException( "The Authorization service is null" );
//            }
//            
//            if ( !( authzInterceptor instanceof AciAuthorizationInterceptor ) )
//            {
//                LOG.error( "The Authorization service is not set correctly : '{}' is an incorect interceptor",
//                    authzInterceptor.getClass().getName() );
//                throw new NamingException( "The Authorization service is incorrectly set" );
//                
//            }
//
//            AciAuthorizationInterceptor authzSrvc = ( AciAuthorizationInterceptor ) authzInterceptor;
//            authzSrvc.cacheNewGroup( name, serverEntry );
        }

        // -------------------------------------------------------------------
        // create system configuration area
        // -------------------------------------------------------------------

        DN configurationDn = new DN( "ou=configuration,ou=system" );
        configurationDn.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, configurationDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, configurationDn );
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC, SchemaConstants.ORGANIZATIONAL_UNIT_OC );

            serverEntry.put( SchemaConstants.OU_AT, "configuration" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        // -------------------------------------------------------------------
        // create system configuration area for partition information
        // -------------------------------------------------------------------

        DN partitionsDn = new DN( "ou=partitions,ou=configuration,ou=system" );
        partitionsDn.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, partitionsDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, partitionsDn );
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC, SchemaConstants.ORGANIZATIONAL_UNIT_OC );
            serverEntry.put( SchemaConstants.OU_AT, "partitions" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        // -------------------------------------------------------------------
        // create system configuration area for services
        // -------------------------------------------------------------------

        DN servicesDn = new DN( "ou=services,ou=configuration,ou=system" );
        servicesDn.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, servicesDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, servicesDn );
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC, SchemaConstants.ORGANIZATIONAL_UNIT_OC );

            serverEntry.put( SchemaConstants.OU_AT, "services" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        // -------------------------------------------------------------------
        // create system configuration area for interceptors
        // -------------------------------------------------------------------

        DN interceptorsDn = new DN( "ou=interceptors,ou=configuration,ou=system" );
        interceptorsDn.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, interceptorsDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, interceptorsDn );
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC, SchemaConstants.ORGANIZATIONAL_UNIT_OC );

            serverEntry.put( SchemaConstants.OU_AT, "interceptors" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        // -------------------------------------------------------------------
        // create system preferences area
        // -------------------------------------------------------------------

        DN sysPrefRootDn = new DN( ServerDNConstants.SYSPREFROOT_SYSTEM_DN );
        sysPrefRootDn.normalize( oidsMap );
        
        if ( !partitionNexus.hasEntry( new EntryOperationContext( adminSession, sysPrefRootDn ) ) )
        {
            firstStart = true;

            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, sysPrefRootDn );
            serverEntry.put( SchemaConstants.OBJECT_CLASS_AT, 
                SchemaConstants.TOP_OC, 
                SchemaConstants.ORGANIZATIONAL_UNIT_OC,
                SchemaConstants.EXTENSIBLE_OBJECT_OC );

            serverEntry.put( "prefNodeName", "sysPrefRoot" );
            serverEntry.put( SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            serverEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            serverEntry.add( SchemaConstants.ENTRY_CSN_AT, getCSN().toString() );
            serverEntry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

            partitionNexus.add( new AddOperationContext( adminSession, serverEntry ) );
        }

        return firstStart;
    }


    /**
     * Displays security warning messages if any possible secutiry issue is found.
     * @throws Exception if there are failures parsing and accessing internal structures
     */
    private void showSecurityWarnings() throws Exception
    {
        // Warn if the default password is not changed.
        boolean needToChangeAdminPassword = false;

        DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN );
        adminDn.normalize( schemaManager.getNormalizerMapping() );
        
        ServerEntry adminEntry = partitionNexus.lookup( new LookupOperationContext( adminSession, adminDn ) );
        Object userPassword = adminEntry.get( SchemaConstants.USER_PASSWORD_AT ).get();
        
        if ( userPassword instanceof byte[] )
        {
            needToChangeAdminPassword = Arrays.equals( PartitionNexus.ADMIN_PASSWORD_BYTES, ( byte[] ) userPassword );
        }
        else if ( userPassword.toString().equals( PartitionNexus.ADMIN_PASSWORD_STRING ) )
        {
            needToChangeAdminPassword = PartitionNexus.ADMIN_PASSWORD_STRING.equals( userPassword.toString() );
        }

        if ( needToChangeAdminPassword )
        {
            LOG.warn( "You didn't change the admin password of directory service " + "instance '" + instanceId + "'.  "
                + "Please update the admin password as soon as possible " + "to prevent a possible security breach." );
        }
    }


    /**
     * Adds test entries into the core.
     *
     * @todo this may no longer be needed when JNDI is not used for bootstrapping
     * 
     * @throws Exception if the creation of test entries fails.
     */
    private void createTestEntries() throws Exception
    {
        for ( LdifEntry testEntry : testEntries )
        {
            try
            {
                LdifEntry ldifEntry = testEntry.clone();
                Entry entry = ldifEntry.getEntry();
                String dn = ldifEntry.getDn().getName();

                try
                {
                    getAdminSession().add( new DefaultServerEntry( schemaManager, entry ) ); 
                }
                catch ( Exception e )
                {
                    LOG.warn( dn + " test entry already exists.", e );
                }
            }
            catch ( CloneNotSupportedException cnse )
            {
                LOG.warn( "Cannot clone the entry ", cnse );
            }
        }
    }


    /**
     * Kicks off the initialization of the entire system.
     *
     * @throws Exception if there are problems along the way
     */
    private void initialize() throws Exception
    {
        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "---> Initializing the DefaultDirectoryService " );
        }

        // triggers partition to load schema fully from schema partition
        schemaService.initialize();
        schemaService.getSchemaPartition().initialize();
        partitions.add( schemaService.getSchemaPartition() );
        systemPartition.getSuffixDn().normalize( schemaManager.getNormalizerMapping() );

        adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN ).normalize( schemaManager.getNormalizerMapping() );
        adminDn.normalize( schemaManager.getNormalizerMapping() );
        adminSession = new DefaultCoreSession( new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), this );
        
        // @TODO - NOTE: Need to find a way to instantiate without dependency on DPN
        partitionNexus = new DefaultPartitionNexus( new DefaultServerEntry( schemaManager, DN.EMPTY_DN ) );
        partitionNexus.setDirectoryService( this );
        partitionNexus.initialize( );
        //partitionNexus.addContextPartition( new AddContextPartitionOperationContext( adminSession, schemaService.getSchemaPartition() ) );

        // --------------------------------------------------------------------
        // Create all the bootstrap entries before initializing chain
        // --------------------------------------------------------------------
        
        firstStart = createBootstrapEntries();

        interceptorChain = new InterceptorChain();
        interceptorChain.init( this );

        // --------------------------------------------------------------------
        // Initialize the changeLog if it's enabled
        // --------------------------------------------------------------------
        
        if ( changeLog.isEnabled() )
        {
            changeLog.init( this );
            
            if( changeLog.isExposed() && changeLog.isTagSearchSupported() )
            {
                String clSuffix = ( ( TaggableSearchableChangeLogStore ) changeLog.getChangeLogStore() ).getPartition().getSuffixDn().getName();
                partitionNexus.getRootDSE( null ).getOriginalEntry().add( SchemaConstants.CHANGELOG_CONTEXT_AT, clSuffix );
            }
        }
        
        // --------------------------------------------------------------------
        // Initialize the journal if it's enabled
        // --------------------------------------------------------------------
        if ( journal.isEnabled() )
        {
            journal.init( this );
        }

        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "<--- DefaultDirectoryService initialized" );
        }
    }
    
    
    /**
     * Read an entry (without DN)
     * 
     * @param text The ldif format file
     * @return An entry.
     */
    private Entry readEntry( String text )
    {
        StringReader strIn = new StringReader( text );
        BufferedReader in = new BufferedReader( strIn );

        String line = null;
        Entry entry = new DefaultClientEntry();

        try
        {
            while ( ( line = in.readLine() ) != null )
            {
                if ( line.length() == 0 )
                {
                    continue;
                }

                String addedLine = line.trim();

                if ( StringTools.isEmpty( addedLine ) )
                {
                    continue;
                }

                EntryAttribute attribute = LdifReader.parseAttributeValue( addedLine );
                EntryAttribute oldAttribute = entry.get( attribute.getId() );

                if ( oldAttribute != null )
                {
                    try
                    {
                        oldAttribute.add( attribute.get() );
                        entry.put( oldAttribute );
                    }
                    catch ( LdapException ne )
                    {
                        // Do nothing
                    }
                }
                else
                {
                    try
                    {
                        entry.put( attribute );
                    }
                    catch ( LdapException ne )
                    {
                        // TODO do nothing ...
                    }
                }
            }
        }
        catch (IOException ioe)
        {
            // Do nothing : we can't reach this point !
        }

        return entry;
    }

    
    /**
     * Create a new ServerEntry
     * 
     * @param ldif The String representing the attributes, as a LDIF file
     * @param dn The DN for this new entry
     */
    public ServerEntry newEntry( String ldif, String dn )
    {
        try
        {
            Entry entry = readEntry( ldif );
            DN newDn = new DN( dn );
            
            entry.setDn( newDn );
            
            // TODO Let's get rid of this Attributes crap
            ServerEntry serverEntry = new DefaultServerEntry( schemaManager, entry );
            return serverEntry;
        }
        catch ( Exception e )
        {
            LOG.error( I18n.err( I18n.ERR_78, ldif, dn ) );
            // do nothing
            return null;
        }
    }


    public EventService getEventService()
    {
        return eventService;
    }


    public void setEventService( EventService eventService )
    {
        this.eventService = eventService;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public boolean isPasswordHidden()
    {
        return passwordHidden;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public void setPasswordHidden( boolean passwordHidden )
    {
        this.passwordHidden = passwordHidden;
    }


    /**
     * @return The maximum allowed size for an incoming PDU
     */
    public int getMaxPDUSize()
    {
        return maxPDUSize;
    }


    /**
     * Set the maximum allowed size for an incoming PDU 
     * @param maxPDUSize A positive number of bytes for the PDU. A negative or
     * null value will be transformed to {@link Integer#MAX_VALUE}
     */
    public void setMaxPDUSize( int maxPDUSize )
    {
        if ( maxPDUSize <= 0 )
        {
            maxPDUSize = Integer.MAX_VALUE;
        }
        
        this.maxPDUSize = maxPDUSize;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public Interceptor getInterceptor( String interceptorName )
    {
        for ( Interceptor interceptor:interceptors )
        {
            if ( interceptor.getName().equalsIgnoreCase( interceptorName ) )
            {
                return interceptor;
            }
        }
        
        return null;
    }


    /**
     * Get a new CSN
     * @return The CSN generated for this directory service
     */
    public Csn getCSN()
    {
        return csnFactory.newInstance();
    }


    /**
     * @return the replicaId
     */
    public int getReplicaId()
    {
        return replicaId;
    }


    /**
     * @param replicaId the replicaId to set
     */
    public void setReplicaId( int replicaId )
    {
        if ( ( replicaId < 0 ) || ( replicaId > 999 ) )
        {
            LOG.error( I18n.err( I18n.ERR_79 ) );
            this.replicaId = 0;
        }
        else
        {
            this.replicaId = replicaId;
        }
    }


    public void setReplicationConfiguration( ReplicationConfiguration replicationConfig )
    {
        this.replicationConfig = replicationConfig;
        
    }
    
    
    /**
     * @return the replication configuration for this DirectoryService
     */
    public ReplicationConfiguration getReplicationConfiguration()
    {
        return replicationConfig;
    }
    
    
    /**
     * @return the syncPeriodMillis
     */
    public long getSyncPeriodMillis()
    {
        return syncPeriodMillis;
    }


    /**
     * @param syncPeriodMillis the syncPeriodMillis to set
     */
    public void setSyncPeriodMillis( long syncPeriodMillis )
    {
        this.syncPeriodMillis = syncPeriodMillis;
    }
}
