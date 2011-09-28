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
package org.apache.directory.server.ldap.handlers;


import static java.lang.Math.min;
import static org.apache.directory.server.ldap.LdapServer.NO_SIZE_LIMIT;
import static org.apache.directory.server.ldap.LdapServer.NO_TIME_LIMIT;

import java.util.concurrent.TimeUnit;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.ReferralManager;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.event.EventType;
import org.apache.directory.server.core.event.NotificationCriteria;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.server.ldap.handlers.controls.PagedSearchContext;
import org.apache.directory.shared.ldap.codec.controls.ManageDsaITControl;
import org.apache.directory.shared.ldap.codec.search.controls.pagedSearch.PagedResultsControl;
import org.apache.directory.shared.ldap.codec.search.controls.persistentSearch.PersistentSearchControl;
import org.apache.directory.shared.ldap.codec.util.LdapURLEncodingException;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.OperationAbandonedException;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.ReferralImpl;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.message.SearchResponseEntryImpl;
import org.apache.directory.shared.ldap.message.SearchResponseReferenceImpl;
import org.apache.directory.shared.ldap.message.internal.InternalLdapResult;
import org.apache.directory.shared.ldap.message.internal.InternalReferral;
import org.apache.directory.shared.ldap.message.internal.InternalResponse;
import org.apache.directory.shared.ldap.message.internal.InternalSearchRequest;
import org.apache.directory.shared.ldap.message.internal.InternalSearchResponseDone;
import org.apache.directory.shared.ldap.message.internal.InternalSearchResponseEntry;
import org.apache.directory.shared.ldap.message.internal.InternalSearchResponseReference;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.util.LdapURL;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A handler for processing search requests.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 664302 $
 */
public class SearchHandler extends ReferralAwareRequestHandler<InternalSearchRequest>
{
    private static final Logger LOG = LoggerFactory.getLogger( SearchHandler.class );

