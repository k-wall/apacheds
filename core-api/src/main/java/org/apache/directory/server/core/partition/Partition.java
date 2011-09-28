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


import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.entry.ServerSearchResult;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * Interface for entry stores containing a part of the DIB (Directory 
 * Information Base).  Partitions are associated with a specific suffix, and
 * all entries contained in the them have the same DN suffix in common.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 923721 $
 */
public interface Partition
{
    // -----------------------------------------------------------------------
    // C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------

    /**
     * Gets the unique identifier for this partition.
     *
     * @return the unique identifier for this partition
     */
    String getId();


    /**
     * Sets the unique identifier for this partition.
     *
     * @param id the unique identifier for this partition
     */
    void setId( String id );


    /**
     * Gets the user provided suffix for this Partition as a String.
     */
    String getSuffix();


    /**
     * Sets the user provided suffix for this Partition as a String.
     *
     * @param suffix the suffix String for this Partition.
     * @throws LdapInvalidDnException if the suffix does not conform to LDAP DN syntax
     */
    void setSuffix( String suffix ) throws LdapInvalidDnException;


    /**
     * Gets the schema manager assigned to this Partition.
     *
     * @return the schema manager
     */
    SchemaManager getSchemaManager();


    /**
     * Sets the schema manager assigned to this Partition.
     *
     * @param registries the manager to assign to this Partition.
     */
    void setSchemaManager( SchemaManager schemaManager );


    // -----------------------------------------------------------------------
    // E N D   C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------

    /**
     * Initializes this partition.
     *
     * @throws Exception if initialization fails in any way
     */
    void initialize() throws Exception;


    /**
     * Gets the normalized suffix as an DN for this Partition after it has 
     * been initialized.  Attempts to get this DN before initialization 
     * throw an IllegalStateException.
     *
     * @return the suffix for this Partition.
     * @throws IllegalStateException if the Partition has not been initialized
     */
    DN getSuffixDn();


    /**
     * Instructs this Partition to synchronize with it's persistent store, and
     * destroy all held resources, in preparation for a shutdown event.
     */
    void destroy() throws Exception;


    /**
     * Checks to see if this partition is initialized or not.
     * @return true if the partition is initialized, false otherwise
     */
    boolean isInitialized();


    /**
     * Flushes any changes made to this partition now.
     * @throws Exception if buffers cannot be flushed to disk
     */
    void sync() throws Exception;


    /**
     * Deletes a leaf entry from this ContextPartition: non-leaf entries cannot be 
     * deleted until this operation has been applied to their children.
     *
     * @param opContext the context of the entry to
     * delete from this ContextPartition.
     * @throws Exception if there are any problems
     */
    void delete( DeleteOperationContext opContext ) throws Exception;


    /**
     * Adds an entry to this ContextPartition.
     *
     * @param opContext the context used  to add and entry to this ContextPartition
     * @throws Exception if there are any problems
     */
    void add( AddOperationContext opContext ) throws Exception;


    /**
     * Modifies an entry by adding, removing or replacing a set of attributes.
     *
     * @param opContext The context containing the modification operation 
     * to perform on the entry which is one of constants specified by the 
     * DirContext interface:
     * <code>ADD_ATTRIBUTE, REMOVE_ATTRIBUTE, REPLACE_ATTRIBUTE</code>.
     * 
     * @throws Exception if there are any problems
     * @see javax.naming.directory.DirContext
     * @see javax.naming.directory.DirContext#ADD_ATTRIBUTE
     * @see javax.naming.directory.DirContext#REMOVE_ATTRIBUTE
     * @see javax.naming.directory.DirContext#REPLACE_ATTRIBUTE
     */
    void modify( ModifyOperationContext opContext ) throws Exception;


    /**
     * A specialized form of one level search used to return a minimal set of 
     * information regarding child entries under a base.  Convenience method
     * used to optimize operations rather than conducting a full search with 
     * retrieval.
     *
     * @param opContext the context containing the distinguished/absolute name for the search/listing
     * @return a NamingEnumeration containing objects of type {@link ServerSearchResult}
     * @throws Exception if there are any problems
     */
    EntryFilteringCursor list( ListOperationContext opContext ) throws Exception;


    /**
     * Conducts a search against this ContextPartition.  Namespace specific
     * parameters for search are contained within the environment using
     * namespace specific keys into the hash.  For example in the LDAP namespace
     * a ContextPartition implementation may look for search Controls using a
     * namespace specific or implementation specific key for the set of LDAP
     * Controls.
     *
     * @param opContext The context containing the information used by the operation
     * @throws Exception if there are any problems
     * @return a NamingEnumeration containing objects of type 
     */
    EntryFilteringCursor search( SearchOperationContext opContext ) throws Exception;


    /**
     * Looks up an entry by distinguished/absolute name.  This is a simplified
     * version of the search operation used to point read an entry used for
     * convenience.
     * 
     * Depending on the context parameters, we my look for a simple entry,
     * or for a restricted set of attributes for this entry
     *
     * @param lookupContext The context containing the parameters
     * @return an Attributes object representing the entry
     * @throws Exception if there are any problems
     */
    ClonedServerEntry lookup( LookupOperationContext lookupContext ) throws Exception;


    /**
     * Fast operation to check and see if a particular entry exists.
     *
     * @param opContext The context used to pass informations
     * @return true if the entry exists, false if it does not
     * @throws Exception if there are any problems
     */
    boolean hasEntry( EntryOperationContext opContext ) throws Exception;


    /**
     * Modifies an entry by changing its relative name. Optionally attributes
     * associated with the old relative name can be removed from the entry.
     * This makes sense only in certain namespaces like LDAP and will be ignored
     * if it is irrelevant.
     *
     * @param opContext the modify DN context
     * @throws Exception if there are any problems
     */
    void rename( RenameOperationContext opContext ) throws Exception;


    /**
     * Transplants a child entry, to a position in the namespace under a new
     * parent entry.
     *
     * @param opContext The context containing the DNs to move
     * @throws Exception if there are any problems
     */
    void move( MoveOperationContext opContext ) throws Exception;


    /**
     * Transplants a child entry, to a position in the namespace under a new
     * parent entry and changes the RN of the child entry which can optionally
     * have its old RN attributes removed.  The removal of old RN attributes
     * may not make sense in all namespaces.  If the concept is undefined in a
     * namespace this parameters is ignored.  An example of a namespace where
     * this parameter is significant is the LDAP namespace.
     *
     * @param opContext The context contain all the information about
     * the modifyDN operation
     * @throws Exception if there are any problems
     */
    void moveAndRename( MoveAndRenameOperationContext opContext ) throws Exception;


    /**
     * Represents a bind operation issued to authenticate a client.  Partitions
     * need not support this operation.  This operation is here to enable those
     * interested in implementing virtual directories with ApacheDS.
     * 
     * @param opContext the bind context, containing all the needed informations to bind
     * @throws Exception if something goes wrong
     */
    void bind( BindOperationContext opContext ) throws Exception;


    /**
     * Represents an unbind operation issued by an authenticated client.  Partitions
     * need not support this operation.  This operation is here to enable those
     * interested in implementing virtual directories with ApacheDS.
     * 
     * @param opContext the context used to unbind
     * @throws Exception if something goes wrong
     */
    void unbind( UnbindOperationContext opContext ) throws Exception;
}
