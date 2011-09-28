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
package org.apache.directory.server.core.schema;

import java.io.File;
import java.util.Enumeration;

import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.util.StringTools;
import org.junit.Before;

/**
 * A common class for all the MetaXXXHandler test classes
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public abstract class AbstractMetaSchemaObjectHandler extends AbstractLdapTestUnit
{
    protected static String workingDir;

    @Before
    public final void init()
    {
        workingDir = service.getWorkingDirectory().getAbsolutePath();
    }
    
    
    /**
     * Get the path on disk where a specific SchemaObject is stored
     *
     * @param dn the SchemaObject DN
     */
    protected String getSchemaPath( DN dn )
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append( workingDir ).append( '/' ).append( service.getSchemaService().getSchemaPartition().getId() ).append( '/' ).append( "ou=schema" );
        
        Enumeration<RDN> rdns = dn.getAllRdn();
        
        while ( rdns.hasMoreElements() )
        {
            sb.append( '/' );
            sb.append( StringTools.toLowerCase( rdns.nextElement().getName() ) );
        }
        
        sb.append( ".ldif" );
        
        return sb.toString();
    }

    
    /**
     * Check that a specific SchemaObject is stored on the disk at the
     * correct position in the Ldif partition
     *
     * @param dn The SchemaObject DN
     */
    protected boolean isOnDisk( DN dn )
    {
        // donot change the value of getSchemaPath to lowercase
        // on Linux this gives a wrong path
        String schemaObjectFileName = getSchemaPath( dn );

        File file = new File( schemaObjectFileName );
     
        return file.exists();
    }
    
    
    /**
     * Gets relative DN to ou=schema.
     *
     * @param schemaName the name of the schema
     * @return the dn of the a schema entity container
     * @throws Exception on failure
     */
    protected DN getSchemaContainer( String schemaName ) throws Exception
    {
        return new DN( "cn=" + schemaName );
    }
    
    
    /**
     * Gets relative DN to ou=schema.
     *
     * @param schemaName the name of the schema
     * @return the dn of the a schema's attributeType entity container
     * @throws Exception on failure
     */
    protected DN getAttributeTypeContainer( String schemaName ) throws Exception
    {
        return new DN( "ou=attributeTypes,cn=" + schemaName );
    }


    /**
     * Get relative DN to ou=schema for Comparators
     *
     * @param schemaName the name of the schema
     * @return the dn to the ou under which comparators are found for a schema
     * @throws Exception if there are dn construction issues
     */
    protected DN getComparatorContainer( String schemaName ) throws Exception
    {
        return new DN( "ou=comparators,cn=" + schemaName );
    }

    
    /**
     * Get relative DN to ou=schema for MatchingRules
     *
     * @param schemaName the name of the schema
     * @return the dn to the ou under which MatchingRules are found for a schema
     * @throws Exception if there are dn construction issues
     */
    protected DN getMatchingRuleContainer( String schemaName ) throws Exception
    {
        return new DN( "ou=matchingRules,cn=" + schemaName );
    }
    
   
    /**
     * Gets relative DN to ou=schema.
     *
     * @param schemaName the name of the schema
     * @return the dn of the container which contains objectClasses
     * @throws Exception on error
     */
    protected DN getObjectClassContainer( String schemaName ) throws Exception
    {
        return new DN( "ou=objectClasses,cn=" + schemaName );
    }



    /**
     * Gets relative DN to ou=schema.
     *
     * @param schemaName the name of the schema
     * @return  the name of the container with normalizer entries in it
     * @throws Exception on error
     */
    protected DN getNormalizerContainer( String schemaName ) throws Exception
    {
        return new DN( "ou=normalizers,cn=" + schemaName );
    }


    /**
     * Get relative DN to ou=schema for Syntaxes
     *
     * @param schemaName the name of the schema
     * @return the dn of the container holding syntaxes for the schema
     * @throws Exception on dn parse errors
     */
    protected DN getSyntaxContainer( String schemaName ) throws Exception
    {
        return new DN( "ou=syntaxes,cn=" + schemaName );
    }
    
    
    /**
     * Get relative DN to ou=schema for SyntaxCheckers
     *
     * @param schemaName the name of the schema
     * @return the dn of the container holding syntax checkers for the schema
     * @throws Exception on dn parse errors
     */
    protected DN getSyntaxCheckerContainer( String schemaName ) throws Exception
    {
        return new DN( "ou=syntaxCheckers,cn=" + schemaName );
    }
}