    /** Speedup for logs */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** cached to save redundant lookups into registries */ 
    private AttributeType objectClassAttributeType;
    
    
    /**
     * Constructs a new filter EqualityNode asserting that a candidate 
     * objectClass is a referral.
     *
     * @param session the {@link LdapSession} to construct the node for
     * @return the {@link EqualityNode} (objectClass=referral) non-normalized
     * @throws Exception in the highly unlikely event of schema related failures
     */
    private EqualityNode<String> newIsReferralEqualityNode( LdapSession session ) throws Exception
    {
        if ( objectClassAttributeType == null )
        {
            objectClassAttributeType = session.getCoreSession().getDirectoryService().
                getSchemaManager().lookupAttributeTypeRegistry( SchemaConstants.OBJECT_CLASS_AT );
        }
        
        EqualityNode<String> ocIsReferral = new EqualityNode<String>( SchemaConstants.OBJECT_CLASS_AT,
            new StringValue( objectClassAttributeType, SchemaConstants.REFERRAL_OC ) );
        
        return ocIsReferral;
    }
    
    
    /**
     * Handles search requests containing the persistent search control but 
     * delegates to doSimpleSearch() if the changesOnly parameter of the 
     * control is set to false.
     *
     * @param session the LdapSession for which this search is conducted 
     * @param req the search request containing the persistent search control
     * @param psearchControl the persistent search control extracted
     * @throws Exception if failures are encountered while searching
     */
    private void handlePersistentSearch( LdapSession session, InternalSearchRequest req, 
        PersistentSearchControl psearchControl ) throws Exception 
    {
        /*
         * We want the search to complete first before we start listening to 
         * events when the control does NOT specify changes ONLY mode.
         */
        if ( ! psearchControl.isChangesOnly() )
        {
            InternalSearchResponseDone done = doSimpleSearch( session, req );
            
            // ok if normal search beforehand failed somehow quickly abandon psearch
            if ( done.getLdapResult().getResultCode() != ResultCodeEnum.SUCCESS )
            {
                session.getIoSession().write( done );
                return;
            }
        }

        if ( req.isAbandoned() )
        {
            return;
        }
        
        // now we process entries forever as they change
        PersistentSearchListener handler = new PersistentSearchListener( session, req );
        
        // compose notification criteria and add the listener to the event 
        // service using that notification criteria to determine which events 
        // are to be delivered to the persistent search issuing client
        NotificationCriteria criteria = new NotificationCriteria();
        criteria.setAliasDerefMode( req.getDerefAliases() );
        criteria.setBase( req.getBase() );
        criteria.setFilter( req.getFilter() );
        criteria.setScope( req.getScope() );
        criteria.setEventMask( EventType.getEventTypes( psearchControl.getChangeTypes() ) );
        getLdapServer().getDirectoryService().getEventService().addListener( handler, criteria );
        req.addAbandonListener( new SearchAbandonListener( ldapServer, handler ) );
        return;
    }
    
    
    /**
     * Handles search requests on the RootDSE. 
     * 
     * @param session the LdapSession for which this search is conducted 
     * @param req the search request on the RootDSE
     * @throws Exception if failures are encountered while searching
     */
    private void handleRootDseSearch( LdapSession session, InternalSearchRequest req ) throws Exception
    {
        EntryFilteringCursor cursor = null;
        
        try
        {
            cursor = session.getCoreSession().search( req );
            
            // Position the cursor at the beginning
            cursor.beforeFirst();
            boolean hasRootDSE = false;
            
            while ( cursor.next() )
            {
                if ( hasRootDSE )
                {
                    // This is an error ! We should never find more than one rootDSE !
                    LOG.error( I18n.err( I18n.ERR_167 ) );
                }
                else
                {
                    hasRootDSE = true;
                    ClonedServerEntry entry = cursor.get();
                    session.getIoSession().write( generateResponse( session, req, entry ) );
                }
            }
    
            // write the SearchResultDone message
            session.getIoSession().write( req.getResultResponse() );
        }
        finally
        {
            // Close the cursor now.
            if ( cursor != null )
            {
                try
                {
                    cursor.close();
                }
                catch ( Exception e )
                {
                    LOG.error( I18n.err( I18n.ERR_168 ), e );
                }
            }
        }
    }
    
    
    /**
     * Based on the server maximum time limits configured for search and the 
     * requested time limits this method determines if at all to replace the 
     * default ClosureMonitor of the result set Cursor with one that closes
     * the Cursor when either server mandated or request mandated time limits 
     * are reached.
     *
     * @param req the {@link InternalSearchRequest} issued
     * @param session the {@link LdapSession} on which search was requested
     * @param cursor the {@link EntryFilteringCursor} over the search results
     */
    private void setTimeLimitsOnCursor( InternalSearchRequest req, LdapSession session, final EntryFilteringCursor cursor )
    {
        // Don't bother setting time limits for administrators
        if ( session.getCoreSession().isAnAdministrator() && req.getTimeLimit() == NO_TIME_LIMIT )
        {
            return;
        }
        
        /*
         * Non administrator based searches are limited by time if the server 
         * has been configured with unlimited time and the request specifies 
         * unlimited search time
         */
        if ( ldapServer.getMaxTimeLimit() == NO_TIME_LIMIT && req.getTimeLimit() == NO_TIME_LIMIT )
        {
            return;
        }
        
        /*
         * If the non-administrator user specifies unlimited time but the server 
         * is configured to limit the search time then we limit by the max time 
         * allowed by the configuration 
         */
        if ( req.getTimeLimit() == 0 )
        {
            cursor.setClosureMonitor( new SearchTimeLimitingMonitor( ldapServer.getMaxTimeLimit(), TimeUnit.SECONDS ) );
            return;
        }
        
        /*
         * If the non-administrative user specifies a time limit equal to or 
         * less than the maximum limit configured in the server then we 
         * constrain search by the amount specified in the request
         */
        if ( ldapServer.getMaxTimeLimit() >= req.getTimeLimit() )
        {
            cursor.setClosureMonitor( new SearchTimeLimitingMonitor( req.getTimeLimit(), TimeUnit.SECONDS ) );
            return;
        }

        /*
         * Here the non-administrative user's requested time limit is greater 
         * than what the server's configured maximum limit allows so we limit
         * the search to the configured limit
         */
        cursor.setClosureMonitor( new SearchTimeLimitingMonitor( ldapServer.getMaxTimeLimit(), TimeUnit.SECONDS ) );
    }
    
    
    /**
     * Return the server size limit
     */
    private long getServerSizeLimit( LdapSession session, InternalSearchRequest request )
    {
        if ( session.getCoreSession().isAnAdministrator() )
        {
            if ( request.getSizeLimit() == NO_SIZE_LIMIT )
            {
                return Long.MAX_VALUE;
            }
            else
            {
                return request.getSizeLimit();
            }
        }
        else
        {
            if ( ldapServer.getMaxSizeLimit() == NO_SIZE_LIMIT )
            {
                return Long.MAX_VALUE;
            }
            else
            {
                return ldapServer.getMaxSizeLimit();
            }
        }
    }
    
    
    private void readResults( LdapSession session, InternalSearchRequest req, InternalLdapResult ldapResult,
    EntryFilteringCursor cursor, long sizeLimit ) throws Exception
    {
        long count = 0;

        while ( (count < sizeLimit ) && cursor.next() )
        {
            // Handle closed session
            if ( session.getIoSession().isClosing() )
            {
                // The client has closed the connection
                LOG.debug( "Request terminated for message {}, the client has closed the session", 
                    req.getMessageId() );
                break;
            }

            if ( req.isAbandoned() )
            {
                // The cursor has been closed by an abandon request.
                LOG.debug( "Request terminated by an AbandonRequest for message {}", 
                    req.getMessageId() );
                break;
            }
            
            ClonedServerEntry entry = cursor.get();
            session.getIoSession().write( generateResponse( session, req, entry ) );
            LOG.debug( "Sending {}", entry.getDn() );
            count++;
        }
        
        // DO NOT WRITE THE RESPONSE - JUST RETURN IT
        ldapResult.setResultCode( ResultCodeEnum.SUCCESS );

        if ( ( count >= sizeLimit ) && ( cursor.next() ) )
        {
            // We have reached the limit
            // Move backward on the cursor to restore the previous position, as we moved forward
            // to check if there is one more entry available
            cursor.previous();
            // Special case if the user has requested more elements than the request size limit
            ldapResult.setResultCode( ResultCodeEnum.SIZE_LIMIT_EXCEEDED );
        }
    }
    
    
    private void readPagedResults( LdapSession session, InternalSearchRequest req, InternalLdapResult ldapResult,  
        EntryFilteringCursor cursor, long sizeLimit, int pagedLimit, boolean isPaged, 
        PagedSearchContext pagedContext, PagedResultsControl pagedResultsControl ) throws Exception
    {
        req.addAbandonListener( new SearchAbandonListener( ldapServer, cursor ) );
        setTimeLimitsOnCursor( req, session, cursor );
        LOG.debug( "using <{},{}> for size limit", sizeLimit, pagedLimit );
        long cookieValue = 0;
        
        int count = pagedContext.getCurrentPosition();
        int pageCount = 0;
        
        while ( ( count < sizeLimit ) && ( pageCount < pagedLimit ) && cursor.next() )
        {
            if ( session.getIoSession().isClosing() )
            {
                break;
            }
            
            ClonedServerEntry entry = cursor.get();
            session.getIoSession().write( generateResponse( session, req, entry ) );
            count++;
            pageCount++;
        }
        
        // DO NOT WRITE THE RESPONSE - JUST RETURN IT
        ldapResult.setResultCode( ResultCodeEnum.SUCCESS );
        
        boolean hasMoreEntry = cursor.next();
        
        if ( hasMoreEntry )
        {
            cursor.previous();
        }

        if ( !hasMoreEntry )
        {
            // That means we don't have anymore entry
            // If we are here, it means we have returned all the entries
            // We have to remove the cookie from the session
            cookieValue = pagedContext.getCookieValue();
            PagedSearchContext psCookie = session.removePagedSearchContext( cookieValue );
            
            // Close the cursor if there is one
            if ( psCookie != null )
            {
                cursor = psCookie.getCursor();
                
                if ( cursor != null )
                {
                    cursor.close();
                }
            }
            
            pagedResultsControl = new PagedResultsControl();
            pagedResultsControl.setCritical( true );
            pagedResultsControl.setSize( 0 );
            req.getResultResponse().add( pagedResultsControl );

            return;
        }
        else
        {
            // We have reached one limit
            
            if ( count < sizeLimit )
            {
                // We stop here. We have to add a ResponseControl
                // DO NOT WRITE THE RESPONSE - JUST RETURN IT
                ldapResult.setResultCode( ResultCodeEnum.SUCCESS );
                req.getResultResponse().add( pagedResultsControl );
                
                // Stores the cursor current position 
                pagedContext.incrementCurrentPosition( pageCount );
                return;
            }
            else
            {
                // Return an exception, close the cursor, and clean the session
                ldapResult.setResultCode( ResultCodeEnum.SIZE_LIMIT_EXCEEDED );
                
                if ( cursor != null )
                {
                    cursor.close();
                }
                
                session.removePagedSearchContext( cookieValue );
                
                return;
            }
        }
    }
    
    
    /**
     * Manage the abandoned Paged Search (when paged size = 0). We have to
     * remove the cookie and its associated cursor from the session.
     */
    private InternalSearchResponseDone abandonPagedSearch( LdapSession session, InternalSearchRequest req ) 
        throws Exception
    {
        PagedResultsControl pagedResultsControl = null;
        PagedResultsControl pagedSearchControl = 
            ( PagedResultsControl )req.getControls().get( PagedResultsControl.CONTROL_OID );
        byte [] cookie= pagedSearchControl.getCookie();
        
        if ( !StringTools.isEmpty( cookie ) )
        {
            // If the cookie is not null, we have to destroy the associated
            // cursor stored into the session (if any)
            int cookieValue = pagedSearchControl.getCookieValue();
            PagedSearchContext psCookie =  session.removePagedSearchContext( cookieValue );
            pagedResultsControl = new PagedResultsControl();
            pagedResultsControl.setCookie( psCookie.getCookie() );
            pagedResultsControl.setSize( 0 );
            pagedResultsControl.setCritical( true );
            
            // Close the cursor
            EntryFilteringCursor cursor = psCookie.getCursor();
            
            if ( cursor != null )
            {
                cursor.close();
            }
        }
        else
        {
            pagedResultsControl = new PagedResultsControl();
            pagedResultsControl.setSize( 0 );
            pagedResultsControl.setCritical( true );
        }
        
        // and return
        // DO NOT WRITE THE RESPONSE - JUST RETURN IT
        InternalLdapResult ldapResult = req.getResultResponse().getLdapResult();
        ldapResult.setResultCode( ResultCodeEnum.SUCCESS );
        req.getResultResponse().add( pagedResultsControl );
        return ( InternalSearchResponseDone ) req.getResultResponse();
    }
    
    
    /**
     * Remove a cookie instance from the session, if it exists.
     */
    private PagedSearchContext removeContext( LdapSession session, PagedSearchContext cookieInstance )
    {
        if ( cookieInstance == null )
        {
            return null;
        }
        
        long cookieValue = cookieInstance.getCookieValue();
        
        return session.removePagedSearchContext( cookieValue );
    }
    
    
    /**
     * Handle a Paged Search request.
     */
    private InternalSearchResponseDone doPagedSearch( LdapSession session, InternalSearchRequest req, PagedResultsControl control )
        throws Exception
    {
        PagedResultsControl pagedSearchControl = ( PagedResultsControl )control;
        PagedResultsControl pagedResultsControl = null;

        // Get the size limits
        // Don't bother setting size limits for administrators that don't ask for it
        long serverLimit = getServerSizeLimit( session, req );
        
        long requestLimit = req.getSizeLimit() == 0L ?
            Long.MAX_VALUE : req.getSizeLimit();
        long sizeLimit = min( serverLimit, requestLimit );

        int pagedLimit = pagedSearchControl.getSize();
        EntryFilteringCursor cursor = null;
        PagedSearchContext pagedContext = null;

        // We have the following cases :
        // 1) The SIZE is 0 and the cookie is the same than the previous one : this
        // is a abandon request for this paged search.
        // 2) The cookie is empty : this is a new request. If the requested
        // size is above the serverLimit and the request limit, this is a normal
        // search
        // 3) The cookie is not empty and the request is the same, we return
        // the next SIZE elements
        // 4) The cookie is not empty, but the request is not the same : this is 
        // a new request (we have to discard the cookie and do a new search from
        // the beginning)
        // 5) The SIZE is above the size-limit : the request is treated as if it
        // was a simple search
        
        // Case 1
        if ( pagedLimit == 0L )
        {
            // An abandoned paged search
            return abandonPagedSearch( session, req );
        }
        
        // Now, depending on the cookie, we will deal with case 2, 3, 4 and 5
        byte [] cookie= pagedSearchControl.getCookie();
        InternalLdapResult ldapResult = req.getResultResponse().getLdapResult();
        
        if ( StringTools.isEmpty( cookie ) )
        {
            // This is a new search. We have a special case when the paged size
            // is above the server size limit : in this case, we default to a 
            // standard search
            if ( pagedLimit > sizeLimit )
            {
                // Normal search : create the cursor, and set pagedControl to false
                try
                {
                    // No cursor : do a search.
                    cursor = session.getCoreSession().search( req );

                    // Position the cursor at the beginning
                    cursor.beforeFirst();
                    
                    // And read the entries
                    readResults( session, req, ldapResult, cursor, sizeLimit );
                }
                finally
                {
                    try
                    {
                        cursor.close();
                    }
                    catch ( Exception e )
                    {
                        LOG.error( I18n.err( I18n.ERR_168 ), e );
                    }
                }
                
                // If we had a cookie in the session, remove it
                removeContext( session, pagedContext );
                return ( InternalSearchResponseDone ) req.getResultResponse();
            }
            else
            {
                // Case 2 : create the context
                pagedContext = new PagedSearchContext( req );

                session.addPagedSearchContext( pagedContext );
                cookie = pagedContext.getCookie();
                pagedResultsControl = new PagedResultsControl();
                pagedResultsControl.setCookie( cookie );
                pagedResultsControl.setSize( 0 );
                pagedResultsControl.setCritical( true );


                // No cursor : do a search.
                cursor = session.getCoreSession().search( req );

                // Position the cursor at the beginning
                cursor.beforeFirst();
                
                // And stores the cursor into the session
                pagedContext.setCursor( cursor );
            }
        }
        else
        {
            // We have a cookie
            // Either case 3, 4 or 5
            int cookieValue = pagedSearchControl.getCookieValue();
            pagedContext = session.getPagedSearchContext( cookieValue );
            
            if ( pagedContext == null )
            {
                // We didn't found the cookie into the session : it must be invalid
                // send an error.
                ldapResult.setErrorMessage( "Invalid cookie for this PagedSearch request." );
                ldapResult.setResultCode( ResultCodeEnum.UNWILLING_TO_PERFORM );
                
                return ( InternalSearchResponseDone ) req.getResultResponse();
            }
            
            if ( pagedContext.hasSameRequest( req, session ) )
            {
                // Case 3 : continue the search
                cursor = pagedContext.getCursor();
                
                // get the cookie
                cookie = pagedContext.getCookie();
                pagedResultsControl = new PagedResultsControl();
                pagedResultsControl.setCookie( cookie );
                pagedResultsControl.setSize( 0 );
                pagedResultsControl.setCritical( true );

            }
            else
            {
                // case 2 : create a new cursor
                // We have to close the cursor
                cursor = pagedContext.getCursor();
                
                if ( cursor != null )
                {
                    cursor.close();
                }
                
                // Now create a new context and stores it into the session
                pagedContext = new PagedSearchContext( req );

                session.addPagedSearchContext( pagedContext );
                
                cookie = pagedContext.getCookie();
                pagedResultsControl = new PagedResultsControl();
                pagedResultsControl.setCookie( cookie );
                pagedResultsControl.setSize( 0 );
                pagedResultsControl.setCritical( true );

            }
        }
        
        // Now, do the real search
        /*
         * Iterate through all search results building and sending back responses
         * for each search result returned.
         */
        try
        {
            readPagedResults( session, req, ldapResult, cursor, sizeLimit, pagedLimit, true, pagedContext, pagedResultsControl );
        }
        catch ( Exception e )
        {
            if ( cursor != null )
            {
                try
                {
                    cursor.close();
                }
                catch ( Exception ne )
                {
                    LOG.error( I18n.err( I18n.ERR_168 ), ne );
                }
            }
        }
        
        return ( InternalSearchResponseDone ) req.getResultResponse();
    }

    
    /**
     * Conducts a simple search across the result set returning each entry 
     * back except for the search response done.  This is calculated but not
     * returned so the persistent search mechanism can leverage this method
     * along with standard search.<br>
     * <br>
     * @param session the LDAP session object for this request
     * @param req the search request 
     * @return the result done 
     * @throws Exception if there are failures while processing the request
     */
    private InternalSearchResponseDone doSimpleSearch( LdapSession session, InternalSearchRequest req ) 
        throws Exception
    {
        InternalLdapResult ldapResult = req.getResultResponse().getLdapResult();
        
        // Check if we are using the Paged Search Control
        Object control = req.getControls().get( PagedResultsControl.CONTROL_OID );
        
        if ( control != null )
        {
            // Let's deal with the pagedControl
            return doPagedSearch( session, req, (PagedResultsControl)control );
        }
        
        // A normal search
        // Check that we have a cursor or not. 
        // No cursor : do a search.
        EntryFilteringCursor cursor = session.getCoreSession().search( req );

        // Position the cursor at the beginning
        cursor.beforeFirst();
        
        /*
         * Iterate through all search results building and sending back responses
         * for each search result returned.
         */
        try
        {
            // Get the size limits
            // Don't bother setting size limits for administrators that don't ask for it
            long serverLimit = getServerSizeLimit( session, req );
            
            long requestLimit = req.getSizeLimit() == 0L ?
                Long.MAX_VALUE : req.getSizeLimit();

            req.addAbandonListener( new SearchAbandonListener( ldapServer, cursor ) );
            setTimeLimitsOnCursor( req, session, cursor );
            LOG.debug( "using <{},{}> for size limit", requestLimit, serverLimit );
            long sizeLimit = min( requestLimit, serverLimit );
            
            readResults( session, req, ldapResult, cursor, sizeLimit );
        }
        finally
        {
            if ( cursor != null )
            {
                try
                {
                    cursor.close();
                }
                catch ( Exception e )
                {
                    LOG.error( I18n.err( I18n.ERR_168 ), e );
                }
            }
        }
        
        return ( InternalSearchResponseDone ) req.getResultResponse();
    }
    

