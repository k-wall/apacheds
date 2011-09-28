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


import static org.apache.directory.server.core.integ.IntegrationUtils.getRootContext;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSchemaContext;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.TimeZone;

import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.LdapSyntax;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.SyntaxChecker;
import org.apache.directory.shared.ldap.schema.comparators.BooleanComparator;
import org.apache.directory.shared.ldap.schema.loader.ldif.SchemaEntityFactory;
import org.apache.directory.shared.ldap.schema.normalizers.DeepTrimNormalizer;
import org.apache.directory.shared.ldap.schema.parsers.AttributeTypeDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.LdapComparatorDescription;
import org.apache.directory.shared.ldap.schema.parsers.LdapComparatorDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.LdapSyntaxDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.MatchingRuleDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.NormalizerDescription;
import org.apache.directory.shared.ldap.schema.parsers.NormalizerDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.ObjectClassDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.SyntaxCheckerDescription;
import org.apache.directory.shared.ldap.schema.parsers.SyntaxCheckerDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.syntaxCheckers.OctetStringSyntaxChecker;
import org.apache.directory.shared.ldap.util.Base64;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * An integration test class for performing various operations on the 
 * subschemaSubentry as listed in the rootDSE.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( FrameworkRunner.class )
@CreateDS( name="SubschemaSubentryIT-class" )
public class SubschemaSubentryIT extends AbstractLdapTestUnit 
{
    private static final String GLOBAL_SUBSCHEMA_DN = "cn=schema";
    private static final String SUBSCHEMA_SUBENTRY = "subschemaSubentry";
    
    private static final SyntaxCheckerDescriptionSchemaParser SYNTAX_CHECKER_DESCRIPTION_SCHEMA_PARSER =
        new SyntaxCheckerDescriptionSchemaParser();
    private static final AttributeTypeDescriptionSchemaParser ATTRIBUTE_TYPE_DESCRIPTION_SCHEMA_PARSER =
        new AttributeTypeDescriptionSchemaParser();
    private LdapComparatorDescriptionSchemaParser comparatorDescriptionSchemaParser =
        new LdapComparatorDescriptionSchemaParser();
    private NormalizerDescriptionSchemaParser normalizerDescriptionSchemaParser =
        new NormalizerDescriptionSchemaParser();
    private LdapSyntaxDescriptionSchemaParser ldapSyntaxDescriptionSchemaParser =
        new LdapSyntaxDescriptionSchemaParser();
    private MatchingRuleDescriptionSchemaParser matchingRuleDescriptionSchemaParser =
        new MatchingRuleDescriptionSchemaParser();
    private ObjectClassDescriptionSchemaParser objectClassDescriptionSchemaParser =
        new ObjectClassDescriptionSchemaParser();

    
    /**
     * Make sure the global subschemaSubentry is where it is expected to be.
     *
     * @throws NamingException on error
     */
    @Test
    public void testRootDSEsSubschemaSubentry() throws Exception
    {
        assertEquals( GLOBAL_SUBSCHEMA_DN, getSubschemaSubentryDN() );
        Attributes subschemaSubentryAttrs = getSubschemaSubentryAttributes();
        assertNotNull( subschemaSubentryAttrs );
    }
    
    
    /**
     * Tests the rejection of a delete operation on the SubschemaSubentry (SSSE).
     *
     * @throws NamingException on error
     */
    @Test
    public void testSSSEDeleteRejection() throws Exception
    {
        try
        {
            getRootContext( service ).destroySubcontext( getSubschemaSubentryDN() );
            fail( "You are not allowed to delete the global schema subentry" );
        }
        catch( OperationNotSupportedException e )
        {
        }
    }


    /**
     * Tests the rejection of an add operation for the SubschemaSubentry (SSSE).
     *
     * @throws NamingException on error
     */
    @Test
    public void testSSSEAddRejection() throws Exception
    {
        try
        {
            getRootContext( service ).createSubcontext( getSubschemaSubentryDN(), getSubschemaSubentryAttributes() );
            fail( "You are not allowed to add the global schema subentry which exists by default" );
        }
        catch( NameAlreadyBoundException e )
        {
        }
    }


    /**
     * Tests the rejection of rename (modifyDn) operation for the SubschemaSubentry (SSSE).
     *
     * @throws NamingException on error
     */
    @Test
    public void testSSSERenameRejection() throws Exception
    {
        try
        {
            getRootContext( service ).rename( getSubschemaSubentryDN(), "cn=schema,ou=system" );
            fail( "You are not allowed to rename the global schema subentry which is fixed" );
        }
        catch( OperationNotSupportedException e )
        {
        }
    }


    /**
     * Tests the rejection of move operation for the SubschemaSubentry (SSSE).
     *
     * @throws NamingException on error
     */
    @Test
    public void testSSSEMoveRejection() throws Exception
    {
        try
        {
            getRootContext( service ).rename( getSubschemaSubentryDN(), "cn=blah,ou=schema" );
            fail( "You are not allowed to move the global schema subentry which is fixed" );
        }
        catch( OperationNotSupportedException e )
        {
        }

        try
        {
            getRootContext( service ).rename( getSubschemaSubentryDN(), "cn=schema,ou=schema" );
            fail( "You are not allowed to move the global schema subentry which is fixed" );
        }
        catch( OperationNotSupportedException e )
        {
        }
    }
    
    
    // -----------------------------------------------------------------------
    // SyntaxChecker Tests
    // -----------------------------------------------------------------------

    
    private void checkSyntaxCheckerPresent( SchemaManager schemaManager, String oid, String schemaName, boolean isPresent ) throws Exception
    {
        // -------------------------------------------------------------------
        // check first to see if it is present in the subschemaSubentry
        // -------------------------------------------------------------------
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "syntaxCheckers" );
        SyntaxCheckerDescription syntaxCheckerDescription = null; 

        for ( int i = 0; i < attrTypes.size(); i++ )
        {
            String desc = ( String ) attrTypes.get( i );
            
            if ( desc.indexOf( oid ) != -1 )
            {
                syntaxCheckerDescription = SYNTAX_CHECKER_DESCRIPTION_SCHEMA_PARSER.parseSyntaxCheckerDescription( desc );
                break;
            }
        }
     
        if ( isPresent )
        {
            assertNotNull( syntaxCheckerDescription );
            assertEquals( oid, syntaxCheckerDescription.getOid() );
        }
        else
        {
            assertNull( syntaxCheckerDescription );
        }

        // -------------------------------------------------------------------
        // check next to see if it is present in the schema partition
        // -------------------------------------------------------------------
        
        attrs = null;
        
        if ( isPresent )
        {
            attrs = getSchemaContext( service ).getAttributes( "m-oid=" + oid + ",ou=syntaxCheckers,cn=" + schemaName );
            assertNotNull( attrs );
            SchemaEntityFactory factory = new SchemaEntityFactory();
            
            ServerEntry serverEntry = ServerEntryUtils.toServerEntry( attrs, DN.EMPTY_DN, service.getSchemaManager() );

            SyntaxChecker syntaxChecker = factory.getSyntaxChecker( schemaManager, serverEntry, service.getSchemaManager().getRegistries(), schemaName );
            assertEquals( oid, syntaxChecker.getOid() );
        }
        else
        {
            //noinspection EmptyCatchBlock
            try
            {
                attrs = getSchemaContext( service ).getAttributes( "m-oid=" + oid + ",ou=syntaxCheckers,cn=" + schemaName );
                fail( "should never get here" );
            }
            catch( NamingException e )
            {
            }
            
            assertNull( attrs );
        }
        
