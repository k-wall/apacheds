/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.entry;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.naming.directory.InvalidAttributeValueException;

import org.apache.commons.io.FileUtils;
import org.apache.directory.shared.ldap.entry.BinaryValue;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.DefaultServerAttribute;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.entry.client.DefaultClientAttribute;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.ldif.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.loader.ldif.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.ldap.util.ExceptionUtils;
import org.apache.directory.shared.ldap.util.StringTools;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * Tests for the DefaultServerAttribute class
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class DefaultServerAttributeTest
{
    private static LdifSchemaLoader loader;
    
    private static AttributeType atCN;
    private static AttributeType atSN;
    
    // A SINGLE-VALUE attribute
    private static AttributeType atC;   
    
    // A Binary attribute
    private static AttributeType atPwd;

    private static final Value<String> NULL_STRING_VALUE = new StringValue( (String)null );
    private static final Value<byte[]> NULL_BINARY_VALUE = new BinaryValue( (byte[])null );
    private static final byte[] BYTES1 = new byte[]{ 'a', 'b' };
    private static final byte[] BYTES2 = new byte[]{ 'b' };
    private static final byte[] BYTES3 = new byte[]{ 'c' };
    private static final byte[] BYTES4 = new byte[]{ 'd' };
    
    private static final StringValue STR_VALUE1 = new StringValue( "a" );
    private static final StringValue STR_VALUE2 = new StringValue( "b" );
    private static final StringValue STR_VALUE3 = new StringValue( "c" );
    private static final StringValue STR_VALUE4 = new StringValue( "d" );

    private static final BinaryValue BIN_VALUE1 = new BinaryValue( BYTES1 );
    private static final BinaryValue BIN_VALUE2 = new BinaryValue( BYTES2 );
    private static final BinaryValue BIN_VALUE3 = new BinaryValue( BYTES3 );
    private static final BinaryValue BIN_VALUE4 = new BinaryValue( BYTES4 );

        
    /**
     * Initialize the registries once for the whole test suite
     */
    @BeforeClass
    public static void setup() throws Exception
    {
    	String workingDirectory = System.getProperty( "workingDirectory" );
    	
    	if ( workingDirectory == null )
    	{
    	    String path = DefaultServerAttributeTest.class.getResource( "" ).getPath();
    	    int targetPos = path.indexOf( "target" );
            workingDirectory = path.substring( 0, targetPos + 6 );
    	}
    	
        // Cleanup the target directory
        File schemaRepository = new File( workingDirectory, "schema" );
        FileUtils.deleteDirectory( schemaRepository );
    	
        SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( new File( workingDirectory ) );
        extractor.extractOrCopy( true );
    	loader = new LdifSchemaLoader( schemaRepository );
        SchemaManager schemaManager = new DefaultSchemaManager( loader );

        schemaManager.loadAllEnabled();
        
        List<Throwable> errors = schemaManager.getErrors();
        
        if ( errors.size() != 0 )
        {
            // We have inconsistencies : log them and exit.
            throw new RuntimeException( "Inconsistent schemas : " + 
                ExceptionUtils.printErrors( errors ) );
        }
        
        atCN = schemaManager.lookupAttributeTypeRegistry( "cn" );
        atC = schemaManager.lookupAttributeTypeRegistry( "c" );
        atSN = schemaManager.lookupAttributeTypeRegistry( "sn" );
        atPwd = schemaManager.lookupAttributeTypeRegistry( "userpassword" );
    }

    /**
     * Serialize a DefaultServerAttribute
     */
    private ByteArrayOutputStream serializeValue( DefaultServerAttribute value ) throws IOException
    {
        ObjectOutputStream oOut = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try
        {
            oOut = new ObjectOutputStream( out );
            value.serialize( oOut );
        }
        catch ( IOException ioe )
        {
            throw ioe;
        }
        finally
        {
            try
            {
                if ( oOut != null )
                {
                    oOut.flush();
                    oOut.close();
                }
            }
            catch ( IOException ioe )
            {
                throw ioe;
            }
        }
        
        return out;
    }
    
    
    /**
     * Deserialize a DefaultServerAttribute
     */
    private DefaultServerAttribute deserializeValue( ByteArrayOutputStream out, AttributeType at ) throws IOException, ClassNotFoundException
    {
        ObjectInputStream oIn = null;
        ByteArrayInputStream in = new ByteArrayInputStream( out.toByteArray() );

        try
        {
            oIn = new ObjectInputStream( in );

            DefaultServerAttribute value = new DefaultServerAttribute( at );
            value.deserialize( oIn );
            
            return value;
        }
        catch ( IOException ioe )
        {
            throw ioe;
        }
        finally
        {
            try
            {
                if ( oIn != null )
                {
                    oIn.close();
                }
            }
            catch ( IOException ioe )
            {
                throw ioe;
            }
        }
    }

    
    @Test public void testAddOneValue() throws Exception
    {
        AttributeType at = TestServerEntryUtils.getIA5StringAttributeType();
        
        DefaultServerAttribute attr = new DefaultServerAttribute( at );
        
        // Add a String value
        attr.add( "test" );
        
        assertEquals( 1, attr.size() );
        
        assertTrue( attr.getAttributeType().getSyntax().isHumanReadable() );
        
        Value<?> value = attr.get();
        
        assertTrue( value instanceof StringValue );
        assertEquals( "test", ((StringValue)value).getString() );
        
        // Add a binary value
        assertEquals( 0, attr.add( new byte[]{0x01} ) );
        
        // Add a Value
        Value<?> ssv = new StringValue( at, "test2" );
        
        attr.add( ssv );
        
        assertEquals( 2, attr.size() );
        
        Set<String> expected = new HashSet<String>();
        expected.add( "test" );
        expected.add( "test2" );
        
        for ( Value<?> val:attr )
        {
            if ( expected.contains( val.get() ) )
            {
                expected.remove( val.get() );
            }
            else
            {
                fail();
            }
        }
        
        assertEquals( 0, expected.size() );
    }


    @Test public void testAddTwoValue() throws Exception
    {
        AttributeType at = TestServerEntryUtils.getIA5StringAttributeType();
        
        DefaultServerAttribute attr = new DefaultServerAttribute( at );
        
        // Add String values
        attr.add( "test" );
        attr.add( "test2" );
        
        assertEquals( 2, attr.size() );
        
        assertTrue( attr.getAttributeType().getSyntax().isHumanReadable() );
        
        Set<String> expected = new HashSet<String>();
        expected.add( "test" );
        expected.add( "test2" );
        
        for ( Value<?> val:attr )
        {
            if ( expected.contains( val.get() ) )
            {
                expected.remove( val.get() );
            }
            else
            {
                fail();
            }
        }
        
        assertEquals( 0, expected.size() );
    }


    @Test public void testAddNullValue() throws Exception
    {
        AttributeType at = TestServerEntryUtils.getIA5StringAttributeType();
        
        DefaultServerAttribute attr = new DefaultServerAttribute( at );
        
        // Add a null value
        attr.add( new StringValue( at, null ) );
        
        assertEquals( 1, attr.size() );
        
        assertTrue( attr.getAttributeType().getSyntax().isHumanReadable() );
        
        Value<?> value = attr.get();
        
        assertTrue( value instanceof StringValue );
        assertNull( ((StringValue)value).get() );
    }
    

    @Test public void testGetAttribute() throws Exception
    {
        AttributeType at = TestServerEntryUtils.getIA5StringAttributeType();
        
        DefaultServerAttribute attr = new DefaultServerAttribute( at );
        
        attr.add( "Test1" );
        attr.add( "Test2" );
        attr.add( "Test3" );
        
        assertEquals( "1.1",attr.getId() );
        assertEquals( 3, attr.size() );
        assertTrue( attr.contains( "Test1" ) );
        assertTrue( attr.contains( "Test2" ) );
        assertTrue( attr.contains( "Test3" ) );
    }


    /**
     * Test the contains() method
     */
    @Test public void testContains() throws Exception
    {
        AttributeType at = TestServerEntryUtils.getIA5StringAttributeType();
        
        DefaultServerAttribute attr = new DefaultServerAttribute( at );
        
        attr.add( "Test  1" );
        attr.add( "Test  2" );
        attr.add( "Test  3" );
        
        assertTrue( attr.contains( "test 1" ) );
        assertTrue( attr.contains( "Test 2" ) );
        assertTrue( attr.contains( "TEST     3" ) );
    }


    /**
     * Test method getBytes()
     */
    @Test
    public void testGetBytes() throws LdapInvalidAttributeValueException
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atPwd );
        
        attr1.add( (byte[])null );
        assertNull( attr1.getBytes() );

        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );
        
        attr2.add( BYTES1, BYTES2 );
        assertTrue( Arrays.equals( BYTES1, attr2.getBytes() ) );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atCN );
        
        attr3.add( "a", "b" );
        
        try
        {
            attr3.getBytes();
            fail();
        }
        catch ( LdapInvalidAttributeValueException ivae )
        {
            assertTrue( true );
        }
    }


    /**
     * Test method getId()
     */
    @Test
    public void testGetId()
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );

        assertEquals( "cn", attr.getId() );
        
        attr.setId(  "  CN  " );
        assertEquals( "cn", attr.getId() );

        attr.setId(  "  CommonName  " );
        assertEquals( "commonname", attr.getId() );

        attr.setId(  "  2.5.4.3  " );
        assertEquals( "2.5.4.3", attr.getId() );
    }


    /**
     * Test method getString()
     */
    @Test
    public void testGetString() throws LdapInvalidAttributeValueException
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        attr1.add( (String)null );
        assertEquals( "", attr1.getString() );

        EntryAttribute attr2 = new DefaultServerAttribute( atCN );
        
        attr2.add( "a", "b" );
        assertEquals( "a", attr2.getString() );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atPwd );
        
        attr3.add( BYTES1, BYTES2 );
        
        try
        {
            attr3.getString();
            fail();
        }
        catch ( LdapInvalidAttributeValueException ivae )
        {
            assertTrue( true );
        }
    }


    /**
     * Test method getUpId
     */
    @Test
    public void testGetUpId()
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );

        assertNotNull( attr.getUpId() );
        assertEquals( "cn", attr.getUpId() );
        
        attr.setUpId( "CN" );
        assertEquals( "CN", attr.getUpId() );
        
        attr.setUpId( "  Cn  " );
        assertEquals( "  Cn  ", attr.getUpId() );

        attr.setUpId( "  2.5.4.3  " );
        assertEquals( "  2.5.4.3  ", attr.getUpId() );
    }


    /**
     * Test method hashCode()
     */
    @Test
    public void testHashCode()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        EntryAttribute attr2 = new DefaultServerAttribute( atSN );
        assertNotSame( attr1.hashCode(), attr2.hashCode() );
        
        attr2.setAttributeType( atCN );
        assertEquals( attr1.hashCode(), attr2.hashCode() );
        
        attr1.put( (String)null );
        assertNotSame( attr1.hashCode(), attr2.hashCode() );

        attr1.clear();
        assertEquals( attr1.hashCode(), attr2.hashCode() );
        
        attr1.put( "a", "b" );
        assertNotSame( attr1.hashCode(), attr2.hashCode() );

        attr2.put( "a", "b" );
        assertEquals( attr1.hashCode(), attr2.hashCode() );
        
        // Order matters
        attr2.put( "b", "a" );
        assertNotSame( attr1.hashCode(), attr2.hashCode() );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atPwd );
        EntryAttribute attr4 = new DefaultServerAttribute( atPwd );
        assertNotSame( attr3.hashCode(), attr4.hashCode() );
        
        attr3.put( (byte[])null );
        assertNotSame( attr3.hashCode(), attr4.hashCode() );

        attr3.clear();
        assertEquals( attr3.hashCode(), attr4.hashCode() );
        
        attr3.put( new byte[]{0x01, 0x02}, new byte[]{0x03, 0x04} );
        assertNotSame( attr1.hashCode(), attr2.hashCode() );

        attr4.put( new byte[]{0x01, 0x02}, new byte[]{0x03, 0x04} );
        assertNotSame( attr1.hashCode(), attr2.hashCode() );

        // Order matters
        attr4.put( new byte[]{0x03, 0x04}, new byte[]{0x01, 0x02} );
        assertNotSame( attr1.hashCode(), attr2.hashCode() );
    }
    
    
    /**
     * Test method SetId(String)
     */
    @Test
    public void testSetId()
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );

        attr.setId( "Cn" );
        assertEquals( "cn", attr.getId() );
        
        attr.setId( " CN " );
        assertEquals( "cn", attr.getId() );
        
        attr.setId( " 2.5.4.3 " );
        assertEquals( "2.5.4.3", attr.getId() );
        
        attr.setId( " commonName " );
        assertEquals( "commonname", attr.getId() );

        try
        {
            attr.setId( null );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            assertTrue( true );
        }
        
        try
        {
            attr.setId( "" );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            assertTrue( true );
        }
        
        try
        {
            attr.setId( "  " );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            assertTrue( true );
        }
        
        try
        {
            attr.setId( " SN " );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            assertTrue( true );
        }
    }


    /**
     * Test method isValid()
     */
    @Test
    public void testIsValid() throws Exception
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        // No value, this should not be valid
        assertFalse( attr.isValid() );
        
        attr.add( "test", "test2", "A123\\;" );
        assertTrue( attr.isValid() );

        // If we try to add a wrong value, it will not be added. The
        // attribute remains valid.
        assertEquals(0, attr.add( new byte[]{0x01} ) );
        assertTrue( attr.isValid() );

        // test a SINGLE-VALUE attribute. CountryName is SINGLE-VALUE
        attr.setAttributeType( atC );
        attr.put( "FR" );
        assertTrue( attr.isValid() );
        attr.add( "US" );
        assertFalse( attr.isValid() );
    }


    /**
     * Test method add( Value... )
     */
    @Test
    public void testAddValueArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        int nbAdded = attr1.add( new StringValue( atCN, null ) );
        assertEquals( 1, nbAdded );
        assertTrue( attr1.isHR() );
        assertEquals( NULL_STRING_VALUE, attr1.get() );
        
        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr2.add( new BinaryValue( atPwd, null ) );
        assertEquals( 1, nbAdded );
        assertFalse( attr2.isHR() );
        assertEquals( NULL_BINARY_VALUE, attr2.get() );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr3.add( new StringValue( atCN, "a" ), new StringValue( atCN, "b" ) );
        assertEquals( 2, nbAdded );
        assertTrue( attr3.isHR() );
        assertTrue( attr3.contains( "a" ) );
        assertTrue( attr3.contains( "b" ) );
        
        EntryAttribute attr4 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr4.add( new BinaryValue( atPwd, BYTES1 ), new BinaryValue( atPwd, BYTES2 ) );
        assertEquals( 0, nbAdded );
        assertTrue( attr4.isHR() );
        assertFalse( attr4.contains( BYTES1 ) );
        assertFalse( attr4.contains( BYTES2 ) );
        
        EntryAttribute attr5 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr5.add( new StringValue( atCN, "c" ), new BinaryValue( atPwd, BYTES1 ) );
        assertEquals( 1, nbAdded );
        assertTrue( attr5.isHR() );
        assertFalse( attr5.contains( "ab" ) );
        assertTrue( attr5.contains( "c" ) );

        EntryAttribute attr6 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr6.add( new BinaryValue( atPwd, BYTES1 ), new StringValue( atCN, "c" ) );
        assertEquals( 1, nbAdded );
        assertFalse( attr6.isHR() );
        assertTrue( attr6.contains( BYTES1 ) );
        assertFalse( attr6.contains( BYTES3 ) );

        EntryAttribute attr7 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr7.add( new BinaryValue( atPwd, null ), new StringValue( atCN, "c" ) );
        assertEquals( 1, nbAdded );
        assertFalse( attr7.isHR() );
        assertTrue( attr7.contains( NULL_BINARY_VALUE ) );
        assertFalse( attr7.contains( BYTES3 ) );

        EntryAttribute attr8 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr8.add( new StringValue( atCN, null ), new BinaryValue( atPwd, BYTES1 ) );
        assertEquals( 1, nbAdded );
        assertTrue( attr8.isHR() );
        assertTrue( attr8.contains( NULL_STRING_VALUE ) );
        assertFalse( attr8.contains( "ab" ) );

    
        EntryAttribute attr9 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr9.add( new StringValue( (String)null ), new StringValue( "ab" ) );
        assertEquals( 2, nbAdded );
        assertTrue( attr9.isHR() );
        assertTrue( attr9.contains( NULL_STRING_VALUE ) );
        assertTrue( attr9.contains( "ab" ) );

        EntryAttribute attr10 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr10.add( new BinaryValue( (byte[])null ), new BinaryValue( BYTES1 ) );
        assertEquals( 2, nbAdded );
        assertFalse( attr10.isHR() );
        assertTrue( attr10.contains( NULL_BINARY_VALUE ) );
        assertTrue( attr10.contains( BYTES1 ) );
    }


    /**
     * Test method add( String... )
     */
    @Test
    public void testAddStringArray() throws LdapInvalidAttributeValueException
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        int nbAdded = attr1.add( (String)null );
        assertEquals( 1, nbAdded );
        assertTrue( attr1.isHR() );
        assertEquals( NULL_STRING_VALUE, attr1.get() );
        
        EntryAttribute attr2 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr2.add( "" );
        assertEquals( 1, nbAdded );
        assertTrue( attr2.isHR() );
        assertEquals( "", attr2.getString() );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr3.add( "t" );
        assertEquals( 1, nbAdded );
        assertTrue( attr3.isHR() );
        assertEquals( "t", attr3.getString() );
        
        EntryAttribute attr4 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr4.add( "a", "b", "c", "d" );
        assertEquals( 4, nbAdded );
        assertTrue( attr4.isHR() );
        assertEquals( "a", attr4.getString() );
        assertTrue( attr4.contains( "a" ) );
        assertTrue( attr4.contains( "b" ) );
        assertTrue( attr4.contains( "c" ) );
        assertTrue( attr4.contains( "d" ) );
        
        nbAdded = attr4.add( "e" );
        assertEquals( 1, nbAdded );
        assertTrue( attr4.isHR() );
        assertEquals( "a", attr4.getString() );
        assertTrue( attr4.contains( "a" ) );
        assertTrue( attr4.contains( "b" ) );
        assertTrue( attr4.contains( "c" ) );
        assertTrue( attr4.contains( "d" ) );
        assertTrue( attr4.contains( "e" ) );
        
        nbAdded = attr4.add( BYTES1 );
        assertEquals( 0, nbAdded );
        assertTrue( attr4.isHR() );
        assertEquals( "a", attr4.getString() );
        assertTrue( attr4.contains( "a" ) );
        assertTrue( attr4.contains( "b" ) );
        assertTrue( attr4.contains( "c" ) );
        assertTrue( attr4.contains( "d" ) );
        assertTrue( attr4.contains( "e" ) );
        assertFalse( attr4.contains( "ab" ) );
        
        EntryAttribute attr5 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr5.add( "a", "b", (String)null, "d" );
        assertEquals( 4, nbAdded );
        assertTrue( attr5.isHR() );
        assertTrue( attr5.contains( "a" ) );
        assertTrue( attr5.contains( "b" ) );
        assertTrue( attr5.contains( NULL_STRING_VALUE ) );
        assertTrue( attr5.contains( "d" ) );

        EntryAttribute attr6 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr6.add( "a", (String)null );
        assertEquals( 0, nbAdded );
        assertFalse( attr6.isHR() );
    }


    /**
     * Test method add( byte[]... )
     */
    @Test
    public void testAddByteArray() throws LdapInvalidAttributeValueException
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atPwd );
        
        int nbAdded = attr1.add( (byte[])null );
        assertEquals( 1, nbAdded );
        assertFalse( attr1.isHR() );
        assertTrue( Arrays.equals( NULL_BINARY_VALUE.getBytes(), attr1.getBytes() ) );
        
        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr2.add( StringTools.EMPTY_BYTES );
        assertEquals( 1, nbAdded );
        assertFalse( attr2.isHR() );
        assertTrue( Arrays.equals( StringTools.EMPTY_BYTES, attr2.getBytes() ) );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr3.add( BYTES1 );
        assertEquals( 1, nbAdded );
        assertFalse( attr3.isHR() );
        assertTrue( Arrays.equals( BYTES1, attr3.getBytes() ) );
        
        EntryAttribute attr4 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr4.add( BYTES1, BYTES2, BYTES3, BYTES4 );
        assertEquals( 4, nbAdded );
        assertFalse( attr4.isHR() );
        assertTrue( attr4.contains( BYTES1 ) );
        assertTrue( attr4.contains( BYTES2 ) );
        assertTrue( attr4.contains( BYTES3 ) );
        assertTrue( attr4.contains( BYTES4 ) );
        
        EntryAttribute attr5 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr5.add( BYTES1, BYTES2, (byte[])null, BYTES3 );
        assertEquals( 4, nbAdded );
        assertFalse( attr5.isHR() );
        assertTrue( attr5.contains( BYTES1 ) );
        assertTrue( attr5.contains( BYTES2 ) );
        assertTrue( attr5.contains( (byte[])null ) );
        assertTrue( attr5.contains( BYTES3 ) );

        EntryAttribute attr6 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr6.add( "ab", (String)null );
        assertEquals( 0, nbAdded );
        assertFalse( attr6.isHR() );
    }


    /**
     * Test method clear()
     */
    @Test
    public void testClear()
    {
        EntryAttribute attr = new DefaultServerAttribute( "cn", atCN );
        
        assertEquals( 0, attr.size() );
        
        attr.add( (String)null, "a", "b" );
        assertEquals( 3, attr.size() );
        
        attr.clear();
        assertTrue( attr.isHR() );
        assertEquals( 0, attr.size() );
        assertEquals( atCN, attr.getAttributeType() );
    }


    /**
     * Test method contains( Value... )
     */
    @Test
    public void testContainsValueArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        assertEquals( 0, attr1.size() );
        assertFalse( attr1.contains( STR_VALUE1 ) );
        assertFalse( attr1.contains( NULL_STRING_VALUE ) );
        
        attr1.add( (String)null );
        assertEquals( 1, attr1.size() );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );
        
        attr1.remove( (String)null );
        assertFalse( attr1.contains( NULL_STRING_VALUE ) );
        assertEquals( 0, attr1.size() );
        
        attr1.add(  "a", "b", "c" );
        assertEquals( 3, attr1.size() );
        assertTrue( attr1.contains( STR_VALUE1 ) );
        assertTrue( attr1.contains( STR_VALUE2 ) );
        assertTrue( attr1.contains( STR_VALUE3 ) );
        assertTrue( attr1.contains( STR_VALUE1, STR_VALUE3 ) );
        assertFalse( attr1.contains( STR_VALUE4 ) );
        assertFalse( attr1.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );
        assertEquals( 0, attr2.size() );
        assertFalse( attr2.contains( BYTES1 ) );
        assertFalse( attr2.contains( NULL_BINARY_VALUE ) );
        
        attr2.add( (byte[])null );
        assertEquals( 1, attr2.size() );
        assertTrue( attr2.contains( NULL_BINARY_VALUE ) );
        
        attr2.remove( (byte[])null );
        assertFalse( attr2.contains( NULL_BINARY_VALUE ) );
        assertEquals( 0, attr2.size() );
        
        attr2.add( BYTES1, BYTES2, BYTES3 );
        assertEquals( 3, attr2.size() );
        assertTrue( attr2.contains( BIN_VALUE1 ) );
        assertTrue( attr2.contains( BIN_VALUE2 ) );
        assertTrue( attr2.contains( BIN_VALUE3 ) );
        assertFalse( attr2.contains( NULL_BINARY_VALUE ) );
    }


    /**
     * Test method contains( String... )
     */
    @Test
    public void testContainsStringArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        assertEquals( 0, attr1.size() );
        assertFalse( attr1.contains( "a" ) );
        assertFalse( attr1.contains( (String)null ) );
        
        attr1.add( (String)null );
        assertEquals( 1, attr1.size() );
        assertTrue( attr1.contains( (String)null ) );
        
        attr1.remove( (String)null );
        assertFalse( attr1.contains( (String)null ) );
        assertEquals( 0, attr1.size() );
        
        attr1.add(  "a", "b", "c" );
        assertEquals( 3, attr1.size() );
        assertTrue( attr1.contains( "a" ) );
        assertTrue( attr1.contains( "b" ) );
        assertTrue( attr1.contains( "c" ) );
        assertFalse( attr1.contains( "e" ) );
        assertFalse( attr1.contains( (String)null ) );
    }


    /**
     * Test method contains( byte[]... )
     */
    @Test
    public void testContainsByteArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atPwd );
        
        assertEquals( 0, attr1.size() );
        assertFalse( attr1.contains( BYTES1 ) );
        assertFalse( attr1.contains( (byte[])null ) );
        
        attr1.add( (byte[])null );
        assertEquals( 1, attr1.size() );
        assertTrue( attr1.contains( (byte[])null ) );
        
        attr1.remove( (byte[])null );
        assertFalse( attr1.contains( (byte[])null ) );
        assertEquals( 0, attr1.size() );
        
        attr1.add(  BYTES1, BYTES2, BYTES3 );
        assertEquals( 3, attr1.size() );
        assertTrue( attr1.contains( BYTES1 ) );
        assertTrue( attr1.contains( BYTES2 ) );
        assertTrue( attr1.contains( BYTES3 ) );
        assertFalse( attr1.contains( BYTES4 ) );
        assertFalse( attr1.contains( (byte[])null ) );
    }


    /**
     * Test method testEquals()
     */
    @Test
    public void testEquals()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        assertFalse( attr1.equals( null ) );
        
        EntryAttribute attr2 = new DefaultServerAttribute( atCN );
        
        assertTrue( attr1.equals( attr2 ) );
        
        attr2.setId( "CN" );
        assertTrue( attr1.equals( attr2 ) );

        attr1.setId( "CommonName" );
        assertTrue( attr1.equals( attr2 ) );
        
        attr1.setUpId( "CN" );
        assertTrue( attr1.equals( attr2 ) );
        
        attr1.add( "a", "b", "c" );
        attr2.add( "c", "b", "a" );
        assertTrue( attr1.equals( attr2 ) );
        
        attr1.setHR( true );
        attr2.setHR( false );
        assertTrue( attr1.equals( attr2 ) );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atPwd );
        EntryAttribute attr4 = new DefaultServerAttribute( atPwd );
        
        attr3.put( NULL_BINARY_VALUE );
        attr4.put( NULL_BINARY_VALUE );
        assertTrue( attr3.equals( attr4 ) );
        
        EntryAttribute attr5 = new DefaultServerAttribute( atPwd );
        EntryAttribute attr6 = new DefaultServerAttribute( atCN );
        assertFalse( attr5.equals( attr6 ) );
        
        attr5.put( NULL_BINARY_VALUE );
        attr6.put( NULL_STRING_VALUE );
        assertFalse( attr5.equals( attr6 ) );

        EntryAttribute attr7 = new DefaultServerAttribute( atCN );
        EntryAttribute attr8 = new DefaultServerAttribute( atPwd );
        
        attr7.put( "a" );
        attr8.put( BYTES2 );
        assertFalse( attr7.equals( attr8 ) );

        EntryAttribute attr9 = new DefaultServerAttribute( atCN );
        EntryAttribute attr10 = new DefaultServerAttribute( atPwd );
        
        attr7.put( "a" );
        attr7.add( BYTES2 );
        attr8.put( "a", "b" );
        assertFalse( attr9.equals( attr10 ) );
    }


    /**
     * Test method get()
     */
    @Test
    public void testGet()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( "cn", atCN );
        
        attr1.add( (String)null );
        assertEquals( NULL_STRING_VALUE,attr1.get() );

        EntryAttribute attr2 = new DefaultServerAttribute( "cn", atCN );
        
        attr2.add( "a", "b", "c" );
        assertEquals( "a", attr2.get().getString() );
        
        attr2.remove( "a" );
        assertEquals( "b", attr2.get().getString() );

        attr2.remove( "b" );
        assertEquals( "c", attr2.get().getString() );

        attr2.remove( "c" );
        assertNull( attr2.get() );

        EntryAttribute attr3 = new DefaultServerAttribute( "userPassword", atPwd );
        
        attr3.add( BYTES1, BYTES2, BYTES3 );
        assertTrue( Arrays.equals( BYTES1, attr3.get().getBytes() ) );
        
        attr3.remove( BYTES1 );
        assertTrue( Arrays.equals( BYTES2, attr3.get().getBytes() ) );

        attr3.remove( BYTES2 );
        assertTrue( Arrays.equals( BYTES3, attr3.get().getBytes() ) );

        attr3.remove( BYTES3 );
        assertNull( attr2.get() );
    }


    /**
     * Test method getAll()
     */
    @Test
    public void testGetAll()
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        Iterator<Value<?>> iterator = attr.getAll(); 
        assertFalse( iterator.hasNext() );
        
        attr.add( NULL_STRING_VALUE );
        iterator = attr.getAll(); 
        assertTrue( iterator.hasNext() );
        
        Value<?> value = iterator.next();
        assertEquals( NULL_STRING_VALUE, value );
        
        attr.clear();
        iterator = attr.getAll(); 
        assertFalse( iterator.hasNext() );
        
        attr.add(  "a", "b", "c" );
        iterator = attr.getAll(); 
        assertTrue( iterator.hasNext() );
        assertEquals( "a", iterator.next().getString() );
        assertEquals( "b", iterator.next().getString() );
        assertEquals( "c", iterator.next().getString() );
        assertFalse( iterator.hasNext() );
    }


    /**
     * Test method size()
     */
    @Test
    public void testSize() throws Exception
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );

        assertEquals( 0, attr1.size() );
        
        attr1.add( (String)null );
        assertEquals( 1, attr1.size() );

        EntryAttribute attr2 = new DefaultServerAttribute( atCN );
        
        attr2.add( "a", "b" );
        assertEquals( 2, attr2.size() );
        
        attr2.clear();
        assertEquals( 0, attr2.size() );

        EntryAttribute attr3 = new DefaultServerAttribute( atC );
        
        attr3.add( "US" );
        assertEquals( 1, attr3.size() );
        
        // TODO : forbid addition of more than 1 value for SINGLE-VALUE attributes
        attr3.add( "FR" );
        assertEquals( 2, attr3.size() );
    }


    /**
     * Test method put( byte[]... )
     */
    @Test
    public void testPutByteArray() throws InvalidAttributeValueException, Exception
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atPwd );
        
        int nbAdded = attr1.put( (byte[])null );
        assertEquals( 1, nbAdded );
        assertFalse( attr1.isHR() );
        assertTrue( Arrays.equals( NULL_BINARY_VALUE.getBytes(), attr1.getBytes() ) );
        
        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr2.put( StringTools.EMPTY_BYTES );
        assertEquals( 1, nbAdded );
        assertFalse( attr2.isHR() );
        assertTrue( Arrays.equals( StringTools.EMPTY_BYTES, attr2.getBytes() ) );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr3.put( BYTES1 );
        assertEquals( 1, nbAdded );
        assertFalse( attr3.isHR() );
        assertTrue( Arrays.equals( BYTES1, attr3.getBytes() ) );
        
        EntryAttribute attr4 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr4.put( BYTES1, BYTES2 );
        assertEquals( 2, nbAdded );
        assertFalse( attr4.isHR() );
        assertTrue( attr4.contains( BYTES1 ) );
        assertTrue( attr4.contains( BYTES2 ) );
        
        nbAdded = attr4.put( BYTES3, BYTES4 );
        assertEquals( 2, nbAdded );
        assertFalse( attr4.isHR() );
        assertTrue( attr4.contains( BYTES3 ) );
        assertTrue( attr4.contains( BYTES4 ) );
        
        EntryAttribute attr5 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr5.put( BYTES1, BYTES2, (byte[])null, BYTES3 );
        assertEquals( 4, nbAdded );
        assertFalse( attr5.isHR() );
        assertTrue( attr5.contains( BYTES1 ) );
        assertTrue( attr5.contains( BYTES2 ) );
        assertTrue( attr5.contains( (byte[])null ) );
        assertTrue( attr5.contains( BYTES3 ) );

        EntryAttribute attr6 = new DefaultServerAttribute( atPwd );
        
        attr6.setHR( true );
        assertFalse( attr6.isHR() );
        nbAdded = attr6.put( BYTES1, (byte[])null );
        assertEquals( 2, nbAdded );
        assertTrue( attr6.contains( BYTES1 ) );
        assertTrue( attr6.contains( (byte[])null ) );
    }


    /**
     * Test method put( String... )
     */
    @Test
    public void testPutStringArray() throws LdapInvalidAttributeValueException
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        int nbAdded = attr1.put( (String)null );
        assertEquals( 1, nbAdded );
        assertTrue( attr1.isHR() );
        assertEquals( NULL_STRING_VALUE, attr1.get() );
        
        EntryAttribute attr2 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr2.put( "" );
        assertEquals( 1, nbAdded );
        assertTrue( attr2.isHR() );
        assertEquals( "", attr2.getString() );
        
        EntryAttribute attr3 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr3.put( "t" );
        assertEquals( 1, nbAdded );
        assertTrue( attr3.isHR() );
        assertEquals( "t", attr3.getString() );
        
        EntryAttribute attr4 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr4.put( "a", "b", "c", "d" );
        assertEquals( 4, nbAdded );
        assertTrue( attr4.isHR() );
        assertEquals( "a", attr4.getString() );
        assertTrue( attr4.contains( "a" ) );
        assertTrue( attr4.contains( "b" ) );
        assertTrue( attr4.contains( "c" ) );
        assertTrue( attr4.contains( "d" ) );
        
        nbAdded = attr4.put( "e" );
        assertEquals( 1, nbAdded );
        assertTrue( attr4.isHR() );
        assertEquals( "e", attr4.getString() );
        assertFalse( attr4.contains( "a" ) );
        assertFalse( attr4.contains( "b" ) );
        assertFalse( attr4.contains( "c" ) );
        assertFalse( attr4.contains( "d" ) );
        assertTrue( attr4.contains( "e" ) );
        
        nbAdded = attr4.put( BYTES1 );
        assertEquals( 0, nbAdded );
        assertTrue( attr4.isHR() );
        
        EntryAttribute attr5 = new DefaultServerAttribute( atCN );
        
        nbAdded = attr5.put( "a", "b", (String)null, "d" );
        assertEquals( 4, nbAdded );
        assertTrue( attr5.isHR() );
        assertTrue( attr5.contains( "a" ) );
        assertTrue( attr5.contains( "b" ) );
        assertTrue( attr5.contains( NULL_STRING_VALUE ) );
        assertTrue( attr5.contains( "d" ) );

        EntryAttribute attr6 = new DefaultServerAttribute( atPwd );
        
        nbAdded = attr6.put( "a", (String)null );
        assertEquals( 0, nbAdded );
        assertFalse( attr6.isHR() );
    }


    /**
     * Test method put( Value... )
     */
    @Test
    public void testPutValueArray() throws Exception
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        assertEquals( 0, attr1.size() );
        
        attr1.put( NULL_STRING_VALUE );
        assertEquals( 1, attr1.size() );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );
        
        attr1.put( STR_VALUE1, STR_VALUE2, STR_VALUE3 );
        assertEquals( 3, attr1.size() );
        assertTrue( attr1.contains( STR_VALUE1 ) );
        assertTrue( attr1.contains( STR_VALUE2 ) );
        assertTrue( attr1.contains( STR_VALUE3 ) );

        attr1.put( STR_VALUE1, NULL_STRING_VALUE, STR_VALUE3 );
        assertEquals( 3, attr1.size() );
        assertTrue( attr1.contains( STR_VALUE1 ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );
        assertTrue( attr1.contains( STR_VALUE3 ) );
        
        attr1.put( STR_VALUE1, NULL_STRING_VALUE, BIN_VALUE3 );
        assertEquals( 2, attr1.size() );
        assertTrue( attr1.contains( STR_VALUE1 ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );
        assertFalse( attr1.contains( STR_VALUE3 ) );
        

        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );
        assertEquals( 0, attr2.size() );
        
        attr2.put( NULL_BINARY_VALUE );
        assertEquals( 1, attr2.size() );
        assertTrue( attr2.contains( NULL_BINARY_VALUE ) );
        
        attr2.put( BIN_VALUE1, BIN_VALUE2, BIN_VALUE3 );
        assertEquals( 3, attr2.size() );
        assertTrue( attr2.contains( BIN_VALUE1 ) );
        assertTrue( attr2.contains( BIN_VALUE2 ) );
        assertTrue( attr2.contains( BIN_VALUE3 ) );
        
        attr2.put( BIN_VALUE1, NULL_BINARY_VALUE, STR_VALUE3 );
        assertEquals( 2, attr2.size() );
        assertTrue( attr2.contains( BIN_VALUE1 ) );
        assertTrue( attr2.contains( NULL_BINARY_VALUE ) );
        assertFalse( attr2.contains( BIN_VALUE3 ) );
    }


    /**
     * Test method put( List&lt;Value&gt; )
     */
    @Test
    public void testPutListOfValues()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        
        assertEquals( 0, attr1.size() );
        
        List<Value<?>> list = new ArrayList<Value<?>>();
        list.add( NULL_STRING_VALUE );
        
        attr1.put( list );
        assertEquals( 1, attr1.size() );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );
        
        list.clear();
        list.add( STR_VALUE1 );
        list.add( STR_VALUE2 );
        list.add( STR_VALUE3 );
        attr1.put( list );
        assertEquals( 3, attr1.size() );
        assertTrue( attr1.contains( STR_VALUE1 ) );
        assertTrue( attr1.contains( STR_VALUE2 ) );
        assertTrue( attr1.contains( STR_VALUE3 ) );

        list.clear();
        list.add( STR_VALUE1 );
        list.add( NULL_STRING_VALUE );
        list.add( STR_VALUE3 );
        attr1.put( list );
        assertEquals( 3, attr1.size() );
        assertTrue( attr1.contains( STR_VALUE1 ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );
        assertTrue( attr1.contains( STR_VALUE3 ) );
        
        list.clear();
        list.add( STR_VALUE1 );
        list.add( NULL_STRING_VALUE );
        list.add( BIN_VALUE3 );
        attr1.put( list );
        assertEquals( 2, attr1.size() );
        assertTrue( attr1.contains( STR_VALUE1 ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );
        assertFalse( attr1.contains( STR_VALUE3 ) );
        

        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );
        assertEquals( 0, attr2.size() );
        
        list.clear();
        list.add( NULL_BINARY_VALUE );
        attr2.put( list );
        assertEquals( 1, attr2.size() );
        assertTrue( attr2.contains( NULL_BINARY_VALUE ) );
        
        list.clear();
        list.add( BIN_VALUE1 );
        list.add( BIN_VALUE2 );
        list.add( BIN_VALUE3 );
        attr2.put( list );
        assertEquals( 3, attr2.size() );
        assertTrue( attr2.contains( BIN_VALUE1 ) );
        assertTrue( attr2.contains( BIN_VALUE2 ) );
        assertTrue( attr2.contains( BIN_VALUE3 ) );
        
        list.clear();
        list.add( BIN_VALUE1 );
        list.add( NULL_BINARY_VALUE );
        list.add( STR_VALUE3 );
        attr2.put( list );
        assertEquals( 2, attr2.size() );
        assertTrue( attr2.contains( BIN_VALUE1 ) );
        assertTrue( attr2.contains( NULL_BINARY_VALUE ) );
        assertFalse( attr2.contains( BIN_VALUE3 ) );
    }


    /**
     * Test method remove( Value... )
     */
    @Test
    public void testRemoveValueArray() throws Exception
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );

        assertFalse( attr1.remove( STR_VALUE1 ) );

        attr1.setHR( true );
        assertFalse( attr1.remove( STR_VALUE1 ) );
        
        attr1.put( "a", "b", "c" );
        assertTrue( attr1.remove( STR_VALUE1 ) );
        assertEquals( 2, attr1.size() );
        
        assertTrue( attr1.remove( STR_VALUE2, STR_VALUE3 ) );
        assertEquals( 0, attr1.size() );
        
        assertFalse( attr1.remove( STR_VALUE4 ) );
        
        attr1.put( "a", "b", "c" );
        assertFalse( attr1.remove( STR_VALUE2, STR_VALUE4 ) );
        assertEquals( 2, attr1.size() );
        
        attr1.clear();
        attr1.put( "a", (String)null, "b" );
        assertTrue( attr1.remove( NULL_STRING_VALUE, STR_VALUE1 ) );
        assertEquals( 1, attr1.size() );
        
        attr1.clear();
        attr1.put( "a", (String)null, "b" );
        attr1.add( BYTES3 );
        assertFalse( attr1.remove( NULL_STRING_VALUE, STR_VALUE1, BIN_VALUE3 ) );
        assertEquals( 1, attr1.size() );
        
        EntryAttribute attr2 = new DefaultServerAttribute( atPwd );

        assertFalse( attr2.remove( BIN_VALUE1 ) );

        attr2.setHR( true );
        assertFalse( attr2.remove( BIN_VALUE1 ) );
        
        attr2.put( BYTES1, BYTES2, BYTES3 );
        assertTrue( attr2.remove( BIN_VALUE1 ) );
        assertEquals( 2, attr2.size() );
        
        assertTrue( attr2.remove( BIN_VALUE2, BIN_VALUE3 ) );
        assertEquals( 0, attr2.size() );
        
        assertFalse( attr2.remove( BIN_VALUE4 ) );
        
        attr2.put( BYTES1, BYTES2, BYTES3 );
        assertFalse( attr2.remove( BIN_VALUE2, STR_VALUE4 ) );
        assertEquals( 2, attr2.size() );
        
        attr2.clear();
        attr2.put( BYTES1, (byte[])null, BYTES3 );
        assertFalse( attr2.remove( NULL_STRING_VALUE, BIN_VALUE1 ) );
        assertEquals( 2, attr2.size() );
        
        attr2.clear();
        attr2.put( BYTES1, (byte[])null, BYTES2 );
        attr2.add( "c" );
        assertFalse( attr2.remove( NULL_STRING_VALUE, BIN_VALUE1, STR_VALUE3 ) );
        assertEquals( 2, attr2.size() );
    }


    /**
     * Test method remove( byte... )
     */
    @Test
    public void testRemoveByteArray() throws Exception
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atPwd );

        assertFalse( attr1.remove( BYTES1 ) );

        attr1.put( BYTES1, BYTES2, BYTES3 );
        assertTrue( attr1.remove( BYTES1 ) );
        assertEquals( 2, attr1.size() );
        
        assertTrue( attr1.remove( BYTES2, BYTES3 ) );
        assertEquals( 0, attr1.size() );
        
        assertFalse( attr1.remove( BYTES4 ) );
        
        attr1.put( BYTES1, BYTES2, BYTES3 );
        assertFalse( attr1.remove( BYTES3, BYTES4 ) );
        assertEquals( 2, attr1.size() );
        
        attr1.clear();
        attr1.put( BYTES1, (byte[])null, BYTES2 ) ;
        assertTrue( attr1.remove( (byte[])null, BYTES1 ) );
        assertEquals( 1, attr1.size() );
    }


    /**
     * Test method remove( String... )
     */
    @Test
    public void testRemoveStringArray() throws Exception
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );

        assertFalse( attr1.remove( "a" ) );

        attr1.setHR( true );
        assertFalse( attr1.remove( "a" ) );
        
        attr1.put( "a", "b", "c" );
        assertTrue( attr1.remove( "a" ) );
        assertEquals( 2, attr1.size() );
        
        assertTrue( attr1.remove( "b", "c" ) );
        assertEquals( 0, attr1.size() );
        
        assertFalse( attr1.remove( "d" ) );
        
        attr1.put( "a", "b", "c" );
        assertFalse( attr1.remove( "b", "e" ) );
        assertEquals( 2, attr1.size() );
        
        attr1.clear();
        attr1.put( "a", (String)null, "b" );
        assertTrue( attr1.remove( (String )null, "a" ) );
        assertEquals( 1, attr1.size() );
        
        EntryAttribute attr2 = new DefaultClientAttribute( "test" );
        
        attr2.put( BYTES1, BYTES2, BYTES3 );
        
        assertFalse( attr2.remove( (String)null ) );
        assertTrue( attr2.remove( "ab", "c" ) );
        assertFalse( attr2.remove( "d" ) );
    }


    /**
     * Test method iterator()
     */
    @Test
    public void testIterator()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN );
        attr1.add(  "a", "b", "c" );
        
        Iterator<Value<?>> iter = attr1.iterator();
        
        assertTrue( iter.hasNext() );
        
        String[] values = new String[]{ "a", "b", "c" };
        int pos = 0;
        
        for ( Value<?> val:attr1 )
        {
            assertTrue( val instanceof StringValue );
            assertEquals( values[pos++], val.getString() );
        }
    }


    /**
     * Test method toString
     */
    @Test
    public void testToString()
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        assertEquals( "    cn: (null)\n", attr.toString() );
        
        attr.setUpId( "CommonName" );
        assertEquals( "    CommonName: (null)\n", attr.toString() );
        
        attr.add( (String)null );
        assertEquals( "    CommonName: ''\n", attr.toString() );

        attr.put( "a", "b" );
        assertEquals( "    CommonName: a\n    CommonName: b\n", attr.toString() );
    }


    /**
     * Test method instanceOf()
     */
    @Test
    public void testInstanceOf() throws Exception
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        assertTrue( attr.instanceOf( "CommonName" ) );
        assertTrue( attr.instanceOf( "2.5.4.3" ) );
        assertTrue( attr.instanceOf( "  Cn  " ) );
        assertFalse( attr.instanceOf( "  " ) );
        assertFalse( attr.instanceOf( "sn" ) );
        assertFalse( attr.instanceOf( "name" ) );
    }


    /**
     * Test method setUpId( String, AttributeType )
     */
    @Test
    public void testSetUpIdStringAttributeType() throws Exception
    {
        EntryAttribute attr = new DefaultServerAttribute( atSN );
        
        attr.setUpId( null, atCN );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  ", atCN );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  CN  ", atCN );
        assertEquals( "cn", attr.getId() );
        assertEquals( "  CN  ", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  CommonName  ", atCN );
        assertEquals( "commonname", attr.getId() );
        assertEquals( "  CommonName  ", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  2.5.4.3  ", atCN );
        assertEquals( "2.5.4.3", attr.getId() );
        assertEquals( "  2.5.4.3  ", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        // Check with wrong IDs
        try
        {
            attr.setUpId( "sn", atCN );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            assertTrue( true );
        }

        try
        {
            attr.setUpId( "  2.5.4.4  ", atCN );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            assertTrue( true );
        }
    }


    /**
     * Test method setUpId( String ) inherited from ClientAttribute
     */
    @Test
    public void testSetUpIdString() throws Exception
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        attr.setUpId( "cn" );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  CN  " );
        assertEquals( "cn", attr.getId() );
        assertEquals( "  CN  ", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  CommonName  ");
        assertEquals( "commonname", attr.getId() );
        assertEquals( "  CommonName  ", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  2.5.4.3  " );
        assertEquals( "  2.5.4.3  ", attr.getUpId() );
        assertEquals( "2.5.4.3", attr.getId() );
        assertEquals( atCN, attr.getAttributeType() );
        
        // Now check wrong IDs
        attr = new DefaultServerAttribute( atCN );
        attr.setUpId( "sn" );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  SN  " );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  surname  " );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );

        attr.setUpId( "  2.5.4.4  " );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );
    }


    /**
     * Test method setAttributeType( AttributeType )
     */
    @Test
    public void testSetAttributeType() throws Exception
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        try
        {
            attr.setAttributeType( null );
            fail();
        }
        catch ( IllegalArgumentException iae )
        {
            assertTrue( true );
        }
        
        attr.setAttributeType( atSN );
        
        assertTrue( attr.instanceOf( "Surname" ) );
        assertEquals( "sn", attr.getId() );
        assertEquals( "sn", attr.getUpId() );
    }


    /**
     * Test method getAttributeType()
     */
    @Test
    public void testGetAttributeType() throws Exception
    {
        EntryAttribute attr = new DefaultServerAttribute( atSN );
        assertEquals( atSN, attr.getAttributeType() );
    }


    /**
     * Test constructor DefaultServerAttribute( AttributeType )
     */
    @Test
    public void testDefaultServerAttributeAttributeType()
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        assertTrue( attr.isHR() );
        assertEquals( 0, attr.size() );
        assertEquals( "cn", attr.getId() );
        assertEquals( "cn", attr.getUpId() );
        assertEquals( atCN, attr.getAttributeType() );
    }


    /**
     * Test constructor DefaultServerAttribute( String, AttributeType )
     */
    @Test
    public void testDefaultServerAttributeStringAttributeType()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( "cn", atCN );
        
        assertTrue( attr1.isHR() );
        assertEquals( 0, attr1.size() );
        assertEquals( "cn", attr1.getId() );
        assertEquals( "cn", attr1.getUpId() );
        assertEquals( atCN, attr1.getAttributeType() );

        EntryAttribute attr2 = new DefaultServerAttribute( "  CommonName  ", atCN );
        
        assertTrue( attr2.isHR() );
        assertEquals( 0, attr2.size() );
        assertEquals( "commonname", attr2.getId() );
        assertEquals( "  CommonName  ", attr2.getUpId() );
        assertEquals( atCN, attr2.getAttributeType() );

        EntryAttribute attr3 = new DefaultServerAttribute( "  ", atCN );
        
        assertTrue( attr3.isHR() );
        assertEquals( 0, attr3.size() );
        assertEquals( "cn", attr3.getId() );
        assertEquals( "cn", attr3.getUpId() );
        assertEquals( atCN, attr3.getAttributeType() );
    }


    /**
     * Test constructor DefaultServerAttribute( AttributeType, Value... )
     */
    @Test
    public void testDefaultServerAttributeAttributeTypeValueArray() throws Exception
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN, STR_VALUE1, STR_VALUE2, NULL_STRING_VALUE );
        
        assertTrue( attr1.isHR() );
        assertEquals( 3, attr1.size() );
        assertEquals( "cn", attr1.getId() );
        assertEquals( "cn", attr1.getUpId() );
        assertEquals( atCN, attr1.getAttributeType() );
        assertTrue( attr1.contains( "a", "b" ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr2 = new DefaultServerAttribute( atCN, STR_VALUE1, BIN_VALUE2, NULL_STRING_VALUE );
        
        assertTrue( attr2.isHR() );
        assertEquals( 2, attr2.size() );
        assertEquals( "cn", attr2.getId() );
        assertEquals( "cn", attr2.getUpId() );
        assertEquals( atCN, attr2.getAttributeType() );
        assertTrue( attr2.contains( "a" ) );
        assertTrue( attr2.contains( NULL_STRING_VALUE ) );
    }


    /**
     * Test constructor DefaultServerAttribute( String, AttributeType, Value... )
     */
    @Test
    public void testDefaultServerAttributeStringAttributeTypeValueArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( "cn", atCN, STR_VALUE1, STR_VALUE2, NULL_STRING_VALUE );
        
        assertTrue( attr1.isHR() );
        assertEquals( 3, attr1.size() );
        assertEquals( "cn", attr1.getId() );
        assertEquals( "cn", attr1.getUpId() );
        assertEquals( atCN, attr1.getAttributeType() );
        assertTrue( attr1.contains( "a", "b" ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr2 = new DefaultServerAttribute( atCN, STR_VALUE1, BIN_VALUE2, NULL_STRING_VALUE );
        
        assertTrue( attr2.isHR() );
        assertEquals( 2, attr2.size() );
        assertEquals( "cn", attr2.getId() );
        assertEquals( "cn", attr2.getUpId() );
        assertEquals( atCN, attr2.getAttributeType() );
        assertTrue( attr2.contains( "a" ) );
        assertTrue( attr2.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr3 = new DefaultServerAttribute( "CommonName", atCN, STR_VALUE1, STR_VALUE2, NULL_STRING_VALUE );
        
        assertTrue( attr3.isHR() );
        assertEquals( 3, attr3.size() );
        assertEquals( "commonname", attr3.getId() );
        assertEquals( "CommonName", attr3.getUpId() );
        assertEquals( atCN, attr3.getAttributeType() );
        assertTrue( attr3.contains( "a", "b" ) );
        assertTrue( attr3.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr4 = new DefaultServerAttribute( " 2.5.4.3 ", atCN, STR_VALUE1, STR_VALUE2, NULL_STRING_VALUE );
        
        assertTrue( attr4.isHR() );
        assertEquals( 3, attr4.size() );
        assertEquals( "2.5.4.3", attr4.getId() );
        assertEquals( " 2.5.4.3 ", attr4.getUpId() );
        assertEquals( atCN, attr4.getAttributeType() );
        assertTrue( attr4.contains( "a", "b" ) );
        assertTrue( attr4.contains( NULL_STRING_VALUE ) );
    }


    /**
     * Test constructor DefaultServerAttribute( AttributeType, String... ) 
     */
    @Test
    public void testDefaultServerAttributeAttributeTypeStringArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atCN, "a", "b", (String)null );
        
        assertTrue( attr1.isHR() );
        assertEquals( 3, attr1.size() );
        assertEquals( "cn", attr1.getId() );
        assertEquals( "cn", attr1.getUpId() );
        assertEquals( atCN, attr1.getAttributeType() );
        assertTrue( attr1.contains( "a", "b" ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr2 = new DefaultServerAttribute( atCN, STR_VALUE1, BIN_VALUE2, NULL_STRING_VALUE );
        
        assertTrue( attr2.isHR() );
        assertEquals( 2, attr2.size() );
        assertEquals( "cn", attr2.getId() );
        assertEquals( "cn", attr2.getUpId() );
        assertEquals( atCN, attr2.getAttributeType() );
        assertTrue( attr2.contains( "a" ) );
        assertTrue( attr2.contains( NULL_STRING_VALUE ) );
    }


    /**
     * Test constructor DefaultServerAttribute( String, AttributeType, String... )
     */
    @Test
    public void testDefaultServerAttributeStringAttributeTypeStringArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( "cn", atCN, "a", "b", (String)null );
        
        assertTrue( attr1.isHR() );
        assertEquals( 3, attr1.size() );
        assertEquals( "cn", attr1.getId() );
        assertEquals( "cn", attr1.getUpId() );
        assertEquals( atCN, attr1.getAttributeType() );
        assertTrue( attr1.contains( "a", "b" ) );
        assertTrue( attr1.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr2 = new DefaultServerAttribute( "CommonName", atCN, "a", "b", (String)null );
        
        assertTrue( attr2.isHR() );
        assertEquals( 3, attr2.size() );
        assertEquals( "commonname", attr2.getId() );
        assertEquals( "CommonName", attr2.getUpId() );
        assertEquals( atCN, attr2.getAttributeType() );
        assertTrue( attr2.contains( "a", "b" ) );
        assertTrue( attr2.contains( NULL_STRING_VALUE ) );

        EntryAttribute attr3 = new DefaultServerAttribute( " 2.5.4.3 ", atCN, "a", "b", (String)null );
        
        assertTrue( attr3.isHR() );
        assertEquals( 3, attr3.size() );
        assertEquals( "2.5.4.3", attr3.getId() );
        assertEquals( " 2.5.4.3 ", attr3.getUpId() );
        assertEquals( atCN, attr3.getAttributeType() );
        assertTrue( attr3.contains( "a", "b" ) );
        assertTrue( attr3.contains( NULL_STRING_VALUE ) );
    }


    /**
     * Test method DefaultServerAttribute( AttributeType, byte[]... ) 
     */
    @Test
    public void testDefaultServerAttributeAttributeTypeByteArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( atPwd, BYTES1, BYTES2, (byte[])null );
        
        assertFalse( attr1.isHR() );
        assertEquals( 3, attr1.size() );
        assertEquals( "userPassword", attr1.getId() );
        assertEquals( "userPassword", attr1.getUpId() );
        assertEquals( atPwd, attr1.getAttributeType() );
        assertTrue( attr1.contains( BYTES1, BYTES2 ) );
        assertTrue( attr1.contains( NULL_BINARY_VALUE ) );

        EntryAttribute attr2 = new DefaultServerAttribute( atPwd, STR_VALUE1, BIN_VALUE2, NULL_BINARY_VALUE );
        
        assertFalse( attr2.isHR() );
        assertEquals( 2, attr2.size() );
        assertEquals( "userPassword", attr2.getId() );
        assertEquals( "userPassword", attr2.getUpId() );
        assertEquals( atPwd, attr2.getAttributeType() );
        assertTrue( attr2.contains( BYTES2 ) );
        assertTrue( attr2.contains( NULL_BINARY_VALUE ) );
    }


    /**
     * Test method DefaultServerAttribute( String, AttributeType, byte[]... )
     */
    @Test
    public void testDefaultServerAttributeStringAttributeTypeByteArray()
    {
        EntryAttribute attr1 = new DefaultServerAttribute( "userPassword", atPwd, BYTES1, BYTES2, (byte[])null );
        
        assertFalse( attr1.isHR() );
        assertEquals( 3, attr1.size() );
        assertEquals( "userpassword", attr1.getId() );
        assertEquals( "userPassword", attr1.getUpId() );
        assertEquals( atPwd, attr1.getAttributeType() );
        assertTrue( attr1.contains( BYTES1, BYTES2 ) );
        assertTrue( attr1.contains( NULL_BINARY_VALUE ) );

        EntryAttribute attr2 = new DefaultServerAttribute( "2.5.4.35", atPwd, STR_VALUE1, BIN_VALUE2, NULL_BINARY_VALUE );
        
        assertFalse( attr2.isHR() );
        assertEquals( 2, attr2.size() );
        assertEquals( "2.5.4.35", attr2.getId() );
        assertEquals( "2.5.4.35", attr2.getUpId() );
        assertEquals( atPwd, attr2.getAttributeType() );
        assertTrue( attr2.contains( BYTES2 ) );
        assertTrue( attr2.contains( NULL_BINARY_VALUE ) );
    }


    /**
     * Test method testClone()
     */
    @Test
    public void testClone()
    {
        EntryAttribute attr = new DefaultServerAttribute( atCN );
        
        EntryAttribute clone = attr.clone();
        
        assertEquals( attr, clone );
        attr.setUpId( "CommonName" );
        assertEquals( "cn", clone.getId() );
        
        attr.add( "a", (String)null, "b" );
        clone = attr.clone();
        assertEquals( attr, clone );
        
        attr.remove( "a" );
        assertNotSame( attr, clone );
        
        clone = attr.clone();
        assertEquals( attr, clone );
    }
    
    
    /**
     * Test the copy constructor of a EntryAttribute
     */
    @Test 
    public void testCopyConstructorServerAttribute() throws LdapInvalidAttributeValueException
    {
        EntryAttribute attribute = new DefaultServerAttribute( atCN );
        
        EntryAttribute copy = new DefaultServerAttribute( atCN, attribute );
        
        assertEquals( copy, attribute );

        EntryAttribute attribute2 = new DefaultServerAttribute( atCN, "test" );
        
        EntryAttribute copy2 = new DefaultServerAttribute( atCN, attribute2 );
        
        assertEquals( copy2, attribute2 );
        attribute2.add( "test2" );
        assertNotSame( copy2, attribute2 );
        assertEquals( "test", copy2.getString() );
    }
    
    
    /**
     * Test the copy constructor of a ClientAttribute
     */
    @Test 
    public void testCopyConstructorClientAttribute() throws LdapInvalidAttributeValueException
    {
        EntryAttribute attribute = new DefaultClientAttribute( "commonName" );
        attribute.put( "test" );
        
        EntryAttribute copy = new DefaultServerAttribute( atCN, attribute );

        assertEquals( atCN, copy.getAttributeType() );
        assertEquals( "test", copy.getString() );
        assertTrue( copy.isHR() );
        
        attribute.add( "test2" );
        assertFalse( copy.contains( "test2" ) );
    }
    
    
    /**
     * Test the conversion method 
     */
    @Test 
    public void testToClientAttribute()
    {
        EntryAttribute attribute = new DefaultServerAttribute( atCN, "test", "test2" );
        
        EntryAttribute clientAttribute = attribute.toClientAttribute();
        
        assertTrue( clientAttribute instanceof EntryAttribute );
        
        assertTrue( clientAttribute.contains( "test", "test2" ) );
        assertEquals( "cn", clientAttribute.getId() );
        
        attribute.remove( "test", "test2" );
        assertTrue( clientAttribute.contains( "test", "test2" ) );
    }
    
    
    /**
     * Test the serialization of a complete server attribute
     */
    @Test
    public void testSerializeCompleteAttribute() throws LdapException, IOException, ClassNotFoundException
    {
        DefaultServerAttribute dsa = new DefaultServerAttribute( atCN );
        dsa.setHR( true );
        dsa.setUpId( "CommonName" );
        dsa.add( "test1", "test2" );

        DefaultServerAttribute dsaSer = deserializeValue( serializeValue( dsa ), atCN );
        assertEquals( dsa.toString(), dsaSer.toString() );
        assertEquals( "commonname", dsaSer.getId() );
        assertEquals( "CommonName", dsaSer.getUpId() );
        assertEquals( "test1", dsaSer.getString() );
        assertTrue( dsaSer.contains( "test2", "test1" ) );
        assertTrue( dsaSer.isHR() );
        assertTrue( dsaSer.isValid() );
    }
    
    
    /**
     * Test the serialization of a server attribute with no value
     */
    @Test
    public void testSerializeAttributeWithNoValue() throws LdapException, IOException, ClassNotFoundException
    {
        DefaultServerAttribute dsa = new DefaultServerAttribute( atCN );
        dsa.setHR( true );
        dsa.setId( "cn" );

        DefaultServerAttribute dsaSer = deserializeValue( serializeValue( dsa ), atCN );
        assertEquals( dsa.toString(), dsaSer.toString() );
        assertEquals( "cn", dsaSer.getId() );
        assertEquals( "cn", dsaSer.getUpId() );
        assertEquals( 0, dsaSer.size() );
        assertTrue( dsaSer.isHR() );
        assertFalse( dsaSer.isValid() );
    }
    
    
    /**
     * Test the serialization of a server attribute with a null value
     */
    @Test
    public void testSerializeAttributeNullValue() throws LdapException, IOException, ClassNotFoundException
    {
        DefaultServerAttribute dsa = new DefaultServerAttribute( atCN );
        dsa.setHR( true );
        dsa.setUpId( "CommonName" );
        dsa.add( (String)null );

        DefaultServerAttribute dsaSer = deserializeValue( serializeValue( dsa ), atCN );
        assertEquals( dsa.toString(), dsaSer.toString() );
        assertEquals( "commonname", dsaSer.getId() );
        assertEquals( "CommonName", dsaSer.getUpId() );
        assertEquals( "", dsaSer.getString() );
        assertEquals( 1, dsaSer.size() );
        assertTrue( dsaSer.contains( (String)null ) );
        assertTrue( dsaSer.isHR() );
        assertFalse( dsaSer.isValid() );
    }
    
    
    /**
     * Test the serialization of a server attribute with a binary value
     */
    @Test
    public void testSerializeAttributeBinaryValue() throws LdapException, IOException, ClassNotFoundException
    {
        DefaultServerAttribute dsa = new DefaultServerAttribute( atPwd );
        dsa.setHR( false );
        byte[] password = StringTools.getBytesUtf8( "secret" );
        dsa.add( password );

        DefaultServerAttribute dsaSer = deserializeValue( serializeValue( dsa ), atPwd );
        assertEquals( dsa.toString(), dsaSer.toString() );
        assertEquals( "userpassword", dsaSer.getId() );
        assertEquals( "userPassword", dsaSer.getUpId() );
        assertTrue( Arrays.equals( dsa.getBytes(), dsaSer.getBytes() ) );
        assertEquals( 1, dsaSer.size() );
        assertTrue( dsaSer.contains( password ) );
        assertFalse( dsaSer.isHR() );
        assertTrue( dsaSer.isValid() );
    }
}