    /**
     * Generates a response for an entry retrieved from the server core based 
     * on the nature of the request with respect to referral handling.  This 
     * method will either generate a SearchResponseEntry or a 
     * SearchResponseReference depending on if the entry is a referral or if 
     * the ManageDSAITControl has been enabled.
     *
     * @param req the search request
     * @param entry the entry to be handled
     * @return the response for the entry
     * @throws Exception if there are problems in generating the response
     */
    private InternalResponse generateResponse( LdapSession session, InternalSearchRequest req, ClonedServerEntry entry ) throws Exception
    {
        EntryAttribute ref = entry.getOriginalEntry().get( SchemaConstants.REF_AT );
        boolean hasManageDsaItControl = req.getControls().containsKey( ManageDsaITControl.CONTROL_OID );

        if ( ( ref != null ) && ! hasManageDsaItControl )
        {
            // The entry is a referral.
            InternalSearchResponseReference respRef;
            respRef = new SearchResponseReferenceImpl( req.getMessageId() );
            respRef.setReferral( new ReferralImpl() );
            
            for ( Value<?> val : ref )
            {
                String url = val.getString();
                
                if ( ! url.startsWith( "ldap" ) )
                {
                    respRef.getReferral().addLdapUrl( url );
                }
                
                LdapURL ldapUrl = new LdapURL();
                ldapUrl.setForceScopeRendering( true );
                try
                {
                    ldapUrl.parse( url.toCharArray() );
                }
                catch ( LdapURLEncodingException e )
                {
                    LOG.error( I18n.err( I18n.ERR_165, url, entry ) );
                }

                switch( req.getScope() )
                {
                    case SUBTREE:
                        ldapUrl.setScope( SearchScope.SUBTREE.getScope() );
                        break;
                        
                    case ONELEVEL: // one level here is object level on remote server
                        ldapUrl.setScope( SearchScope.OBJECT.getScope() );
                        break;
                        
                    default:
                        throw new IllegalStateException( I18n.err( I18n.ERR_686 ) );
                }
                
                respRef.getReferral().addLdapUrl( ldapUrl.toString() );
            }
            
            return respRef;
        }
        else 
        {
            // The entry is not a referral, or the ManageDsaIt control is set
            InternalSearchResponseEntry respEntry;
            respEntry = new SearchResponseEntryImpl( req.getMessageId() );
            respEntry.setEntry( entry );
            respEntry.setObjectName( entry.getDn() );
            
            // Filter the userPassword if the server mandate to do so
            if ( session.getCoreSession().getDirectoryService().isPasswordHidden() )
            {
                // Remove the userPassord attribute from the entry.
                respEntry.getEntry().removeAttributes( SchemaConstants.USER_PASSWORD_AT );
            }
            
            return respEntry;
        }
    }
    
    
    /**
     * Alters the filter expression based on the presence of the 
     * ManageDsaIT control.  If the control is not present, the search
     * filter will be altered to become a disjunction with two terms.
     * The first term is the original filter.  The second term is a
     * (objectClass=referral) assertion.  When OR'd together these will
     * make sure we get all referrals so we can process continuations 
     * properly without having the filter remove them from the result 
     * set.
     * 
     * NOTE: original filter is first since most entries are not referrals 
     * so it has a higher probability on average of accepting and shorting 
     * evaluation before having to waste cycles trying to evaluate if the 
     * entry is a referral.
     *
     * @param session the session to use to construct the filter (schema access)
     * @param req the request to get the original filter from
     * @throws Exception if there are schema access problems
     */
    public void modifyFilter( LdapSession session, InternalSearchRequest req ) throws Exception
    {
        if ( req.hasControl( ManageDsaITControl.CONTROL_OID ) )
        {
            return;
        }
        
        /*
         * Do not add the OR'd (objectClass=referral) expression if the user 
         * searches for the subSchemaSubEntry as the SchemaIntercepter can't 
         * handle an OR'd filter.
         */
        if ( isSubSchemaSubEntrySearch( session, req ) )
        {
            return;
        }
        
        /*
         * Most of the time the search filter is just (objectClass=*) and if 
         * this is the case then there's no reason at all to OR this with an
         * (objectClass=referral).  If we detect this case then we leave it 
         * as is to represent the OR condition:
         * 
         *  (| (objectClass=referral)(objectClass=*)) == (objectClass=*)
         */
        if ( req.getFilter() instanceof PresenceNode )
        {
            PresenceNode presenceNode = ( PresenceNode ) req.getFilter();
            
            AttributeType at = session.getCoreSession().getDirectoryService()
                .getSchemaManager().lookupAttributeTypeRegistry( presenceNode.getAttribute() );
            if ( at.getOid().equals( SchemaConstants.OBJECT_CLASS_AT_OID ) )
            {
                return;
            }
        }

        // using varags to add two expressions to an OR node 
        req.setFilter( new OrNode( req.getFilter(), newIsReferralEqualityNode( session ) ) );
    }
    
    
    /**
     * Main message handing method for search requests.  This will be called 
     * even if the ManageDsaIT control is present because the super class does
     * not know that the search operation has more to do after finding the 
     * base.  The call to this means that finding the base can ignore 
     * referrals.
     * 
     * @param session the associated session
     * @param req the received SearchRequest
     */
    public void handleIgnoringReferrals( LdapSession session, InternalSearchRequest req )
    {
        if ( IS_DEBUG )
        {
            LOG.debug( "Message received:  {}", req.toString() );
        }

        // A flag set if we have a persistent search
        boolean isPersistentSearch = false;
        
        // A flag set when we've got an exception while processing a
        // persistent search
        boolean persistentSearchException = false;
        
        // add the search request to the registry of outstanding requests for this session
        session.registerOutstandingRequest( req );

        try
        {
            // ===============================================================
            // Handle search in rootDSE differently.
            // ===============================================================
            if ( isRootDSESearch( req ) )
            {
                handleRootDseSearch( session, req );
                
                return;
            }

            // modify the filter to affect continuation support
            modifyFilter( session, req );
            
            // ===============================================================
            // Handle psearch differently
            // ===============================================================

            PersistentSearchControl psearchControl = ( PersistentSearchControl ) 
                req.getControls().get( PersistentSearchControl.CONTROL_OID );
            
            if ( psearchControl != null )
            {
                // Set the flag to avoid the request being removed
                // from the session
                isPersistentSearch = true;

                handlePersistentSearch( session, req, psearchControl );
                
                return;
            }

            // ===============================================================
            // Handle regular search requests from here down
            // ===============================================================

            InternalSearchResponseDone done = doSimpleSearch( session, req );
            session.getIoSession().write( done );
        }
        catch ( Exception e )
        {
            /*
             * From RFC 2251 Section 4.11:
             *
             * In the event that a server receives an Abandon Request on a Search
             * operation in the midst of transmitting responses to the Search, that
             * server MUST cease transmitting entry responses to the abandoned
             * request immediately, and MUST NOT send the SearchResultDone. Of
             * course, the server MUST ensure that only properly encoded LDAPMessage
             * PDUs are transmitted.
             *
             * SO DON'T SEND BACK ANYTHING!!!!!
             */
            if ( e instanceof OperationAbandonedException )
            {
                return;
            }

            // If it was a persistent search and if we had an exception,
            // we set the flag to remove the request from the session
            if ( isPersistentSearch )
            {
                persistentSearchException = true;
            }
            
            handleException( session, req, e );
        }
        finally 
        {
            
            // remove the request from the session, except if
            // we didn't got an exception for a Persistent search 
            if ( !isPersistentSearch || persistentSearchException )
            {
                session.unregisterOutstandingRequest( req );
            }
        }
    }