        // -------------------------------------------------------------------
        // check to see if it is present in the syntaxCheckerRegistry
        // -------------------------------------------------------------------

        if ( isPresent )
        {
            assertTrue( service.getSchemaManager().getSyntaxCheckerRegistry().contains( oid ) );
        }
        else
        {
            assertFalse( service.getSchemaManager().getSyntaxCheckerRegistry().contains( oid ) );
        }
    }


    /**
     * Tests a number of modify add, remove and replace operation combinations for
     * a syntaxChecker on the schema subentry.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddRemoveReplaceSyntaxCheckers() throws Exception
    {
        // 1st change
        enableSchema( "nis" );
        List<String> descriptions = new ArrayList<String>();
        
        // ( 1.3.6.1.4.1.18060.0.4.0.2.10000 DESC 'bogus desc' FQCN org.foo.Bar BYTECODE 14561234 )
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10000 DESC 'bogus desc' FQCN " 
            + OctetStringSyntaxChecker.class.getName() + " X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10001 DESC 'bogus desc' FQCN " 
            + OctetStringSyntaxChecker.class.getName() + " X-SCHEMA 'nis' )" );

        // -------------------------------------------------------------------
        // add and check
        // -------------------------------------------------------------------
        
        // 2nd change
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "syntaxCheckers" );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10000", "nis", true );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", true );

        // -------------------------------------------------------------------
        // remove and check
        // -------------------------------------------------------------------

        // 3rd change
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "syntaxCheckers" );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10000", "nis", false );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", false );
        
        // -------------------------------------------------------------------
        // test failure to replace
        // -------------------------------------------------------------------
        
        try
        {
            modify( DirContext.REPLACE_ATTRIBUTE, descriptions, "syntaxCheckers" );
            fail( "modify REPLACE operations should not be allowed" );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        // -------------------------------------------------------------------
        // check add with valid bytecode
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10002 DESC 'bogus desc' FQCN org.apache.directory.shared.ldap.schema.syntaxCheckers.DummySyntaxChecker BYTECODE " 
            +  getByteCode( "DummySyntaxChecker.bytecode" ) + " X-SCHEMA 'nis' )" );

        // 4th change
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "syntaxCheckers" );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10002", "nis", true );

        // -------------------------------------------------------------------
        // check remove
        // -------------------------------------------------------------------

        // 5th change
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "syntaxCheckers" );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10002", "nis", false );

        // -------------------------------------------------------------------
        // check add no schema info
        // -------------------------------------------------------------------
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10002 DESC 'bogus desc' FQCN org.apache.directory.shared.ldap.schema.syntaxCheckers.DummySyntaxChecker BYTECODE " 
            +  getByteCode( "DummySyntaxChecker.bytecode" ) + " )" );

        // 6th change
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "syntaxCheckers" );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10002", "other", true );

        // after a total of 6 changes 
        if ( service.getChangeLog().getLatest() != null )
        {
            assertEquals( service.getChangeLog().getLatest().getRevision() + 6,
                    service.getChangeLog().getCurrentRevision() );
        }
    }
    
    
    // -----------------------------------------------------------------------
    // Comparator Tests
    // -----------------------------------------------------------------------
    
    
    private void checkComparatorPresent( String oid, String schemaName, boolean isPresent ) throws Exception
    {
        // -------------------------------------------------------------------
        // check first to see if it is present in the subschemaSubentry
        // -------------------------------------------------------------------
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "comparators" );
        LdapComparatorDescription comparatorDescription = null;
        
        for ( int ii = 0; ii < attrTypes.size(); ii++ )
        {
            String desc = ( String ) attrTypes.get( ii );
            
            if ( desc.indexOf( oid ) != -1 )
            {
                comparatorDescription = comparatorDescriptionSchemaParser.parseComparatorDescription( desc );
                break;
            }
        }
     
        if ( isPresent )
        {
            assertNotNull( comparatorDescription );
            assertEquals( oid, comparatorDescription.getOid() );
        }
        else
        {
            assertNull( comparatorDescription );
        }

        // -------------------------------------------------------------------
        // check next to see if it is present in the schema partition
        // -------------------------------------------------------------------
        
        attrs = null;

        LdapContext schemaRoot = getSchemaContext( service );
        if ( isPresent )
        {
            attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=comparators,cn=" + schemaName );
            assertNotNull( attrs );
        }
        else
        {
            //noinspection EmptyCatchBlock
            try
            {
                attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=comparators,cn=" + schemaName );
                fail( "should never get here" );
            }
            catch( NamingException e )
            {
            }
            assertNull( attrs );
        }
        
        // -------------------------------------------------------------------
        // check to see if it is present in the comparatorRegistry
        // -------------------------------------------------------------------

        if ( isPresent )
        {
            assertTrue( service.getSchemaManager().getComparatorRegistry().contains( oid ) );
        }
        else
        {
            assertFalse( service.getSchemaManager().getComparatorRegistry().contains( oid ) );
        }
    }
    
    
    /**
     * Tests a number of modify add, remove and replace operation combinations for
     * comparators on the schema subentry.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddRemoveReplaceComparators() throws Exception
    {
        enableSchema( "nis" );
        List<String> descriptions = new ArrayList<String>();
        
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10002 DESC 'bogus desc' FQCN " 
            + BooleanComparator.class.getName() + " X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10001 DESC 'bogus desc' FQCN " 
            + BooleanComparator.class.getName() + " X-SCHEMA 'nis' )" );

        // -------------------------------------------------------------------
        // add and check
        // -------------------------------------------------------------------
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "comparators" );
        checkComparatorPresent( "1.3.6.1.4.1.18060.0.4.1.0.10002", "nis", true );
        checkComparatorPresent( "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", true );

        // -------------------------------------------------------------------
        // remove and check
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "comparators" );
        checkComparatorPresent( "1.3.6.1.4.1.18060.0.4.1.0.10002", "nis", false );
        checkComparatorPresent( "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", false );
        
        // -------------------------------------------------------------------
        // test failure to replace
        // -------------------------------------------------------------------
        
        try
        {
            modify( DirContext.REPLACE_ATTRIBUTE, descriptions, "comparators" );
            fail( "modify REPLACE operations should not be allowed" );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        // -------------------------------------------------------------------
        // check add with valid bytecode
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.0.1.100000 DESC 'bogus desc' FQCN org.apache.directory.shared.ldap.schema.comparators.DummyComparator BYTECODE " 
            +  getByteCode( "DummyComparator.bytecode" ) + " X-SCHEMA 'nis' )" );

        modify( DirContext.ADD_ATTRIBUTE, descriptions, "comparators" );
        checkComparatorPresent( "1.3.6.1.4.1.18060.0.4.0.1.100000", "nis", true );

        // -------------------------------------------------------------------
        // check remove with valid bytecode
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "comparators" );
        checkComparatorPresent( "1.3.6.1.4.1.18060.0.4.0.1.100000", "nis", false );

        // -------------------------------------------------------------------
        // check add no schema info
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.0.1.100000 DESC 'bogus desc' FQCN org.apache.directory.shared.ldap.schema.comparators.DummyComparator BYTECODE " 
            +  getByteCode( "DummyComparator.bytecode" ) + " )" );

        modify( DirContext.ADD_ATTRIBUTE, descriptions, "comparators" );
        checkComparatorPresent( "1.3.6.1.4.1.18060.0.4.0.1.100000", "other", true );
    }

    
    // -----------------------------------------------------------------------
    // Normalizer Tests
    // -----------------------------------------------------------------------
    
    
    private void checkNormalizerPresent( String oid, String schemaName, boolean isPresent ) throws Exception
    {
        // -------------------------------------------------------------------
        // check first to see if it is present in the subschemaSubentry
        // -------------------------------------------------------------------
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "normalizers" );
        NormalizerDescription normalizerDescription = null; 
        
        for ( int i = 0; i < attrTypes.size(); i++ )
        {
            String desc = ( String ) attrTypes.get( i );
            
            if ( desc.indexOf( oid ) != -1 )
            {
                normalizerDescription = normalizerDescriptionSchemaParser.parseNormalizerDescription( desc );
                break;
            }
        }
     
        if ( isPresent )
        {
            assertNotNull( normalizerDescription );
            assertEquals( oid, normalizerDescription.getOid() );
        }
        else
        {
            assertNull( normalizerDescription );
        }

        // -------------------------------------------------------------------
        // check next to see if it is present in the schema partition
        // -------------------------------------------------------------------
        
        attrs = null;

        LdapContext schemaRoot = getSchemaContext( service );
        if ( isPresent )
        {
            attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=normalizers,cn=" + schemaName );
            assertNotNull( attrs );
        }
        else
        {
            //noinspection EmptyCatchBlock
            try
            {
                attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=normalizers,cn=" + schemaName );
                fail( "should never get here" );
            }
            catch( NamingException e )
            {
            }
            assertNull( attrs );
        }
        
        // -------------------------------------------------------------------
        // check to see if it is present in the normalizerRegistry
        // -------------------------------------------------------------------
        
        if ( isPresent ) 
        { 
            assertTrue( service.getSchemaManager().getNormalizerRegistry().contains( oid ) );
        }
        else
        {
            assertFalse( service.getSchemaManager().getNormalizerRegistry().contains( oid ) );
        }
    }
    
    
    /**
     * Tests a number of modify add, remove and replace operation combinations for
     * normalizers on the schema subentry.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddRemoveReplaceNormalizers() throws Exception
    {
        enableSchema( "nis" );
        List<String> descriptions = new ArrayList<String>();
        
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10002 DESC 'bogus desc' FQCN " 
            + DeepTrimNormalizer.class.getName() + " X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10001 DESC 'bogus desc' FQCN " 
            + DeepTrimNormalizer.class.getName() + " X-SCHEMA 'nis' )" );

        // -------------------------------------------------------------------
        // add and check
        // -------------------------------------------------------------------
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "normalizers" );
        checkNormalizerPresent( "1.3.6.1.4.1.18060.0.4.1.0.10002", "nis", true );
        checkNormalizerPresent( "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", true );

        // -------------------------------------------------------------------
        // remove and check
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "normalizers" );
        checkNormalizerPresent( "1.3.6.1.4.1.18060.0.4.1.0.10002", "nis", false );
        checkNormalizerPresent( "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", false );
        
        // -------------------------------------------------------------------
        // test failure to replace
        // -------------------------------------------------------------------
        
        try
        {
            modify( DirContext.REPLACE_ATTRIBUTE, descriptions, "normalizers" );
            fail( "modify REPLACE operations should not be allowed" );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        // -------------------------------------------------------------------
        // check add with valid bytecode
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.0.1.100000 DESC 'bogus desc' FQCN org.apache.directory.shared.ldap.schema.normalizers.DummyNormalizer BYTECODE " 
            +  getByteCode( "DummyNormalizer.bytecode" ) + " X-SCHEMA 'nis' )" );

        modify( DirContext.ADD_ATTRIBUTE, descriptions, "normalizers" );
        checkNormalizerPresent( "1.3.6.1.4.1.18060.0.4.0.1.100000", "nis", true );

        // -------------------------------------------------------------------
        // check remove with valid bytecode
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "normalizers" );
        checkNormalizerPresent( "1.3.6.1.4.1.18060.0.4.0.1.100000", "nis", false );

        // -------------------------------------------------------------------
        // check add no schema info
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.0.1.100000 DESC 'bogus desc' FQCN org.apache.directory.shared.ldap.schema.normalizers.DummyNormalizer BYTECODE " 
            +  getByteCode( "DummyNormalizer.bytecode" ) + " )" );

        modify( DirContext.ADD_ATTRIBUTE, descriptions, "normalizers" );
        checkNormalizerPresent( "1.3.6.1.4.1.18060.0.4.0.1.100000", "other", true );
    }

    
    // -----------------------------------------------------------------------
    // Syntax Tests
    // -----------------------------------------------------------------------
    
    
    private void checkSyntaxPresent( String oid, String schemaName, boolean isPresent ) throws Exception
    {
        // -------------------------------------------------------------------
        // check first to see if it is present in the subschemaSubentry
        // -------------------------------------------------------------------
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "ldapSyntaxes" );
        LdapSyntax ldapSyntax = null;
        
        for ( int ii = 0; ii < attrTypes.size(); ii++ )
        {
            String desc = ( String ) attrTypes.get( ii );
            
            if ( desc.indexOf( oid ) != -1 )
            {
                ldapSyntax = ldapSyntaxDescriptionSchemaParser.parseLdapSyntaxDescription( desc );
                break;
            }
        }
     
        if ( isPresent )
        {
            assertNotNull( ldapSyntax );
            assertEquals( oid, ldapSyntax.getOid() );
        }
        else
        {
            assertNull( ldapSyntax );
        }

        // -------------------------------------------------------------------
        // check next to see if it is present in the schema partition
        // -------------------------------------------------------------------
        
        attrs = null;

        LdapContext schemaRoot = getSchemaContext( service );
        if ( isPresent )
        {
            attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=syntaxes,cn=" + schemaName );
            assertNotNull( attrs );
        }
        else
        {
            //noinspection EmptyCatchBlock
            try
            {
                attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=syntaxes,cn=" + schemaName );
                fail( "should never get here" );
            }
            catch( NamingException e )
            {
            }
            assertNull( attrs );
        }
        
        // -------------------------------------------------------------------
        // check to see if it is present in the syntaxRegistry
        // -------------------------------------------------------------------
        
        if ( isPresent ) 
        { 
            assertTrue( service.getSchemaManager().getLdapSyntaxRegistry().contains( oid ) );
        }
        else
        {
            assertFalse( service.getSchemaManager().getLdapSyntaxRegistry().contains( oid ) );
        }
    }
    
    
    /**
     * Tests a number of modify add, remove and replace operation combinations for
     * syntaxes on the schema subentry.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddRemoveReplaceSyntaxes() throws Exception
    {
        enableSchema( "nis" );
        List<String> descriptions = new ArrayList<String>();
        
        // -------------------------------------------------------------------
        // add of syntaxes without their syntax checkers should fail
        // -------------------------------------------------------------------
        
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10000 DESC 'bogus desc' X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10001 DESC 'bogus desc' X-SCHEMA 'nis' )" );
        
        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "ldapSyntaxes" );
            fail( "should not be able to add syntaxes without their syntaxCheckers" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        // none of the syntaxes should be present
        checkSyntaxPresent( "1.3.6.1.4.1.18060.0.4.1.0.10000", "nis", false );
        checkSyntaxPresent( "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", false );

        // -------------------------------------------------------------------
        // first in order to add syntaxes we need their syntax checkers 
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10000 DESC 'bogus desc' FQCN " 
            + OctetStringSyntaxChecker.class.getName() + " X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10001 DESC 'bogus desc' FQCN " 
            + OctetStringSyntaxChecker.class.getName() + " X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10002 DESC 'bogus desc' FQCN " 
            + OctetStringSyntaxChecker.class.getName() + " X-SCHEMA 'nis' )" );

        modify( DirContext.ADD_ATTRIBUTE, descriptions, "syntaxCheckers" );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10000", "nis", true );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", true );
        checkSyntaxCheckerPresent( service.getSchemaManager(), "1.3.6.1.4.1.18060.0.4.1.0.10002", "nis", true );

        // -------------------------------------------------------------------
        // add and check
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10000 DESC 'bogus desc' X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10001 DESC 'bogus desc' X-SCHEMA 'nis' )" );

        modify( DirContext.ADD_ATTRIBUTE, descriptions, "ldapSyntaxes" );
        checkSyntaxPresent( "1.3.6.1.4.1.18060.0.4.1.0.10000", "nis", true );
        checkSyntaxPresent( "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", true );

        // -------------------------------------------------------------------
        // remove and check
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "ldapSyntaxes" );
        checkSyntaxPresent( "1.3.6.1.4.1.18060.0.4.1.0.10000", "nis", false );
        checkSyntaxPresent( "1.3.6.1.4.1.18060.0.4.1.0.10001", "nis", false );
        
        // -------------------------------------------------------------------
        // test failure to replace
        // -------------------------------------------------------------------
        
        try
        {
            modify( DirContext.REPLACE_ATTRIBUTE, descriptions, "ldapSyntaxes" );
            fail( "modify REPLACE operations should not be allowed" );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        // -------------------------------------------------------------------
        // check add no schema info
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.0.10002 DESC 'bogus desc' )" );
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "ldapSyntaxes" );
        checkSyntaxPresent( "1.3.6.1.4.1.18060.0.4.1.0.10002", "other", true );
    }
    
    
    // -----------------------------------------------------------------------
    // MatchingRule Tests
    // -----------------------------------------------------------------------
    
    
    private void checkMatchingRulePresent( String oid, String schemaName, boolean isPresent ) throws Exception
    {
        // -------------------------------------------------------------------
        // check first to see if it is present in the subschemaSubentry
        // -------------------------------------------------------------------
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "matchingRules" );
        MatchingRule matchingRule = null; 
        
        for ( int ii = 0; ii < attrTypes.size(); ii++ )
        {
            String desc = ( String ) attrTypes.get( ii );
            if ( desc.indexOf( oid ) != -1 )
            {
                matchingRule = matchingRuleDescriptionSchemaParser.parseMatchingRuleDescription( desc );
                break;
            }
        }
     
        if ( isPresent )
        {
            assertNotNull( matchingRule );
            assertEquals( oid, matchingRule.getOid() );
        }
        else
        {
            assertNull( matchingRule );
        }

        // -------------------------------------------------------------------
        // check next to see if it is present in the schema partition
        // -------------------------------------------------------------------
        
        attrs = null;

        LdapContext schemaRoot = getSchemaContext( service );
        if ( isPresent )
        {
            attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=matchingRules,cn=" + schemaName );
            assertNotNull( attrs );
        }
        else
        {
            //noinspection EmptyCatchBlock
            try
            {
                attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=matchingRules,cn=" + schemaName );
                fail( "should never get here" );
            }
            catch( NamingException e )
            {
            }
            assertNull( attrs );
        }
        
        // -------------------------------------------------------------------
        // check to see if it is present in the matchingRuleRegistry
        // -------------------------------------------------------------------
        
        if ( isPresent ) 
        { 
            assertTrue( service.getSchemaManager().getMatchingRuleRegistry().contains( oid ) );
        }
        else
        {
            assertFalse( service.getSchemaManager().getMatchingRuleRegistry().contains( oid ) );
        }
    }
    
    
    /**
     * Tests a number of modify add, remove and replace operation combinations for
     * matchingRules on the schema subentry.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddRemoveReplaceMatchingRules() throws Exception
    {
        enableSchema( "nis" );
        List<String> descriptions = new ArrayList<String>();

        // -------------------------------------------------------------------
        // test rejection with non-existant syntax
        // -------------------------------------------------------------------
        
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10000 DESC 'bogus desc' SYNTAX 1.2.3.4 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10001 DESC 'bogus desc' SYNTAX 1.2.3.4 X-SCHEMA 'nis' )" );

        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "matchingRules" );
            fail( "Cannot add matchingRule with bogus non-existant syntax" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", false );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", false );

        // -------------------------------------------------------------------
        // test add with existant syntax but no name and no desc
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10000 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10001 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "matchingRules" );
        
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", true );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add with existant syntax but no name 
        // -------------------------------------------------------------------
        
        // clear the matchingRules out now
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "matchingRules" );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", false );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", false );

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10000 DESC 'bogus desc' " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10001 DESC 'bogus desc' " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "matchingRules" );
        
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", true );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add success with name
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "matchingRules" );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", false );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10000 NAME 'blah0' DESC 'bogus desc' " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10001 NAME ( 'blah1' 'othername1' ) DESC 'bogus desc' " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "matchingRules" );
        
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", true );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add success full (with obsolete)
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "matchingRules" );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", false );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10000 NAME 'blah0' DESC 'bogus desc' " +
                "OBSOLETE SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10001 NAME ( 'blah1' 'othername1' ) DESC 'bogus desc' " +
                "OBSOLETE SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "matchingRules" );
        
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", true );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", true );

        // -------------------------------------------------------------------
        // test failure to replace
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "matchingRules" );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10000", "nis", false );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10001", "nis", false );
        
        try
        {
            modify( DirContext.REPLACE_ATTRIBUTE, descriptions, "matchingRules" );
            fail( "modify REPLACE operations should not be allowed" );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        // -------------------------------------------------------------------
        // check add no schema info
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.1.10002 DESC 'bogus desc' " +
        "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )" );
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "matchingRules" );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.1.10002", "other", true );
    }

    
    // -----------------------------------------------------------------------
    // AttributeType Tests
    // -----------------------------------------------------------------------

    
    private void checkAttributeTypePresent( String oid, String schemaName, boolean isPresent ) throws Exception
    {
        // -------------------------------------------------------------------
        // check first to see if it is present in the subschemaSubentry
        // -------------------------------------------------------------------
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "attributeTypes" );
        AttributeType attributeType = null; 
        
        for ( int ii = 0; ii < attrTypes.size(); ii++ )
        {
            String desc = ( String ) attrTypes.get( ii );
            
            if ( desc.indexOf( oid ) != -1 )
            {
                attributeType = ATTRIBUTE_TYPE_DESCRIPTION_SCHEMA_PARSER.parseAttributeTypeDescription( desc );
                break;
            }
        }
     
        if ( isPresent )
        {
            assertNotNull( attributeType );
            assertEquals( oid, attributeType.getOid() );
        }
        else
        {
            assertNull( attributeType );
        }

        // -------------------------------------------------------------------
        // check next to see if it is present in the schema partition
        // -------------------------------------------------------------------
        //noinspection UnusedAssignment
        attrs = null;

        LdapContext schemaRoot = getSchemaContext( service );
        
        if ( isPresent )
        {
            attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=attributeTypes,cn=" + schemaName );
            assertNotNull( attrs );
        }
        else
        {
            //noinspection EmptyCatchBlock
            try
            {
                attrs = schemaRoot.getAttributes( "m-oid=" + oid + ",ou=attributeTypes,cn=" + schemaName );
                fail( "should never get here" );
            }
            catch( NamingException e )
            {
            }
            assertNull( attrs );
        }
        
        // -------------------------------------------------------------------
        // check to see if it is present in the attributeTypeRegistry
        // -------------------------------------------------------------------
        
        if ( isPresent ) 
        { 
            assertTrue( service.getSchemaManager().getAttributeTypeRegistry().contains( oid ) );
        }
        else
        {
            assertFalse( service.getSchemaManager().getAttributeTypeRegistry().contains( oid ) );
        }
    }
    
    
    /**
     * Tests a number of modify add, remove and replace operation combinations for
     * attributeTypes on the schema subentry.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddRemoveReplaceAttributeTypes() throws Exception
    {
        enableSchema( "nis" );
        List<String> descriptions = new ArrayList<String>();

        // -------------------------------------------------------------------
        // test rejection with non-existant syntax
        // -------------------------------------------------------------------
        
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 DESC 'bogus desc' " +
                "SYNTAX 1.2.3.4 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 DESC 'bogus desc' " +
                "SYNTAX 1.2.3.4 X-SCHEMA 'nis' )" );

        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
            fail( "Cannot add attributeType with bogus non-existant syntax" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", false );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", false );

        // -------------------------------------------------------------------
        // test reject with non-existant super type
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 1.2.3.4 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 1.2.3.4 X-SCHEMA 'nis' )" );
        
        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
            fail( "Cannot add attributeType with bogus non-existant syntax" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", false );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", false );

        // -------------------------------------------------------------------
        // test reject with non-existant equality matchingRule
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 EQUALITY 1.2.3.4 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 EQUALITY 1.2.3.4 X-SCHEMA 'nis' )" );
        
        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
            fail( "Cannot add attributeType with bogus non-existant equality MatchingRule" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", false );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", false );

        // -------------------------------------------------------------------
        // test reject with non-existant ordering matchingRule
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 ORDERING 1.2.3.4 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 ORDERING 1.2.3.4 X-SCHEMA 'nis' )" );
        
        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
            fail( "Cannot add attributeType with bogus non-existant ordering MatchingRule" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", false );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", false );

        // -------------------------------------------------------------------
        // test reject with non-existant substring matchingRule
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUBSTR 1.2.3.4 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUBSTR 1.2.3.4 X-SCHEMA 'nis' )" );
        
        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
            fail( "Cannot add attributeType with bogus non-existant substrings MatchingRule" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", false );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", false );

        // -------------------------------------------------------------------
        // test success with valid superior, valid syntax but no name
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 2.5.4.41 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 2.5.4.41 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", true );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", true );

        // -------------------------------------------------------------------
        // test success with valid superior, valid syntax and names
        // -------------------------------------------------------------------

        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "attributeTypes" );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 NAME 'type0' " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 2.5.4.41 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 NAME ( 'type1' 'altName' ) " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 2.5.4.41 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", true );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", true );

        // -------------------------------------------------------------------
        // test success with everything
        // -------------------------------------------------------------------

        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "attributeTypes" );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 NAME 'type0' " +
                "OBSOLETE SUP 2.5.4.41 " +
                "EQUALITY caseExactIA5Match " +
                "ORDERING octetStringOrderingMatch " +
                "SUBSTR caseExactIA5SubstringsMatch COLLECTIVE " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
                "SINGLE-VALUE USAGE userApplications X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 NAME ( 'type1' 'altName' ) " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 2.5.4.41 " +
                "USAGE userApplications X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", true );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", true );

        // -------------------------------------------------------------------
        // test failure to replace
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "attributeTypes" );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "nis", false );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "nis", false );
        
        try
        {
            modify( DirContext.REPLACE_ATTRIBUTE, descriptions, "attributeTypes" );
            fail( "modify REPLACE operations should not be allowed" );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        // -------------------------------------------------------------------
        // check add no schema info
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10000 NAME 'type0' " +
                "OBSOLETE SUP 2.5.4.41 " +
                "EQUALITY caseExactIA5Match " +
                "ORDERING octetStringOrderingMatch " +
                "SUBSTR caseExactIA5SubstringsMatch COLLECTIVE " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
                "SINGLE-VALUE USAGE userApplications )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.2.10001 NAME ( 'type1' 'altName' ) " +
                "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP 2.5.4.41 " +
                "USAGE userApplications )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "attributeTypes" );
        
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10000", "other", true );
        checkAttributeTypePresent( "1.3.6.1.4.1.18060.0.4.1.2.10001", "other", true );
    }

    
    /**
     * Tests the addition of a new attributeType via a modify ADD on the SSSE to disabled schema.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddAttributeTypeOnDisabledSchema() throws Exception
    {
        disableSchema( "nis" );
        DN dn = new DN( getSubschemaSubentryDN() );
        String substrate = "( 1.3.6.1.4.1.18060.0.4.0.2.10000 NAME ( 'bogus' 'bogusName' ) " +
            "DESC 'bogus description' SUP name SYNTAX '1.3.6.1.4.1.1466.115.121.1.15' SINGLE-VALUE X-SCHEMA 'nis' )";
        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem( DirContext.ADD_ATTRIBUTE, 
            new BasicAttribute( "attributeTypes", substrate ) );
        
        getRootContext( service ).modifyAttributes( DN.toName( dn ), mods );
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "attributeTypes" );
        AttributeType attributeType = null;
        
        for ( int ii = 0; ii < attrTypes.size(); ii++ )
        {
            String desc = ( String ) attrTypes.get( ii );
            
            if ( desc.indexOf( "1.3.6.1.4.1.18060.0.4.0.2.10000" ) != -1 )
            {
                attributeType = ATTRIBUTE_TYPE_DESCRIPTION_SCHEMA_PARSER.parseAttributeTypeDescription( desc );
                break;
            }
        }
        
        assertNull( attributeType );

        attrs = getSchemaContext( service ).getAttributes( "m-oid=1.3.6.1.4.1.18060.0.4.0.2.10000,ou=attributeTypes,cn=nis" );
        assertNotNull( attrs );
        SchemaEntityFactory factory = new SchemaEntityFactory();
        
        ServerEntry serverEntry = ServerEntryUtils.toServerEntry( attrs, DN.EMPTY_DN, service.getSchemaManager() );
        
        AttributeType at = factory.getAttributeType( service.getSchemaManager(), serverEntry, service.getSchemaManager().getRegistries(), "nis" );
        assertEquals( "1.3.6.1.4.1.18060.0.4.0.2.10000", at.getOid() );
        assertEquals( "name", at.getSuperiorOid() );
        assertEquals( "bogus description", at.getDescription() );
        assertEquals( "bogus", at.getName() );
        assertEquals( "bogusName", at.getNames().get( 1 ) );
        assertEquals( true, at.isUserModifiable() );
        assertEquals( false, at.isCollective() );
        assertEquals( false, at.isObsolete() );
        assertEquals( true, at.isSingleValued() );
    }

    
    /**
     * Tests the addition of a new attributeType via a modify ADD on the SSSE to enabled schema.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddAttributeTypeOnEnabledSchema() throws Exception
    {
        enableSchema( "nis" );
        DN dn = new DN( getSubschemaSubentryDN() );
        String substrate = "( 1.3.6.1.4.1.18060.0.4.0.2.10000 NAME ( 'bogus' 'bogusName' ) " +
            "DESC 'bogus description' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP name SINGLE-VALUE X-SCHEMA 'nis' )";
        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem( DirContext.ADD_ATTRIBUTE, 
            new BasicAttribute( "attributeTypes", substrate ) );
        
        getRootContext( service ).modifyAttributes( DN.toName( dn ), mods );
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "attributeTypes" );
        AttributeType attributeType = null; 
        
        for ( int ii = 0; ii < attrTypes.size(); ii++ )
        {
            String desc = ( String ) attrTypes.get( ii );
            
            if ( desc.indexOf( "1.3.6.1.4.1.18060.0.4.0.2.10000" ) != -1 )
            {
                attributeType = ATTRIBUTE_TYPE_DESCRIPTION_SCHEMA_PARSER.parseAttributeTypeDescription( desc );
                break;
            }
        }
        
        assertNotNull( attributeType );
        assertEquals( true, attributeType.isSingleValued() );
        assertEquals( false, attributeType.isCollective() );
        assertEquals( false, attributeType.isObsolete() );
        assertEquals( true, attributeType.isUserModifiable() );
        assertEquals( "bogus description", attributeType.getDescription() );
        assertEquals( "bogus", attributeType.getNames().get( 0 ) );
        assertEquals( "bogusName", attributeType.getNames().get( 1 ) );
        assertEquals( "name", attributeType.getSuperiorOid() );
        
        attrs = getSchemaContext( service ).getAttributes(
                "m-oid=1.3.6.1.4.1.18060.0.4.0.2.10000,ou=attributeTypes,cn=nis" );
        assertNotNull( attrs );
        SchemaEntityFactory factory = new SchemaEntityFactory();
        
        ServerEntry serverEntry = ServerEntryUtils.toServerEntry( attrs, DN.EMPTY_DN, service.getSchemaManager() );

        AttributeType at = factory.getAttributeType( service.getSchemaManager(), serverEntry, service.getSchemaManager().getRegistries(), "nis" );
        assertEquals( "1.3.6.1.4.1.18060.0.4.0.2.10000", at.getOid() );
        assertEquals( "name", at.getSuperiorOid() );
        assertEquals( "bogus description", at.getDescription() );
        assertEquals( "bogus", at.getNames().get( 0 ) );
        assertEquals( "bogusName", at.getNames().get( 1 ) );
        assertEquals( true, at.isUserModifiable() );
        assertEquals( false, at.isCollective() );
        assertEquals( false, at.isObsolete() );
        assertEquals( true, at.isSingleValued() );
    }


    // -----------------------------------------------------------------------
    // ObjectClass Tests
    // -----------------------------------------------------------------------
    
    
    private void checkObjectClassPresent( String oid, String schemaName, boolean isPresent ) throws Exception
    {
        // -------------------------------------------------------------------
        // check first to see if it is present in the subschemaSubentry
        // -------------------------------------------------------------------
        
        Attributes attrs = getSubschemaSubentryAttributes();
        Attribute attrTypes = attrs.get( "objectClasses" );
        ObjectClass objectClass = null; 
        for ( int i = 0; i < attrTypes.size(); i++ )
        {
            String desc = ( String ) attrTypes.get( i );
            
            if ( desc.indexOf( oid ) != -1 )
            {
                objectClass = objectClassDescriptionSchemaParser.parseObjectClassDescription( desc );
                break;
            }
        }
     
        if ( isPresent )
        {
            assertNotNull( objectClass );
            assertEquals( oid, objectClass.getOid() );
        }
        else
        {
            assertNull( objectClass );
        }

        // -------------------------------------------------------------------
        // check next to see if it is present in the schema partition
        // -------------------------------------------------------------------

        //noinspection UnusedAssignment
        attrs = null;
        
        if ( isPresent )
        {
            attrs = getSchemaContext( service ).getAttributes( "m-oid=" + oid
                    + ",ou=objectClasses,cn=" + schemaName );
            assertNotNull( attrs );
        }
        else
        {
            //noinspection EmptyCatchBlock
            try
            {
                attrs = getSchemaContext( service ).getAttributes( "m-oid=" +
                        oid + ",ou=objectClasses,cn=" + schemaName );
                fail( "should never get here" );
            }
            catch( NamingException e )
            {
            }
            assertNull( attrs );
        }
        
        // -------------------------------------------------------------------
        // check to see if it is present in the matchingRuleRegistry
        // -------------------------------------------------------------------
        
        if ( isPresent ) 
        { 
            assertTrue( service.getSchemaManager().getObjectClassRegistry().contains( oid ) );
        }
        else
        {
            assertFalse( service.getSchemaManager().getObjectClassRegistry().contains( oid ) );
        }
    }
    
    
    /**
     * Tests a number of modify add, remove and replace operation combinations for
     * objectClasses on the schema subentry.
     *
     * @throws Exception on error
     */
    @Test
    public void testAddRemoveReplaceObjectClasses() throws Exception
    {
        enableSchema( "nis" );
        List<String> descriptions = new ArrayList<String>();

        // -------------------------------------------------------------------
        // test rejection with non-existant superclass
        // -------------------------------------------------------------------
        
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 SUP 1.2.3 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 SUP ( 1.2.3 $ 4.5.6 ) X-SCHEMA 'nis' )" );

        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
            fail( "Cannot add objectClass with bogus non-existant super" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );

        // -------------------------------------------------------------------
        // test add with existant superiors but no name and no desc
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "SUP 2.5.6.0 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "SUP 2.5.6.0 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add with existant superiors with names and no desc
        // -------------------------------------------------------------------

        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "objectClasses" );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) SUP 2.5.6.0 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) SUP 2.5.6.0 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add with existant superiors with names and desc
        // -------------------------------------------------------------------

        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "objectClasses" );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) DESC 'bogus' SUP 2.5.6.0 X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) DESC 'bogus' SUP 2.5.6.0 X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add with many existant superiors with names and desc
        // -------------------------------------------------------------------

        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "objectClasses" );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) DESC 'bogus' SUP ( 2.5.6.0 $ dynamicObject ) AUXILIARY X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) DESC 'bogus' SUP ( 2.5.6.0 $ domain ) X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", true );

        // -------------------------------------------------------------------
        // test reject with non-existant attributeType in may list
        // -------------------------------------------------------------------

        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "objectClasses" );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) DESC 'bogus' SUP ( 2.5.6.0 $ dynamicObject ) " +
                "MAY ( blah0 $ cn ) X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) DESC 'bogus' SUP ( 2.5.6.0 $ domain ) " +
                "MAY ( sn $ blah1 ) X-SCHEMA 'nis' )" );
        
        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
            fail( "Cannot add objectClass with bogus non-existant attributeTypes" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );

        // -------------------------------------------------------------------
        // test reject with non-existant attributeType in must list
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) DESC 'bogus' SUP ( 2.5.6.0 $ dynamicObject ) " +
                "MUST ( blah0 $ cn ) X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) DESC 'bogus' SUP ( 2.5.6.0 $ domain ) " +
                "MUST ( sn $ blah1 ) X-SCHEMA 'nis' )" );
        
        try
        {
            modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
            fail( "Cannot add objectClass with bogus non-existant attributeTypes" );
        }
        catch( OperationNotSupportedException e )
        {
        }
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );

        // -------------------------------------------------------------------
        // test add with valid attributeTypes in may list
        // -------------------------------------------------------------------

        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) DESC 'bogus' SUP ( 2.5.6.0 $ dynamicObject ) AUXILIARY " +
                "MAY ( sn $ cn ) X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) DESC 'bogus' SUP ( 2.5.6.0 $ domain ) " +
                "MAY ( sn $ ou ) X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add with valid attributeTypes in must list
        // -------------------------------------------------------------------

        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "objectClasses" );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) DESC 'bogus' SUP ( 2.5.6.0 $ dynamicObject ) AUXILIARY " +
                "MUST ( sn $ cn ) X-SCHEMA 'nis' )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) DESC 'bogus' SUP ( 2.5.6.0 $ domain ) " +
                "MUST ( sn $ ou ) X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", true );

        // -------------------------------------------------------------------
        // test add success full (with obsolete)
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "objectClasses" );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
                "NAME ( 'blah0' 'altname0' ) DESC 'bogus' OBSOLETE SUP ( 2.5.6.0 $ dynamicObject ) AUXILIARY " +
                "MUST ( sn $ cn ) " +
                "MAY ( gn $ ou ) " +
                "X-SCHEMA 'nis' ) " );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
                "NAME ( 'blah1' 'altname1' ) DESC 'bogus' OBSOLETE SUP ( 2.5.6.0 $ domain ) STRUCTURAL " +
                "MUST ( sn $ ou ) " +
                "MAY ( cn $ gn ) " +
                "X-SCHEMA 'nis' )" );
        
        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", true );

        // -------------------------------------------------------------------
        // test failure to replace
        // -------------------------------------------------------------------
        
        modify( DirContext.REMOVE_ATTRIBUTE, descriptions, "objectClasses" );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "nis", false );
        checkMatchingRulePresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "nis", false );
        
        try
        {
            modify( DirContext.REPLACE_ATTRIBUTE, descriptions, "objectClasses" );
            fail( "modify REPLACE operations should not be allowed" );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        // -------------------------------------------------------------------
        // check add no schema info
        // -------------------------------------------------------------------
        
        descriptions.clear();
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10000 " +
            "NAME ( 'blah0' 'altname0' ) DESC 'bogus' OBSOLETE SUP ( 2.5.6.0 $ dynamicObject ) AUXILIARY " +
            "MUST ( sn $ cn ) " +
            "MAY ( gn $ ou ) )" );
        descriptions.add( "( 1.3.6.1.4.1.18060.0.4.1.3.10001 " +
            "NAME ( 'blah1' 'altname1' ) DESC 'bogus' OBSOLETE SUP ( 2.5.6.0 $ domain ) STRUCTURAL " +
            "MUST ( sn $ ou ) " +
            "MAY ( gn $ cn ) )" );

        modify( DirContext.ADD_ATTRIBUTE, descriptions, "objectClasses" );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10000", "other", true );
        checkObjectClassPresent( "1.3.6.1.4.1.18060.0.4.1.3.10001", "other", true );
    }

    
    // -----------------------------------------------------------------------
    // Test Modifier and Timestamp Updates 
    // -----------------------------------------------------------------------
    
    
    /**
     * This method checks the modifiersName, and the modifyTimestamp on the schema
     * subentry then modifies a schema.  It then checks it again to make sure these
     * values have been updated properly to reflect the modification time and the
     * modifier.
     *
     * @throws InterruptedException on error
     * @throws NamingException on error
     */
    @Test
    @Ignore ( "Don't know why but this is causing intermittant failures in assertions" )
    public void testTimestampAndModifierUpdates() throws Exception, InterruptedException
    {
        TimeZone tz = TimeZone.getTimeZone( "GMT" );
        
        Attributes subentry = this.getSubschemaSubentryAttributes();
        
        // check first that everything that is required is present
        
        Attribute creatorsNameAttr = subentry.get( "creatorsName" );
        Attribute createTimestampAttr = subentry.get( "createTimestamp" );
        assertNotNull( creatorsNameAttr );
        assertNotNull( createTimestampAttr );

        Attribute modifiersNameAttr = subentry.get( "modifiersName" );
        Attribute modifyTimestampAttr = subentry.get( "modifyTimestamp" );
        assertNotNull( modifiersNameAttr );
        DN expectedDN = new DN( "uid=admin,ou=system" );
        expectedDN.normalize( service.getSchemaManager().getNormalizerMapping() );
        assertEquals( expectedDN.getNormName(), modifiersNameAttr.get() );
        assertNotNull( modifyTimestampAttr );

        Calendar cal = Calendar.getInstance( tz );
        String modifyTimestampStr = ( String ) modifyTimestampAttr.get();
        Date modifyTimestamp = DateUtils.getDate( modifyTimestampStr );
        Date currentTimestamp = cal.getTime();

        assertFalse( modifyTimestamp.after( currentTimestamp ) );
        
        // now update the schema information: add a new attribute type
        
        enableSchema( "nis" );
        DN dn = new DN( getSubschemaSubentryDN() );
        String substrate = "( 1.3.6.1.4.1.18060.0.4.0.2.10000 NAME ( 'bogus' 'bogusName' ) " +
            "DESC 'bogus description' SUP name SINGLE-VALUE X-SCHEMA 'nis' )";
        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem( DirContext.ADD_ATTRIBUTE, 
            new BasicAttribute( "attributeTypes", substrate ) );
        
        getRootContext( service ).modifyAttributes( DN.toName( dn ), mods );

        // now check the modification timestamp and the modifiers name

        subentry = this.getSubschemaSubentryAttributes();
        
        // check first that everything that is required is present
        
        Attribute creatorsNameAttrAfter = subentry.get( "creatorsName" );
        Attribute createTimestampAttrAfter = subentry.get( "createTimestamp" );
        assertNotNull( creatorsNameAttrAfter );
        assertNotNull( createTimestampAttrAfter );

        Attribute modifiersNameAttrAfter = subentry.get( "modifiersName" );
        Attribute modifiersTimestampAttrAfter = subentry.get( "modifyTimestamp" );
        assertNotNull( modifiersNameAttrAfter );
        expectedDN = new DN( "uid=admin,ou=system" );
        expectedDN.normalize( service.getSchemaManager().getNormalizerMapping() );
        assertEquals( expectedDN.getNormName(), modifiersNameAttrAfter.get() );
        assertNotNull( modifiersTimestampAttrAfter );
        
        cal = Calendar.getInstance( tz );
        Date modifyTimestampAfter = DateUtils.getDate( ( String ) modifiersTimestampAttrAfter.get() );
        assertTrue( modifyTimestampAfter.getTime() <= cal.getTime().getTime() );


        assertTrue( modifyTimestampAfter.getTime() >= modifyTimestamp.getTime() );

        // now let's test the modifiersName update with another user besides
        // the administrator - we'll create a dummy user for that ...
        
        Attributes user = new BasicAttributes( "objectClass", "person", true );
        user.put( "sn", "bogus" );
        user.put( "cn", "bogus user" );
        user.put( "userPassword", "secret" );
        getSystemContext( service ).createSubcontext( "cn=bogus user", user );
        
        // now let's get a context for this user
        
        Hashtable<String,Object> env = new Hashtable<String,Object>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, "org.apache.directory.server.core.jndi.CoreContextFactory" );
        env.put( Context.PROVIDER_URL, "" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_PRINCIPAL, "cn=bogus user,ou=system" );
        env.put( DirectoryService.JNDI_KEY, service );
        InitialDirContext ctx = new InitialDirContext( env );
        
        // now let's add another attribute type definition to the schema but 
        // with this newly created user and check that the modifiers name is his

        substrate = "( 1.3.6.1.4.1.18060.0.4.0.2.10001 NAME ( 'bogus2' 'bogusName2' ) " +
            "DESC 'bogus description' SUP name SINGLE-VALUE X-SCHEMA 'nis' )";
        mods[0] = new ModificationItem( DirContext.ADD_ATTRIBUTE, 
            new BasicAttribute( "attributeTypes", substrate ) );
        ctx.modifyAttributes( DN.toName( dn ), mods );
        
        // now let's verify the new values for the modification attributes

        subentry = this.getSubschemaSubentryAttributes();

        creatorsNameAttrAfter = subentry.get( "creatorsName" );
        createTimestampAttrAfter = subentry.get( "createTimestamp" );
        assertNotNull( creatorsNameAttrAfter );
        assertNotNull( createTimestampAttrAfter );

        modifiersNameAttrAfter = subentry.get( "modifiersName" );
        modifiersTimestampAttrAfter = subentry.get( "modifyTimestamp" );
        assertNotNull( modifiersNameAttrAfter );
        expectedDN = new DN( "cn=bogus user,ou=system" );
        expectedDN.normalize( service.getSchemaManager().getNormalizerMapping() );
        assertEquals( expectedDN.getNormName(), modifiersNameAttrAfter.get() );
        assertNotNull( modifiersTimestampAttrAfter );
        
        cal = Calendar.getInstance( tz );
        modifyTimestamp = DateUtils.getDate( ( String ) modifyTimestampAttr.get() );
        modifyTimestampAfter = DateUtils.getDate( ( String ) modifiersTimestampAttrAfter.get() );
        assertTrue( modifyTimestampAfter.getTime() <= cal.getTime().getTime() );
        assertTrue( modifyTimestampAfter.getTime() >= modifyTimestamp.getTime() );
    }


    // -----------------------------------------------------------------------
    // Private Utility Methods 
    // -----------------------------------------------------------------------
    
    private void modify( int op, List<String> descriptions, String opAttr ) throws Exception
    {
        DN dn = new DN( getSubschemaSubentryDN() );
        
        // Uses ModificationItem to keep the modification ordering
        ModificationItem[] modifications = new ModificationItem[ descriptions.size()];
        int i = 0;
        
        for ( String description : descriptions )
        {
            modifications[i++] = new ModificationItem( op, new BasicAttribute( opAttr, description ) );
        }
        
        getRootContext( service ).modifyAttributes( DN.toName( dn ), modifications );
    }
    
    
    private void enableSchema( String schemaName ) throws Exception
    {
        // now enable the test schema
        ModificationItem[] mods = new ModificationItem[1];
        Attribute attr = new BasicAttribute( "m-disabled", "FALSE" );
        mods[0] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, attr );
        getSchemaContext( service ).modifyAttributes( "cn=" + schemaName, mods );
    }
    
    
    private void disableSchema( String schemaName ) throws Exception
    {
        // now enable the test schema
        ModificationItem[] mods = new ModificationItem[1];
        Attribute attr = new BasicAttribute( "m-disabled", "TRUE" );
        mods[0] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, attr );
        getSchemaContext( service ).modifyAttributes( "cn=" + schemaName, mods );
    }
    
    
    private String getByteCode( String resource ) throws IOException
    {
        InputStream in = getClass().getResourceAsStream( resource );
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while ( in.available() > 0 )
        {
            out.write( in.read() );
        }
        
        return new String( Base64.encode( out.toByteArray() ) );
    }


    /**
     * Get's the subschemaSubentry attribute value from the rootDSE.
     * 
     * @return the subschemaSubentry distinguished name
     * @throws NamingException if there are problems accessing the RootDSE
     */
    private String getSubschemaSubentryDN() throws Exception
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        controls.setReturningAttributes( new String[]{ SUBSCHEMA_SUBENTRY } );
        
        NamingEnumeration<SearchResult> results = getRootContext( service )
                .search( "", "(objectClass=*)", controls );
        SearchResult result = results.next();
        results.close();
        Attribute subschemaSubentry = result.getAttributes().get( SUBSCHEMA_SUBENTRY );
        
        return ( String ) subschemaSubentry.get();
    }

    
    /**
     * Gets the subschemaSubentry attributes for the global schema.
     *
     * @return all operational attributes of the subschemaSubentry
     * @throws NamingException if there are problems accessing this entry
     */
    private Attributes getSubschemaSubentryAttributes() throws Exception
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        controls.setReturningAttributes( new String[]{ "+", "*" } );

        NamingEnumeration<SearchResult> results = getRootContext( service )
                .search( getSubschemaSubentryDN(), "(objectClass=*)", controls );
        SearchResult result = results.next();
        results.close();
        return result.getAttributes();
    }
}
