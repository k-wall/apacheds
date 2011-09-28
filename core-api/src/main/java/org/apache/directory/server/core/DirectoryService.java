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


import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.directory.server.core.changelog.ChangeLog;
import org.apache.directory.server.core.entry.ServerEntryFactory;
import org.apache.directory.server.core.event.EventService;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.InterceptorChain;
import org.apache.directory.server.core.journal.Journal;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.replication.ReplicationConfiguration;
import org.apache.directory.server.core.schema.SchemaService;
import org.apache.directory.shared.ldap.csn.Csn;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * Provides JNDI service to {@link AbstractContextFactory}.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $, $Date: 2010-03-24 19:39:54 +0100 (Mer, 24 mar 2010) $
 */
public interface DirectoryService extends ServerEntryFactory
{
    String JNDI_KEY = DirectoryService.class.getName();

    /**
     * Reverts the server's state to an earlier revision.  Note that the revsion number
     * still increases to revert back even though the state reverted to is the same.
     * Note that implementations may lock the server from making changes or searching
     * the directory until this operation has completed.
     *
     * @param revision the revision number to revert to
     * @return the new revision reached by applying all changes needed to revert to the
     * original state
     * @throws Exception if there are problems reverting back to the earlier state
     * @throws IllegalArgumentException if the revision provided is greater than the current
     * revision or less than 0
     * @throws UnsupportedOperationException if this feature is not supported by the
     * change log
     */
    long revert( long revision ) throws Exception;


    /**
     * Reverts the server's state to the latest tagged snapshot if one was taken.  If
     * there is no tag a illegal state exception will result.  If the latest revision
     * is not earlier than the current revision (both are same), then no changes were
     * made to the directory to be reverted.  In this case we return the current
     * revision and do nothiig loggin the fact that we ignored the request to revert.
     *
     * @return the new revision reached by applying all changes needed to revert
     * to the new state or the same version before this call if no revert actually
     * took place
     * @throws Exception if there are problems reverting back to the earlier state
     * @throws UnsupportedOperationException if this feature is not supported by the
     * change log
     */
    long revert() throws Exception;


    PartitionNexus getPartitionNexus();


    InterceptorChain getInterceptorChain();


    void addPartition( Partition partition ) throws Exception;
    

    void removePartition( Partition partition ) throws Exception;

    /**
     * @return The Directory Service SchemaManager
     */
    SchemaManager getSchemaManager();


    /**
     * @return The referral manager
     */
    ReferralManager getReferralManager();

    
    /**
     * Set the referralManager
     * 
     * @org.apache.xbean.Property hidden="true"
     * @param referralManager The initialized referralManager
     */
    void setReferralManager( ReferralManager referralManager );

    
    SchemaService getSchemaService();


    /**
     * @org.apache.xbean.Property hidden="true"
     */
    void setSchemaService( SchemaService schemaService );
    
    
    EventService getEventService();
    
    /**
     * @org.apache.xbean.Property hidden="true"
     */
    void setEventService( EventService eventService );


    /**
     * Starts up this service.
     * 
     * @throws Exception if failed to start up
     */
    void startup() throws Exception;


    /**
     * Shuts down this service.
     * 
     * @throws Exception if failed to shut down
     */
    void shutdown() throws Exception;


    /**
     * Calls {@link Partition#sync()} for all registered {@link Partition}s.
     * @throws Exception if synchronization failed
     */
    void sync() throws Exception;