    /**
     * Handles processing with referrals without ManageDsaIT control.
     */
    public void handleWithReferrals( LdapSession session, DN reqTargetDn, InternalSearchRequest req ) throws LdapException
    {
        InternalLdapResult result = req.getResultResponse().getLdapResult();
        ClonedServerEntry entry = null;
        boolean isReferral = false;
        boolean isparentReferral = false;
        ReferralManager referralManager = session.getCoreSession().getDirectoryService().getReferralManager();
        
        reqTargetDn.normalize( session.getCoreSession().getDirectoryService().
            getSchemaManager().getNormalizerMapping() );
        
        // Check if the entry itself is a referral
        referralManager.lockRead();
        
        isReferral = referralManager.isReferral( reqTargetDn );
        
        if ( !isReferral )
        {
            // Check if the entry has a parent which is a referral
            isparentReferral = referralManager.hasParentReferral( reqTargetDn );
        }
        
        referralManager.unlock();
        
        if ( !isReferral && !isparentReferral )
        {
            // This is not a referral and it does not have a parent which 
            // is a referral : standard case, just deal with the request
            LOG.debug( "Entry {} is NOT a referral.", reqTargetDn );
            handleIgnoringReferrals( session, req );
            return;
        }
        else
        {
            // -------------------------------------------------------------------
            // Lookup Entry
            // -------------------------------------------------------------------
            
            // try to lookup the entry but ignore exceptions when it does not   
            // exist since entry may not exist but may have an ancestor that is a 
            // referral - would rather attempt a lookup that fails then do check 
            // for existence than have to do another lookup to get entry info
            try
            {
                entry = session.getCoreSession().lookup( reqTargetDn );
                LOG.debug( "Entry for {} was found: ", reqTargetDn, entry );
            }
            catch ( LdapException e )
            {
                /* ignore */
                LOG.debug( "Entry for {} not found.", reqTargetDn );
            }
            catch ( Exception e )
            {
                /* serious and needs handling */
                handleException( session, req, e );
                return;
            }
            
            // -------------------------------------------------------------------
            // Handle Existing Entry
            // -------------------------------------------------------------------
            
            if ( entry != null )
            {
                try
                {
                    LOG.debug( "Entry is a referral: {}", entry );
                    
                    handleReferralEntryForSearch( session, ( InternalSearchRequest ) req, entry );

                    return;
                }
                catch ( Exception e )
                {
                    handleException( session, req, e );
                }
            }
    
            // -------------------------------------------------------------------
            // Handle Non-existing Entry
            // -------------------------------------------------------------------
            
            // if the entry is null we still have to check for a referral ancestor
            // also the referrals need to be adjusted based on the ancestor's ref
            // values to yield the correct path to the entry in the target DSAs
            
            else
            {
                // The entry is null : it has a parent referral.
                ClonedServerEntry referralAncestor = null;
    
                try
                {
                    referralAncestor = getFarthestReferralAncestor( session, reqTargetDn );
                }
                catch ( Exception e )
                {
                    handleException( session, req, e );
                    return;
                }
    
                if ( referralAncestor == null )
                {
                    result.setErrorMessage( "Entry not found." );
                    result.setResultCode( ResultCodeEnum.NO_SUCH_OBJECT );
                    session.getIoSession().write( req.getResultResponse() );
                    return;
                }
                  
                // if we get here then we have a valid referral ancestor
                try
                {
                    InternalReferral referral = getReferralOnAncestorForSearch( session, ( InternalSearchRequest ) req, referralAncestor );
                    
                    result.setResultCode( ResultCodeEnum.REFERRAL );
                    result.setReferral( referral );
                    session.getIoSession().write( req.getResultResponse() );
                }
                catch ( Exception e )
                {
                    handleException( session, req, e );
                }
            }
        }
    }
    
    
    /**
     * Handles processing a referral response on a target entry which is a 
     * referral.  It will for any request that returns an LdapResult in it's 
     * response.
     *
     * @param session the session to use for processing
     * @param reqTargetDn the dn of the target entry of the request
     * @param req the request
     * @param entry the entry associated with the request
     */
    private void handleReferralEntryForSearch( LdapSession session, InternalSearchRequest req, ClonedServerEntry entry )
        throws Exception
    {
        InternalLdapResult result = req.getResultResponse().getLdapResult();
        ReferralImpl referral = new ReferralImpl();
        result.setReferral( referral );
        result.setResultCode( ResultCodeEnum.REFERRAL );
        result.setErrorMessage( "Encountered referral attempting to handle request." );
        result.setMatchedDn( req.getBase() );

        EntryAttribute refAttr = entry.getOriginalEntry().get( SchemaConstants.REF_AT );
        
        for ( Value<?> refval : refAttr )
        {
            String refstr = refval.getString();
            
            // need to add non-ldap URLs as-is
            if ( ! refstr.startsWith( "ldap" ) )
            {
                referral.addLdapUrl( refstr );
                continue;
            }
            
            // parse the ref value and normalize the DN  
            LdapURL ldapUrl = new LdapURL();
            try
            {
                ldapUrl.parse( refstr.toCharArray() );
            }
            catch ( LdapURLEncodingException e )
            {
                LOG.error( I18n.err( I18n.ERR_165, refstr, entry ) );
                continue;
            }
            
            ldapUrl.setForceScopeRendering( true );
            ldapUrl.setAttributes( req.getAttributes() );
            ldapUrl.setScope( req.getScope().getScope() );
            referral.addLdapUrl( ldapUrl.toString() );
        }

        session.getIoSession().write( req.getResultResponse() );
    }
    
    
    /**
     * Determines if a search request is on the RootDSE of the server.
     * 
     * It is a RootDSE search if :
     * - the base DN is empty
     * - and the scope is BASE OBJECT
     * - and the filter is (ObjectClass = *)
     * 
     * (RFC 4511, 5.1, par. 1 & 2)
     *
     * @param req the request issued
     * @return true if the search is on the RootDSE false otherwise
     */
    private static boolean isRootDSESearch( InternalSearchRequest req )
    {
        boolean isBaseIsRoot = req.getBase().isEmpty();
        boolean isBaseScope = req.getScope() == SearchScope.OBJECT;
        boolean isRootDSEFilter = false;
        
        if ( req.getFilter() instanceof PresenceNode )
        {
            String attribute = ( ( PresenceNode ) req.getFilter() ).getAttribute();
            isRootDSEFilter = attribute.equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT ) ||
                                attribute.equals( SchemaConstants.OBJECT_CLASS_AT_OID );
        }
        
