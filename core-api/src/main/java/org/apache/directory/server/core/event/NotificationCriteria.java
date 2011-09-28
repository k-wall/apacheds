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
package org.apache.directory.server.core.event;


import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.internal.InternalSearchRequest;
import org.apache.directory.shared.ldap.name.DN;


/**
 * Contains the set of notification criteria required for triggering the 
 * delivery of change notifications notifications to {@link DirectoryListener}s.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class NotificationCriteria
{
    public static final SearchScope DEFAULT_SCOPE = SearchScope.ONELEVEL;
    public static final AliasDerefMode DEFAULT_ALIAS_DEREF_MODE = AliasDerefMode.DEREF_ALWAYS;
    public static final DN DEFAULT_BASE = new DN();
    public static final ExprNode DEFAULT_FILTER = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT_OID );
    
    private SearchScope scope = DEFAULT_SCOPE;
    private AliasDerefMode aliasDerefMode = DEFAULT_ALIAS_DEREF_MODE;
    private DN base = DEFAULT_BASE;
    private ExprNode filter = DEFAULT_FILTER;
    private int eventMask = EventType.ALL_EVENT_TYPES_MASK;
    
    
    public NotificationCriteria()
    {
    }
    
    
    public NotificationCriteria( InternalSearchRequest req )
    {
        this.scope = req.getScope();
        this.aliasDerefMode = req.getDerefAliases();
        this.base = req.getBase();
        this.filter = req.getFilter();
    }
    
    
    /**
     * @param scope the scope to set
     */
    public void setScope( SearchScope scope )
    {
        this.scope = scope;
    }
    
    
    /**
     * @return the scope
     */
    public SearchScope getScope()
    {
        return scope;
    }


    /**
     * @param aliasDerefMode the aliasDerefMode to set
     */
    public void setAliasDerefMode( AliasDerefMode aliasDerefMode )
    {
        this.aliasDerefMode = aliasDerefMode;
    }


    /**
     * @return the aliasDerefMode
     */
    public AliasDerefMode getAliasDerefMode()
    {
        return aliasDerefMode;
    }


    /**
     * @param base the base to set
     */
    public void setBase( DN base )
    {
        this.base = base;
    }


    /**
     * @param base the base to set
     */
    public void setBase( String base ) throws Exception
    {
        this.base = new DN( base );
    }


    /**
     * @return the base
     */
    public DN getBase()
    {
        return base;
    }


    /**
     * @param filter the filter to set
     */
    public void setFilter( ExprNode filter )
    {
        this.filter = filter;
    }


    /**
     * @param filter the filter to set
     */
    public void setFilter( String filter ) throws Exception
    {
        this.filter = FilterParser.parse( filter );
    }


    /**
     * @return the filter
     */
    public ExprNode getFilter()
    {
        return filter;
    }


    /**
     * @param eventMask the eventMask to set
     */
    public void setEventMask( int eventMask )
    {
        this.eventMask = eventMask;
    }


    /**
     * @param eventMask the eventMask to set
     */
    public void setEventMask( EventType ...eventTypes )
    {
        this.eventMask = EventType.getMask( eventTypes );
    }


    /**
     * @return the eventMask
     */
    public int getEventMask()
    {
        return eventMask;
    }
}