    /**
     * Returns <tt>true</tt> if this service is started.
     * @return true if the service has started, false otherwise
     */
    boolean isStarted();

    
    CoreSession getAdminSession() throws Exception;
    
    
    /**
     * Gets a logical session to perform operations on this DirectoryService
     * as the anonymous user.  This bypasses authentication without 
     * propagating a bind operation into the core.
     *
     * @return a logical session as the anonymous user
     */
    CoreSession getSession() throws Exception;

    
    /**
     * Gets a logical session to perform operations on this DirectoryService
     * as a specific user.  This bypasses authentication without propagating 
     * a bind operation into the core.
     *
     * @return a logical session as a specific user
     */
    CoreSession getSession( LdapPrincipal principal ) throws Exception;

    
    /**
     * Gets a logical session to perform operations on this DirectoryService
     * as a specific user with a separate authorization principal.  This 
     * bypasses authentication without propagating a bind operation into the 
     * core.
     *
     * @return a logical session as a specific user
     */
    CoreSession getSession( DN principalDn, byte[] credentials ) throws Exception;

    
    /**
     * Gets a logical session to perform operations on this DirectoryService
     * as a specific user with a separate authorization principal.  This 
     * bypasses authentication without propagating a bind operation into the 
     * core.
     *
     * @return a logical session as a specific user
     */
    CoreSession getSession( DN principalDn, byte[] credentials, String saslMechanism, String saslAuthId ) 
        throws Exception;

    
    /**
     * @org.apache.xbean.Property hidden="true"
     */
    void setInstanceId( String instanceId );


    String getInstanceId();


    /**
     * Gets the {@link Partition}s used by this DirectoryService.
     *
     * @return the set of partitions used
     */
    Set<? extends Partition> getPartitions();


    /**
     * Sets {@link Partition}s used by this DirectoryService.
     *
     * @org.apache.xbean.Property nestedType="org.apache.directory.server.core.partition.Partition"
     * @param partitions the partitions to used
     */
    void setPartitions( Set<? extends Partition> partitions );


    /**
     * Returns <tt>true</tt> if access control checks are enabled.
     *
     * @return true if access control checks are enabled, false otherwise
     */
    boolean isAccessControlEnabled();


    /**
     * Sets whether to enable basic access control checks or not.
     *
     * @param accessControlEnabled true to enable access control checks, false otherwise
     */
    void setAccessControlEnabled( boolean accessControlEnabled );


    /**
     * Returns <tt>true</tt> if anonymous access is allowed on entries besides the RootDSE.
     * If the access control subsystem is enabled then access to some entries may not be
     * allowed even when full anonymous access is enabled.
     *
     * @return true if anonymous access is allowed on entries besides the RootDSE, false
     * if anonymous access is allowed to all entries.
     */
    boolean isAllowAnonymousAccess();


    /**
     * Returns <tt>true</tt> if the service requires the userPassword attribute
     * to be masked. It's an option in the server.xml file.
     *
     * @return true if the service requires that the userPassword is to be hidden
     */
    boolean isPasswordHidden();

    
    /**
     * Sets whether to allow anonymous access to entries other than the RootDSE.  If the
     * access control subsystem is enabled then access to some entries may not be allowed
     * even when full anonymous access is enabled.
     *
     * @param enableAnonymousAccess true to enable anonymous access, false to disable it
     */
    void setAllowAnonymousAccess( boolean enableAnonymousAccess );


    /**
     * Sets whether the userPassword attribute is readable, or hidden.
     *
     * @param passwordHidden true to enable hide the userPassword attribute, false otherwise
     */
    void setPasswordHidden( boolean passwordHidden );


    /**
     * Returns interceptors in the server.
     *
     * @return the interceptors in the server.
     */
    List<Interceptor> getInterceptors();


    /**
     * Sets the interceptors in the server.
     *
     * @org.apache.xbean.Property nestedType="org.apache.directory.server.core.interceptor.Interceptor"
     * @param interceptors the interceptors to be used in the server.
     */
    void setInterceptors( List<Interceptor> interceptors );


    /**
     * {@inheritDoc}
     */
    void setJournal( Journal journal );

    
    /**
     * Returns test directory entries({@link LdifEntry}) to be loaded while
     * bootstrapping.
     *
     * @return test entries to load during bootstrapping
     */
    List<LdifEntry> getTestEntries();


    /**
     * Sets test directory entries({@link Attributes}) to be loaded while
     * bootstrapping.
     *
     * @org.apache.xbean.Property nestedType="org.apache.directory.shared.ldap.ldif.Entry"
     * @param testEntries the test entries to load while bootstrapping
     */
    void setTestEntries( List<? extends LdifEntry> testEntries );


