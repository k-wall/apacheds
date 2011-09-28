/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.core;


import java.net.SocketAddress;
import java.util.List;
import java.util.Set;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.changelog.LogChange;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.control.Control;
import org.apache.directory.shared.ldap.message.internal.InternalAddRequest;
import org.apache.directory.shared.ldap.message.internal.InternalCompareRequest;
import org.apache.directory.shared.ldap.message.internal.InternalDeleteRequest;
import org.apache.directory.shared.ldap.message.internal.InternalModifyDnRequest;
import org.apache.directory.shared.ldap.message.internal.InternalModifyRequest;
import org.apache.directory.shared.ldap.message.internal.InternalSearchRequest;
import org.apache.directory.shared.ldap.message.internal.InternalUnbindRequest;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.AttributeTypeOptions;


/**
 * An interface representing a session with the core DirectoryService. These 
 * sessions may either be real representing LDAP sessions associated with an 
 * actual LDAP network client, or may be virtual in which case there is no 
 * real LDAP client associated with the session.  This interface is used by 
 * the DirectoryService core to track session specific parameters used to make 
 * various decisions during the course of operation handling.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public interface CoreSession
{
    /**
     * Gets the DirectoryService this session is bound to.
     *
     * @return the DirectoryService associated with this session
     */
    DirectoryService getDirectoryService();

    
    /**
     * Gets the LDAP principal used to authenticate.  This is the identity 
     * used to establish this session on authentication.
     *
     * @return the LdapPrincipal used to authenticate.
     */
    LdapPrincipal getAuthenticatedPrincipal();
    
    
    /**
     * Gets the LDAP principal used for the effective identity associated with
     * this session which may not be the same as the authenticated principal.  
     * This principal is often the same as the authenticated principal.  
     * Sometimes however, a user authenticating as one principal, may request 
     * to have all operations performed in the session as if they were another 
     * principal.  The SASL mechanism allows setting an authorized principal 
     * which is in effect for the duration of the session.  In this case all 
     * operations are performed as if they are being performed by this 
     * principal.  This method will then return the authorized principal which
     * will be different from the authenticated principal.
     * 
     * Implementations of this interface may have a means to set the 
     * authorized principal which may or may not be the same as the 
     * authenticated principal.  Implementations should default to return the 
     * authenticated principal when an authorized principal is not provided.
     *
     * @return the LdapPrincipal to use as the effective principal
     */
    LdapPrincipal getEffectivePrincipal();

    
    /**
     * Gets whether or not confidentiality is enabled for this session.
     * 
     * @return true if confidentiality is enabled, false otherwise
     */
    boolean isConfidential();
    
    
    /**
     * Gets whether or not this user is anonymous.
     *
     * @return true if the identity is anonymous false otherwise
     */
    boolean isAnonymous();
    
    
    /**
     * Returns true if the effective principal associated with this session is 
     * the administrator.
     * 
     * @see {@link ServerDNConstants#ADMIN_SYSTEM_DN}
     * @return true if authorized as the administrator, false otherwise
     */
    boolean isAdministrator();
    
    
    /**
     * Returns true if the effective principal associated with this session is 
     * the administrator or is within the administrators group.
     *
     * @see {@link ServerDNConstants#ADMIN_SYSTEM_DN}
     * @see {@link ServerDNConstants#ADMINISTRATORS_GROUP_DN}
     * @return true if authorized as an administrator, false otherwise
     */
    boolean isAnAdministrator();
    
    
    /**
     * Gets the authentication level associated with this session.
     * 
     * @return the authentication level associated with the session
     */
    AuthenticationLevel getAuthenticationLevel();
    
    
    /**
     * Gets the controls enabled for this session.
     * 
     * @return the session controls as a Set
     */
    Set<Control> getControls();
    
    
    /**
     * Gets all outstanding operations currently being performed that have yet 
     * to be completed.
     * 
     * @return the set of outstanding operations
     */
    Set<OperationContext> getOutstandingOperations();

    
    /**
     * Gets whether or not this session is virtual.  Virtual sessions verses 
     * real sessions represent logical sessions established by non-LDAP 
     * services or embedding applications which do not expose the LDAP access.
     *
     * @return true if the session is virtual, false otherwise
     */
    boolean isVirtual();
    
    
    /**
     * Gets the socket address of the LDAP client or null if there is no LDAP
     * client associated with the session.  Some calls to the core can be made
     * by embedding applications or by non-LDAP services using a programmatic
     * (virtual) session.  In these cases no client address is available.
     * 
     * @return null if the session is virtual, non-null when the session is 
     * associated with a real LDAP client
     */
    SocketAddress getClientAddress();


    /**
     * Gets the socket address of the LDAP server or null if there is no LDAP
     * service associated with the session.  Some calls to the core can be 
     * made by embedding applications or by non-LDAP services using a 
     * programmatic (virtual) session.  In these cases no service address is 
     * available.
     * 
     * @return null if the session is virtual, non-null when the session is 
     * associated with a real LDAP service
     */
    SocketAddress getServiceAddress();
    
    
    // -----------------------------------------------------------------------
    // Operation Methods
    // -----------------------------------------------------------------------
    /**
     * Adds an entry into the DirectoryService associated with this CoreSession.
     * 
     * @param entry the entry to add
     * @exception Exception on failures to add the entry
     */
    void add( ServerEntry entry ) throws Exception;
    
    
    /**
     * Adds an entry into the DirectoryService associated with this CoreSession.
     * 
     * @param entry the entry to add
     * @param log a flag set if the added entry should be stored in the changeLog
     * @exception Exception on failures to add the entry
     */
    void add( ServerEntry entry, LogChange log ) throws Exception;
    
    
    /**
     * Adds an entry into the DirectoryService associated with this CoreSession.
     * The flag is used to tell the server to ignore the referrals and manipulate
     * them as if they were normal entries.
     * 
     * @param entry the entry to add
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @exception Exception on failures to add the entry
     */
    void add( ServerEntry entry, boolean ignoreReferral ) throws Exception;
    
    
    /**
     * Adds an entry into the DirectoryService associated with this CoreSession.
     * The flag is used to tell the server to ignore the referrals and manipulate
     * them as if they were normal entries.
     * 
     * @param entry the entry to add
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @param log a flag set if the added entry should be stored in the changeLog
     * @exception Exception on failures to add the entry
     */
    void add( ServerEntry entry, boolean ignoreReferral, LogChange log ) throws Exception;
    
    
    /**
     * Adds an entry into the DirectoryService associated with this CoreSession.
     * The entry is built using the received AddRequest.
     * 
     * @param InternalAddRequest the request to execute
     * @exception Exception on failures to add the entry
     */
    void add( InternalAddRequest addRequest ) throws Exception;
    
    
    /**
     * Adds an entry into the DirectoryService associated with this CoreSession.
     * The entry is built using the received AddRequest.
     * 
     * @param InternalAddRequest the request to execute
     * @param log a flag set if the added entry should be stored in the changeLog
     * @exception Exception on failures to add the entry
     */
    void add( InternalAddRequest addRequest, LogChange log ) throws Exception;
    
    
    /**
     * Checks to see if an attribute in an entry contains a value.
     *
     * @param dn the distinguished name of the entry to check
     * @param oid the OID of the attribute to check for the value
     * @param value the value to check for
     * @throws Exception if there are failures while comparing
     */
    boolean compare( DN dn, String oid, Object value ) throws Exception;
    
    
    /**
     * Checks to see if an attribute in an entry contains a value.
     * The flag is used to tell the server to ignore the referrals and manipulate
     * them as if they were normal entries.
     *
     * @param dn the distinguished name of the entry to check
     * @param oid the OID of the attribute to check for the value
     * @param value the value to check for
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @throws Exception if there are failures while comparing
     */
    boolean compare( DN dn, String oid, Object value, boolean ignoreReferral ) throws Exception;
    
    
    /**
     * Checks to see if an attribute in an entry contains a value.
     *
     * @param compareRequest the received request
     * @throws Exception if there are failures while comparing
     */
    boolean compare( InternalCompareRequest compareRequest ) throws Exception;

    
    /**
     * Deletes an entry in the server.
     *
     * @param dn the distinguished name of the entry to delete
     * @throws Exception if there are failures while deleting the entry
     */
    void delete( DN dn ) throws Exception;

    
    /**
     * Deletes an entry in the server.
     *
     * @param dn the distinguished name of the entry to delete
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while deleting the entry
     */
    void delete( DN dn, LogChange log ) throws Exception;
    
    
    void delete( InternalDeleteRequest deleteRequest ) throws Exception;
    
    
    void delete( InternalDeleteRequest deleteRequest, LogChange log ) throws Exception;

    
    /**
     * Deletes an entry in the server.
     * The flag is used to tell the server to ignore the referrals and manipulate
     * them as if they were normal entries.
     *
     * @param dn the distinguished name of the entry to delete
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @throws Exception if there are failures while deleting the entry
     */
    void delete( DN dn, boolean ignoreReferral ) throws Exception;
    
    
    /**
     * Deletes an entry in the server.
     * The flag is used to tell the server to ignore the referrals and manipulate
     * them as if they were normal entries.
     *
     * @param dn the distinguished name of the entry to delete
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while deleting the entry
     */
    void delete( DN dn, boolean ignoreReferral, LogChange log ) throws Exception;
    
    
    /**
     * Checks to see if an entry exists. 
     */
    boolean exists( DN dn ) throws Exception;
    
    
    /**
     * Looks up an entry in the server returning all attributes: both user and
     * operational attributes.
     *
     * @param dn the name of the entry to lookup
     * @throws Exception if there are failures while looking up the entry
     */
    ClonedServerEntry lookup( DN dn ) throws Exception;

    /**
     * Looks up an entry in the server returning all attributes: both user and
     * operational attributes.
     *
     * @param dn the name of the entry to lookup
     * @param atIds The list of attributes to return
     * @throws Exception if there are failures while looking up the entry
     */
    ClonedServerEntry lookup( DN dn, String[] atIds ) throws Exception;

    
    /**
     * Modifies an entry within the server by applying a list of modifications 
     * to the entry.
     *
     * @param dn the distinguished name of the entry to modify
     * @param mods the list of modifications to apply
     * @throws Exception if there are failures while modifying the entry
     */
    void modify( DN dn, List<Modification> mods ) throws Exception;
    
    
    /**
     * Modifies an entry within the server by applying a list of modifications 
     * to the entry.
     *
     * @param dn the distinguished name of the entry to modify
     * @param mods the list of modifications to apply
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while modifying the entry
     */
    void modify( DN dn, List<Modification> mods, LogChange log ) throws Exception;
    
    
    /**
     * Modifies an entry within the server by applying a list of modifications 
     * to the entry.
     * The flag is used to tell the server to ignore the referrals and manipulate
     * them as if they were normal entries.
     *
     * @param dn the distinguished name of the entry to modify
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @param mods the list of modifications to apply
     * @throws Exception if there are failures while modifying the entry
     */
    void modify( DN dn, List<Modification> mods, boolean ignoreReferral ) throws Exception;
    
    
    /**
     * Modifies an entry within the server by applying a list of modifications 
     * to the entry.
     * The flag is used to tell the server to ignore the referrals and manipulate
     * them as if they were normal entries.
     *
     * @param dn the distinguished name of the entry to modify
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @param mods the list of modifications to apply
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while modifying the entry
     */
    void modify( DN dn, List<Modification> mods, boolean ignoreReferral, LogChange log ) throws Exception;
    
    
    void modify( InternalModifyRequest modifyRequest ) throws Exception;
    
    
    void modify( InternalModifyRequest modifyRequest, LogChange log ) throws Exception;

    
    /**
     * Moves an entry or a branch of entries at a specified distinguished name
     * to a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @exception if there are failures while moving the entry/branch
     */
    void move( DN dn, DN newParent ) throws Exception;
    
    
    /**
     * Moves an entry or a branch of entries at a specified distinguished name
     * to a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @param log a flag set if the added entry should be stored in the changeLog
     * @exception if there are failures while moving the entry/branch
     */
    void move( DN dn, DN newParent, LogChange log ) throws Exception;
    
    
    /**
     * Moves an entry or a branch of entries at a specified distinguished name
     * to a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @exception if there are failures while moving the entry/branch
     */
    void move( DN dn, DN newParent, boolean ignoreReferral ) throws Exception;
    
    
    /**
     * Moves an entry or a branch of entries at a specified distinguished name
     * to a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @param log a flag set if the added entry should be stored in the changeLog
     * @exception if there are failures while moving the entry/branch
     */
    void move( DN dn, DN newParent, boolean ignoreReferral, LogChange log ) throws Exception;
    
    
    /**
     * Move an entry by changing its superior.
     *
     * @param modifyDnRequest The ModifyDN request
     * @throws Exception if there are failures while moving the entry/branch
     */
    void move( InternalModifyDnRequest modifyDnRequest ) throws Exception;
    
    
    /**
     * Move an entry by changing its superior.
     *
     * @param modifyDnRequest The ModifyDN request
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while moving the entry/branch
     */
    void move( InternalModifyDnRequest modifyDnRequest, LogChange log ) throws Exception;
    
    
    /**
     * Moves and renames (the relative distinguished name of) an entry (or a 
     * branch if the entry has children) at a specified distinguished name to 
     * a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @param newRdn the new relative distinguished name of the entry at the 
     * root of the branch
     * @exception if there are failures while moving and renaming the entry
     * or branch
     */
    void moveAndRename( DN dn, DN newParent, RDN newRdn, boolean deleteOldRdn ) throws Exception;
    
    
    /**
     * Moves and renames (the relative distinguished name of) an entry (or a 
     * branch if the entry has children) at a specified distinguished name to 
     * a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @param newRdn the new relative distinguished name of the entry at the 
     * root of the branch
     * @param log a flag set if the added entry should be stored in the changeLog
     * @exception if there are failures while moving and renaming the entry
     * or branch
     */
    void moveAndRename( DN dn, DN newParent, RDN newRdn, boolean deleteOldRdn, LogChange log ) throws Exception;
    
    
    /**
     * Moves and renames (the relative distinguished name of) an entry (or a 
     * branch if the entry has children) at a specified distinguished name to 
     * a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @param newRdn the new relative distinguished name of the entry at the 
     * root of the branch
     * @param ignoreReferral  a flag to tell the server to ignore referrals
     * @exception if there are failures while moving and renaming the entry
     * or branch
     */
    void moveAndRename( DN dn, DN newParent, RDN newRdn, boolean deleteOldRdn, boolean ignoreReferral ) throws Exception;
    
    
    /**
     * Moves and renames (the relative distinguished name of) an entry (or a 
     * branch if the entry has children) at a specified distinguished name to 
     * a position under a new parent.
     * 
     * @param dn the distinguished name of the entry/branch to move
     * @param newParent the new parent under which the entry/branch is moved
     * @param newRdn the new relative distinguished name of the entry at the 
     * root of the branch
     * @param ignoreReferral  a flag to tell the server to ignore referrals
     * @param log a flag set if the added entry should be stored in the changeLog
     * @exception if there are failures while moving and renaming the entry
     * or branch
     */
    void moveAndRename( DN dn, DN newParent, RDN newRdn, boolean deleteOldRdn, boolean ignoreReferral, LogChange log ) throws Exception;
    
    
    /**
     * Move and rename an entry. We change the RDN and the superior.
     *
     * @param modifyDnRequest The move and rename request
     * @throws Exception if there are failures while moving and renaming the entry
     * or branch
     */
    void moveAndRename( InternalModifyDnRequest modifyDnRequest ) throws Exception;
    
    
    /**
     * Move and rename an entry. We change the RDN and the superior.
     *
     * @param modifyDnRequest The move and rename request
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while moving and renaming the entry
     * or branch
     */
    void moveAndRename( InternalModifyDnRequest modifyDnRequest, LogChange log ) throws Exception;
    
    
    /**
     * Renames an entry by changing it's relative distinguished name.  This 
     * has the side effect of changing the distinguished name of all entries
     * directly or indirectly subordinate to the named entry if it has 
     * descendants.
     *
     * @param dn the distinguished name of the entry to rename
     * @param newRdn the new relative distinguished name for the entry
     * @param deleteOldRdn whether or not the old value for the relative 
     * distinguished name is to be deleted from the entry
     * @throws Exception if there are failures while renaming the entry
     */
    void rename( DN dn, RDN newRdn, boolean deleteOldRdn ) throws Exception;
    
    
    /**
     * Renames an entry by changing it's relative distinguished name.  This 
     * has the side effect of changing the distinguished name of all entries
     * directly or indirectly subordinate to the named entry if it has 
     * descendants.
     *
     * @param dn the distinguished name of the entry to rename
     * @param newRdn the new relative distinguished name for the entry
     * @param deleteOldRdn whether or not the old value for the relative 
     * distinguished name is to be deleted from the entry
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while renaming the entry
     */
    void rename( DN dn, RDN newRdn, boolean deleteOldRdn, LogChange log ) throws Exception;
    
    
    /**
     * Renames an entry by changing it's relative distinguished name.  This 
     * has the side effect of changing the distinguished name of all entries
     * directly or indirectly subordinate to the named entry if it has 
     * descendants.
     *
     * @param dn the distinguished name of the entry to rename
     * @param newRdn the new relative distinguished name for the entry
     * @param deleteOldRdn whether or not the old value for the relative 
     * distinguished name is to be deleted from the entry
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @throws Exception if there are failures while renaming the entry
     */
    void rename( DN dn, RDN newRdn, boolean deleteOldRdn, boolean ignoreReferral ) throws Exception;
    
    
    /**
     * Renames an entry by changing it's relative distinguished name.  This 
     * has the side effect of changing the distinguished name of all entries
     * directly or indirectly subordinate to the named entry if it has 
     * descendants.
     *
     * @param dn the distinguished name of the entry to rename
     * @param newRdn the new relative distinguished name for the entry
     * @param deleteOldRdn whether or not the old value for the relative 
     * distinguished name is to be deleted from the entry
     * @param ignoreReferral a flag to tell the server to ignore referrals
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while renaming the entry
     */
    void rename( DN dn, RDN newRdn, boolean deleteOldRdn, boolean ignoreReferral, LogChange log ) throws Exception;
    
    
    /**
     * Rename an entry applying the ModifyDN request 
     *
     * @param modifyDnRequest The requested modification
     * @throws Exception if there are failures while renaming the entry
     */
    void rename( InternalModifyDnRequest modifyDnRequest ) throws Exception;
    
    
    /**
     * Rename an entry applying the ModifyDN request 
     *
     * @param modifyDnRequest The requested modification
     * @param log a flag set if the added entry should be stored in the changeLog
     * @throws Exception if there are failures while renaming the entry
     */
    void rename( InternalModifyDnRequest modifyDnRequest, LogChange log ) throws Exception;
    
    
    /**
     * An optimized search operation using one level search scope which 
     * returns all the children of an entry specified by distinguished name.
     * This is equivalent to a search operation with one level scope using
     * the <code>(objectClass=*)</code> filter.
     *
     * @param dn the distinguished name of the entry to list the children of
     * @param aliasDerefMode the alias dereferencing mode used
     * @param returningAttributes the attributes to return
     * @throws Exception if there are failures while listing children
     */
    EntryFilteringCursor list( DN dn, AliasDerefMode aliasDerefMode, 
        Set<AttributeTypeOptions> returningAttributes ) throws Exception;
    
    
    /**
     * An optimized search operation using one level search scope which 
     * applies size and time limit constraints and returns all the children 
     * of an entry specified by distinguished name if thes limits are not
     * violated.  This is equivalent to a search operation with one level 
     * scope using the <code>(objectClass=*)</code> filter.
     *
     * @param dn the distinguished name of the entry to list the children of
     * @param aliasDerefMode the alias dereferencing mode used
     * @param returningAttributes the attributes to return
     * @param sizeLimit the upper bound to the number of entries to return
     * @param timeLimit the upper bound to the amount of time before 
     * terminating the search
     * @throws Exception if there are failures while listing children
     */
    EntryFilteringCursor list( DN dn, AliasDerefMode aliasDerefMode, 
        Set<AttributeTypeOptions> returningAttributes, long sizeLimit, int timeLimit ) throws Exception;
    
    
    /**
     * Searches the directory using a specified filter. The scope is defaulting
     * to 'base'. The alias dereferencing default to 'always'. the returned attributes 
     * defaults to 'all the user attributes)
     *
     * @param dn the distinguished name of the entry to list the children of
     * @param filter the search filter
     * @throws Exception if there are failures while listing children
     */
    EntryFilteringCursor search( DN dn, String filter ) throws Exception;
    
    
    /**
     * Searches the directory using a specified filter. The scope is defaulting
     * to 'base'. The alias dereferencing default to 'always'. the returned attributes 
     * defaults to 'all the user attributes)
     *
     * @param dn the distinguished name of the entry to list the children of
     * @param filter the search filter
     * @param ignoreReferrals a flag to tell the server to ignore referrals
     * @throws Exception if there are failures while listing children
     */
    EntryFilteringCursor search( DN dn, String filter, boolean ignoreReferrals ) throws Exception;
    
    
    /**
     * Searches the directory using a specified search scope and filter.
     *
     * @param dn the distinguished name of the entry to list the children of
     * @param scope the search scope to apply
     * @param filter the search filter
     * @param aliasDerefMode the alias dereferencing mode used
     * @param returningAttributes the attributes to return
     * @throws Exception if there are failures while listing children
     */
    EntryFilteringCursor search( DN dn, SearchScope scope, ExprNode filter, AliasDerefMode aliasDerefMode, 
        Set<AttributeTypeOptions> returningAttributes ) throws Exception;
    
    
    /**
     * Searches the directory using a specified search scope and filter.
     *
     * @param dn the distinguished name of the entry to list the children of
     * @param scope the search scope to apply
     * @param filter the search filter
     * @param aliasDerefMode the alias dereferencing mode used
     * @param returningAttributes the attributes to return
     * @throws Exception if there are failures while listing children
     */
    
    
    /**
     * Searches the directory using a specified search scope and filter.
     *
     * @param dn the distinguished name of the entry to list the children of
     * @param aliasDerefMode the alias dereferencing mode used
     * @param returningAttributes the attributes to return
     * @param sizeLimit the upper bound to the number of entries to return
     * @param timeLimit the upper bound to the amount of time before 
     * terminating the search
     * @throws Exception if there are failures while listing children
     */
    EntryFilteringCursor search( DN dn, SearchScope scope, ExprNode filter, AliasDerefMode aliasDerefMode, 
        Set<AttributeTypeOptions> returningAttributes, long sizeLimit, int timeLimit ) throws Exception;


    EntryFilteringCursor search( InternalSearchRequest searchRequest ) throws Exception;


    void unbind() throws Exception;
    
    
    void unbind( InternalUnbindRequest unbindRequest ) throws Exception;
}
