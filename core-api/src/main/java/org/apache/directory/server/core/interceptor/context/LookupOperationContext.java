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
 

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.util.StringTools;


/**
 * A context for tracking lookup operations. Lookup operations will return a
 * cloned server entry.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class LookupOperationContext extends AbstractOperationContext
{
    private static final String[] EMPTY = new String[] {};
    
    /** The list of attributes id to return */
    private List<String> attrsId = new ArrayList<String>();
    
    private Boolean allOperational;
    
    private Boolean allUser;
    
    
    /**
     * 
     * Creates a new instance of LookupOperationContext.
     *
     */
    public LookupOperationContext( CoreSession session )
    {
        super( session );
    }
    

    /**
     * 
     * Creates a new instance of LookupOperationContext.
     *
     */
    public LookupOperationContext( CoreSession session, DN dn )
    {
        super( session, dn );
    }
    

    /**
     * 
     * Creates a new instance of LookupOperationContext.
     *
     */
    public LookupOperationContext( CoreSession session, String attrsId[] )
    {
        super( session );
        setAttrsId( attrsId );
    }

    
    /**
     * 
     * Creates a new instance of LookupOperationContext.
     *
     */
    public LookupOperationContext( CoreSession session, DN dn, String attrsId[] )
    {
        super( session, dn );
        setAttrsId( attrsId );
    }

    
    /**
     * @return Get the attribute ids as a String array
     */
    public String[] getAttrsIdArray()
    {
        if ( attrsId == null || attrsId.size() == 0 )
        {
            return EMPTY;
        }
        else
        {
            String[] attrs = new String[ attrsId.size()];
            return attrsId.toArray( attrs );
        }
    }

    
    /**
     * Set the attribute Ids
     *
     * @param attrsId The String array containing all the attribute IDs
     */
    public void setAttrsId( String[] attrsId )
    {
        if ( attrsId != null && attrsId.length > 0 )
        {
            this.attrsId = new ArrayList<String>( Arrays.asList( attrsId ) );
            
            // filter out the '+' and '*' and set boolean parameters 
            for ( String id : this.attrsId )
            {
                if ( id.equals( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES ) )
                {
                    allOperational = true;
                }
                else if ( id.equals( SchemaConstants.ALL_USER_ATTRIBUTES ) )
                {
                    allUser = true;
                }
            }

            if ( allOperational != null && allOperational )
            {
                this.attrsId.remove( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES );
            }
            
            if ( allUser != null && allUser )
            {
                this.attrsId.remove( SchemaConstants.ALL_USER_ATTRIBUTES );
            }
        }
    }


    /**
     * Add an attribute ID to the current list, creating the list if necessary
     *
     * @param attrId the Id to add
     */
    public void addAttrsId( String attrId )
    {
        if ( attrId.equals( SchemaConstants.ALL_USER_ATTRIBUTES ) )
        {
            allUser = true;
            return;
        }
        
        if ( attrId.equals( SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES ) )
        {
            allOperational = true;
            return;
        }
        
        if ( attrsId == null )
        {
            attrsId = new ArrayList<String>(); 
        }
        
        attrsId.add( attrId );
    }

    
    /**
     * @return The attribute IDs list
     */
    public List<String> getAttrsId()
    {
        return attrsId;
    }

    
    public Boolean getAllUser()
    {
        return allUser;
    }
    

    public Boolean getAllOperational()
    {
        return allOperational;
    }
    

    /**
     * @return the operation name
     */
    public String getName()
    {
        return "Lookup";
    }

    
    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return "LookupContext for DN '" + getDn().getName() + "'" + ( ( attrsId != null ) ? ", attributes : <" + StringTools.listToString( attrsId ) + ">" : "" );
    }
}