    /**
     * Returns working directory (counterpart of <tt>var/lib</tt>) where partitions are
     * stored by default.
     *
     * @return the directory where partition's are stored.
     */
    File getWorkingDirectory();


    /**
     * Sets working directory (counterpart of <tt>var/lib</tt>) where partitions are stored
     * by default.
     *
     * @param workingDirectory the directory where the server's partitions are stored by default.
     */
    void setWorkingDirectory( File workingDirectory );


    /**
     * Sets the shutdown hook flag which controls whether or not this DirectoryService
     * registers a JVM shutdown hook to flush caches and synchronize to disk safely.  This is
     * enabled by default.
     *
     * @org.apache.xbean.Property hidden="true"
     * @param shutdownHookEnabled true to enable the shutdown hook, false to disable
     */
    void setShutdownHookEnabled( boolean shutdownHookEnabled );


    /**
     * Checks to see if this DirectoryService has registered a JVM shutdown hook
     * to flush caches and synchronize to disk safely.  This is enabled by default.
     *
     * @return true if a shutdown hook is registered, false if it is not
     */
    boolean isShutdownHookEnabled();


    void setExitVmOnShutdown( boolean exitVmOnShutdown );


    boolean isExitVmOnShutdown();


    void setSystemPartition( Partition systemPartition );


    Partition getSystemPartition();


    boolean isDenormalizeOpAttrsEnabled();


    void setDenormalizeOpAttrsEnabled( boolean denormalizeOpAttrsEnabled );


    /**
     * Gets the ChangeLog service for this DirectoryService used for tracking
     * changes (revisions) to the server and using them to revert the server
     * to earlier revisions.
     *
     * @return the change log service
     */
    ChangeLog getChangeLog();


    /**
     * Gets the Journal service for this DirectoryService used for tracking
     * changes to the server.
     *
     * @return the journal service
     */
    Journal getJournal();


    /**
     * Sets the ChangeLog service for this DirectoryService used for tracking
     * changes (revisions) to the server and using them to revert the server
     * to earlier revisions.
     *
     * @param changeLog the change log service to set
     */
    void setChangeLog( ChangeLog changeLog );
    

    /**
     * Create a new ServerEntry.
     * 
     * @param ldif the String representing the attributes, in LDIF format
     * @param dn the DN for this new entry
     */
    ServerEntry newEntry( String ldif, String dn );
    
    
    /**
     * Gets the operation manager.
     */
    OperationManager getOperationManager();


    /**
     * @return The maximum allowed size for an incoming PDU
     */
    int getMaxPDUSize();


    /**
     * Set the maximum allowed size for an incoming PDU 
     * @param maxPDUSize A positive number of bytes for the PDU. A negative or
     * null value will be transformed to {@link Integer#MAX_VALUE}
     */
    void setMaxPDUSize( int maxPDUSize );
    
    
    /**
     * Get an Interceptor instance from its name
     * @param interceptorName The interceptor's name for which we want the instance 
     * @return
     */
    Interceptor getInterceptor( String interceptorName );
    
    
    /**
     * Get a new CSN
     * @return The CSN generated for this directory service
     */
    Csn getCSN();


    /**
     * @return the replicaId
     */
    int getReplicaId();


    /**
     * @param replicaId the replicaId to set
     */
    void setReplicaId( int replicaId );


    /**
     * Sets the replication configuration in the server.
     *
     * @param replicationConfiguration the replication configuration to be used in the server.
     */
    void setReplicationConfiguration( ReplicationConfiguration replicationConfig );
    
    
    /**
     * @return the replication configuration for this DirectoryService
     */
    ReplicationConfiguration getReplicationConfiguration();
    
    
    /**
     * Associates a SchemaManager to the service
     * 
     * @param schemaManager The SchemaManager to associate
     */
    void setSchemaManager( SchemaManager schemaManager );
}