        return isBaseIsRoot && isBaseScope && isRootDSEFilter;
    }
    
    
    /**
     * <p>
     * Determines if a search request is a subSchemaSubEntry search.
     * </p>
     * <p>
     * It is a schema search if:
     * - the base DN is the DN of the subSchemaSubEntry of the root DSE
     * - and the scope is BASE OBJECT
     * - and the filter is (objectClass=subschema)
     * (RFC 4512, 4.4,)
     * </p>
     * <p>
     * However in this method we only check the first condition to avoid
     * performance issues.
     * </p>
     * 
     * @param session the LDAP session
     * @param req the request issued
     * 
     * @return true if the search is on the subSchemaSubEntry, false otherwise
     * 
     * @throws Exception the exception
     */
    private static boolean isSubSchemaSubEntrySearch( LdapSession session, InternalSearchRequest req ) throws Exception
    {
        DN base = req.getBase();
        String baseNormForm = ( base.isNormalized() ? base.getNormName() : base.getNormName() );

        DirectoryService ds = session.getCoreSession().getDirectoryService();
        PartitionNexus nexus = ds.getPartitionNexus();
        Value<?> subschemaSubentry = nexus.getRootDSE( null ).get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).get();
        DN subschemaSubentryDn = new DN( subschemaSubentry.getString() );
        subschemaSubentryDn.normalize( ds.getSchemaManager().getNormalizerMapping() );
        String subschemaSubentryDnNorm = subschemaSubentryDn.getNormName();
        
        return subschemaSubentryDnNorm.equals( baseNormForm );
    }
}