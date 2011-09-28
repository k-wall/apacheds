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
package org.apache.directory.server.core.interceptor.context;


import java.util.Collection;
import java.util.List;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.message.control.Control;
import org.apache.directory.shared.ldap.name.DN;


/**
 * This interface represent the context passed as an argument to each interceptor.
 * It will contain data used by all the operations.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public interface OperationContext
{
    /**
     * Checks to see if this operation is the first operation in a chain of 
     * operations performed on the DirectoryService.  The first operation in  
     * a sequence of operations, is not a byproduct of another operation 
     * unlike operations following in the sequence.  The other operations 
     * following the first, occur as a side effect to complete this first 
     * operation.
     * 
     * @return true if the operation is the first, false otherwise
     */
    boolean isFirstOperation();
    
    
    /**
     * Gets the first, direct operation issued against the DirectoryService.
     *
     * @return the first, direct operation issued 
     */
    OperationContext getFirstOperation();
    
    
    /**
     * Gets the previous, operation issued on the DirectoryService.
     *
     * @return the previous, operation issued
     */
    OperationContext getPreviousOperation();
    
    
    /**
     * Gets the next, indirect operation issued on the DirectoryService.
     *
     * @return the next, indirect operation issued 
     */
    OperationContext getNextOperation();
    
    
    /**
     * Gets the last, operation issued on the DirectoryService.
     *
     * @return the last, operation issued
     */
    OperationContext getLastOperation();


    /**
     * Gets the effective principal for this operation which may not be the 
     * same as the authenticated principal when the session for this context
     * has an explicit authorization id, or this operation was applied with 
     * the proxy authorization control.
     * 
     * @see CoreSession#getAuthenticatedPrincipal()
     * @see CoreSession#getEffectivePrincipal()
     * @return the effective principal for this operation
     */
    LdapPrincipal getEffectivePrincipal();


    /**
     * @return The associated DN
     */
    DN getDn();
    
    
    /**
     * Set the context DN
     *
     * @param dn The DN to set
     */
    void setDn( DN dn );

    
    /**
     * Gets the server entry associated with the target DN of this 
     * OperationContext.  The entry associated with the DN may be altered 
     * during the course of processing an LDAP operation through the 
     * InterceptorChain.  This place holder is put here to prevent the need
     * for repetitive lookups of the target entry.  Furthermore the returned
     * entry may be altered by any Interceptor in the chain and this is why a
     * ClonedServerEntry is returned instead of a ServerEntry.  A 
     * ClonedServerEntry has an immutable reference to the original state of
     * the target entry.  The original state can be accessed via a call to
     * {@link ClonedServerEntry#getOriginalEntry()}.  The return value may be 
     * null in which case any lookup performed to access it may set it to 
     * prevent the need for subsequent lookups.
     * 
     * Also note that during the course of handling some operations such as 
     * those that rename, move or rename and move the entry, may alter the DN 
     * of this entry.  Interceptor implementors should not presume the DN or 
     * the values contained in this entry are currently what is present in the 
     * DIT.  The original entry contained in the ClonedServerEntry shoudl be 
     * used as the definitive source of information about the state of the 
     * entry in the DIT before returning from the Partition subsystem.
     * 
     * @return target entry associated with the DN of this OperationContext
     */
    ClonedServerEntry getEntry();
    
    
    /**
     * Sets the server entry associated with the target DN of this 
     * OperationContext.
     *
     * @param entry the entry whose DN is associated with this OperationContext.
     */
    void setEntry( ClonedServerEntry entry );
    
    
    /**
     * Adds a response control to this operation.
     *
     * @param responseControl the response control to add to this operation
     */
    void addResponseControl( Control responseControl );
    
    
    /** 
     * Checks to see if a response control is present on this operation.
     *
     * @param numericOid the numeric OID of the control also known as it's type OID
     * @return true if the control is associated with this operation, false otherwise
     */
    boolean hasResponseControl( String numericOid );
    
    
    /**
     * Gets a response control if present for this request.
     * 
     * @param numericOid the numeric OID of the control also known as it's type OID
     * @return the control if present
     */
    Control getResponseControl( String numericOid );
    
    
    /**
     * Gets all the response controls producted during this operation.
     *
     * @return an array over all the response controls 
     */
    Control[] getResponseControls();
    
    
    /**
     * Checks if any response controls have been generated for this operation.
     *
     * @return true if any response controls have been generated, false otherwise
     */
    boolean hasResponseControls();
    
    
    /**
     * Checks the number of response controls have been generated for this operation.
     *
     * @return the number of response controls that have been generated
     */
    int getResponseControlCount();
    
    
    /**
     * Adds a request control to this operation.
     *
     * @param requestControl the request control to add to this operation
     */
    void addRequestControl( Control requestControl );
    
    
    /** 
     * Checks to see if a request control is present on this request.
     *
     * @param numericOid the numeric OID of the control also known as it's type OID
     * @return true if the control is associated with this operation, false otherwise
     */
    boolean hasRequestControl( String numericOid );
    
    
    /**
     * Checks if any request controls exists for this operation.
     *
     * @return true if any request controls exist, false otherwise
     */
    boolean hasRequestControls();
    
    
    /**
     * Gets a request control if present for this request.
     * 
     * @param numericOid the numeric OID of the control also known as it's type OID
     * @return the control if present
     */
    Control getRequestControl( String numericOid );


    /**
     * Adds many request controls to this operation.
     *
     * @param requestControls the request controls to add to this operation
     */
    void addRequestControls( Control[] requestControls );
    
    
    /**
     * @return the operation's name
     */
    String getName();
    
    
    /**
     * Checks to see if an Interceptor is bypassed for this operation.
     *
     * @param interceptorName the interceptorName of the Interceptor to check for bypass
     * @return true if the Interceptor should be bypassed, false otherwise
     */
    boolean isBypassed( String interceptorName );


    /**
     * Checks to see if any Interceptors are bypassed by this Invocation.
     *
     * @return true if at least one bypass exists
     */
    boolean hasBypass();
    
    
    /**
     * Gets the set of bypassed Interceptors.
     *
     * @return the set of bypassed Interceptors
     */
    Collection<String> getByPassed();
    
    
    /**
     * Sets the set of bypassed Interceptors.
     * 
     * @param byPassed the set of bypassed Interceptors
     */
    void setByPassed( Collection<String> byPassed );
    
    
    /**
     * Gets the session associated with this operation.
     *
     * @return the session associated with this operation
     */
    CoreSession getSession();
    
    
    // -----------------------------------------------------------------------
    // Utility Factory Methods to Create New OperationContexts
    // -----------------------------------------------------------------------
    
    
    LookupOperationContext newLookupContext( DN dn );

    
    ClonedServerEntry lookup( DN dn, Collection<String> byPass ) throws Exception;
    
    
    ClonedServerEntry lookup( LookupOperationContext lookupContext ) throws Exception;
    
    
    void modify( DN dn, List<Modification> mods, Collection<String> byPass ) throws Exception;
    
    
    void add( ServerEntry entry, Collection<String> byPass ) throws Exception;
    
    
    void delete( DN dn, Collection<String> byPass ) throws Exception;


    /**
     * Checks to see if an entry exists.
     *
     * @param dn the distinguished name of the entry to check
     * @param byPass collection of {@link Interceptor}'s to bypass for this check
     * @return true if the entry exists, false if it does not
     * @throws Exception on failure to perform this operation
     */
    boolean hasEntry( DN dn, Collection<String> byPass ) throws Exception;
    
    
    /**
     * Set the throwReferral flag to true
     */
    void throwReferral();
    
    
    /**
     * @return <code>true</code> if the referrals are thrown
     */
    boolean isReferralThrown();


    /**
     * Set the throwReferral flag to false
     */
    void ignoreReferral();


    /**
     * @return <code>true</code> if the referrals are ignored
     */
    boolean isReferralIgnored();
}
