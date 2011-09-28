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
package org.apache.directory.server.core.authz;


import static org.apache.directory.server.core.authz.AutzIntegUtils.addUserToGroup;
import static org.apache.directory.server.core.authz.AutzIntegUtils.changePresciptiveACI;
import static org.apache.directory.server.core.authz.AutzIntegUtils.createAccessControlSubentry;
import static org.apache.directory.server.core.authz.AutzIntegUtils.createGroup;
import static org.apache.directory.server.core.authz.AutzIntegUtils.createUser;
import static org.apache.directory.server.core.authz.AutzIntegUtils.deleteAccessControlSubentry;
import static org.apache.directory.server.core.authz.AutzIntegUtils.getAdminConnection;
import static org.apache.directory.server.core.authz.AutzIntegUtils.getConnectionAs;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.message.ModifyRequest;
import org.apache.directory.ldap.client.api.message.ModifyResponse;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.core.integ.IntegrationUtils;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.client.ClientModification;
import org.apache.directory.shared.ldap.entry.client.DefaultClientAttribute;
import org.apache.directory.shared.ldap.entry.client.DefaultClientEntry;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests whether or not authorization around entry modify operations work properly.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 925248 $
 */
@RunWith(FrameworkRunner.class)
public class ModifyAuthorizationIT extends AbstractLdapTestUnit
{

    @Before
    public void setService()
    {
        AutzIntegUtils.ldapServer = ldapServer;
    }


    @After
    public void closeConnections()
    {
        IntegrationUtils.closeConections();
    }
    
    
    /**
     * Checks if an attribute of a simple entry (an organizationalUnit) with an RDN
     * relative to ou=system can be modified by a specific non-admin user.  If a
     * permission exception is encountered it is caught and false is returned,
     * otherwise true is returned.  The entry is deleted after being created just in case
     * subsequent calls to this method are made in the same test case: the admin account
     * is used to add and delete this test entry so permissions to add and delete are not
     * required to test the modify operation by the user.
     *
     * @param uid the unique identifier for the user (presumed to exist under ou=users,ou=system)
     * @param password the password of this user
     * @param entryRdn the relative DN, relative to ou=system where entry is created
     * for modification test
     * @param mods the modifications to make to the entry
     * @return true if the modifications can be made by the user at the specified location,
     * false otherwise.
     * @throws javax.naming.Exception if there are problems conducting the test
     */
    public boolean checkCanModifyAs( String uid, String password, String entryRdn, Modification[] mods )
        throws Exception
    {
        DN entryDN = new DN( entryRdn + ",ou=system" );
        boolean result;
        
        // create the entry with the telephoneNumber attribute to compare
        Entry testEntry = new DefaultClientEntry( entryDN );
        testEntry.add( SchemaConstants.OBJECT_CLASS_AT, "organizationalUnit" );
        testEntry.add( SchemaConstants.OU_AT, "testou" );
        testEntry.add( "telephoneNumber", "867-5309" ); // jenny don't change your number

        LdapConnection adminConnection = getAdminConnection();

        // create the entry as admin
        adminConnection.add( testEntry );

        DN userName = new DN( "uid=" + uid + ",ou=users,ou=system" );
        // compare the telephone numbers
        LdapConnection userConnection = getConnectionAs( userName, password );

        // modify the entry as the user
        ModifyRequest modReq = new ModifyRequest( entryDN );
        modReq.addModification( mods );
        ModifyResponse resp = userConnection.modify( modReq );

        if ( resp.getLdapResult().getResultCode() == ResultCodeEnum.SUCCESS )
        {
            result = true;
        }
        else
        {
            result = false;
        }
        
        // let's clean up
        adminConnection.delete( entryDN );
    
        return result;
    }


    /**
     * Checks if an attribute of a simple entry (an organizationalUnit) with an RDN
     * relative to ou=system can be modified by a specific non-admin user.  If a
     * permission exception is encountered it is caught and false is returned,
     * otherwise true is returned.  The entry is deleted after being created just in case
     * subsequent calls to this method are made in the same test case: the admin account
     * is used to add and delete this test entry so permissions to add and delete are not
     * required to test the modify operation by the user.
     *
     * @param uid the unique identifier for the user (presumed to exist under ou=users,ou=system)
     * @param password the password of this user
     * @param entryRdn the relative DN, relative to ou=system where entry is created
     * for modification test
     * @param mods the attributes to modify in the entry
     * @param modOp the modification operation to use for all attributes
     * @return true if the modifications can be made by the user at the specified location,
     * false otherwise.
     * @throws javax.naming.Exception if there are problems conducting the test
     */
    public boolean checkCanModifyAs( String uid, String password, String entryRdn, ModificationOperation modOp,
        EntryAttribute attr ) throws Exception
    {
        DN entryDN = new DN( entryRdn + ",ou=system" );
        boolean result;

        // create the entry with the telephoneNumber attribute to compare
        Entry testEntry = new DefaultClientEntry( entryDN );
        testEntry.add( SchemaConstants.OBJECT_CLASS_AT, "organizationalUnit" );
        testEntry.add( SchemaConstants.OU_AT, "testou" );
        testEntry.add( "telephoneNumber", "867-5309" ); // jenny don't change your number

        LdapConnection adminConnection = getAdminConnection();

        adminConnection.add( testEntry );

        // create the entry as admin
        DN userName = new DN( "uid=" + uid + ",ou=users,ou=system" );
        // modify the entry as the user
        LdapConnection userConnection = getConnectionAs( userName, password );
        ModifyRequest modReq = new ModifyRequest( entryDN );
        modReq.addModification( attr, modOp );

        ModifyResponse resp = userConnection.modify( modReq );

        if ( resp.getLdapResult().getResultCode() == ResultCodeEnum.SUCCESS )
        {
            result = true;
        }
        else
        {
            result = false;
        }

        // let's clean up
        adminConnection.delete( entryDN );

        return result;
    }


    /**
     * Checks if a user can modify an attribute of their own entry.  Users are
     * presumed to reside under ou=users,ou=system.  If a permission exception is
     * encountered it is caught and false is returned, otherwise true is returned.
     *
     * @param uid the unique identifier for the user (presumed to exist under ou=users,ou=system)
     * @param password the password of this user
     * @param mods the attributes to modify in the entry
     * @return true if the modifications can be made by the user his/her own entry,
     * false otherwise.
     * @throws javax.naming.Exception if there are problems conducting the test
     */
    public boolean checkCanSelfModify( String uid, String password, Modification[] mods ) throws Exception
    {
        // modify the entry as the user
        DN userDN = new DN( "uid=" + uid + ",ou=users,ou=system" );
        LdapConnection connection = getConnectionAs( userDN, password );

        ModifyRequest modReq = new ModifyRequest( userDN );
        modReq.addModification( mods );

        ModifyResponse resp = connection.modify( modReq );

        return resp.getLdapResult().getResultCode() == ResultCodeEnum.SUCCESS;
    }


    /**
     * Converts a set of attributes and a modification operation type into a MoficationItem array.
     *
     * @param modOp the modification operation to perform
     * @param changes the modifications to the attribute
     * @return the array of modification items represting the changes
     * @throws Exception if there are problems accessing attributes
     */
    private Modification[] toItems( ModificationOperation modOp, EntryAttribute... attrs ) throws Exception
    {
        Modification[] mods = new Modification[attrs.length];

        for ( int i = 0; i < attrs.length; i++ )
        {
            EntryAttribute ea = attrs[i];
            mods[i] = new ClientModification( modOp, ea );
        }

        return mods;
    }


    @Test
    public void testSelfModification() throws Exception
    {
        // ----------------------------------------------------------------------------------
        // Modify with Attribute Addition
        // ----------------------------------------------------------------------------------

        // create the non-admin user
        createUser( "billyd", "billyd" );

        // create the password modification
        Modification[] mods = toItems( ModificationOperation.REPLACE_ATTRIBUTE, new DefaultClientAttribute(
            "userPassword", "williams" ) );

        // try a modify operation which should fail without any ACI
        assertFalse( checkCanSelfModify( "billyd", "billyd", mods ) );

        // Gives grantModify, and grantRead perm to all users in the Administrators group for
        // entries and all attribute types and values
        createAccessControlSubentry( "selfModifyUserPassword", "{ " + "identificationTag \"addAci\", "
            + "precedence 14, " + "authenticationLevel none, " + "itemOrUserFirst userFirst: { "
            + "userClasses { thisEntry }, " + "userPermissions { "
            + "{ protectedItems {entry}, grantsAndDenials { grantModify, grantBrowse, grantRead } }, "
            + "{ protectedItems {allAttributeValues {userPassword}}, grantsAndDenials { grantAdd, grantRemove } } "
            + "} } }" );

        // try a modify operation which should succeed with ACI
        assertTrue( checkCanSelfModify( "billyd", "billyd", mods ) );
        deleteAccessControlSubentry( "selfModifyUserPassword" );
    }


    /**
     * Checks to make sure group membership based userClass works for modify operations.
     *
     * @throws javax.naming.Exception if the test encounters an error
     */
    @Test
    public void testGrantModifyByTestGroup() throws Exception
    {
        // ----------------------------------------------------------------------------------
        // Modify with Attribute Addition
        // ----------------------------------------------------------------------------------

        // create the add modifications
        EntryAttribute attr = new DefaultClientAttribute( "registeredAddress", "100 Park Ave." );
        Modification[] mods = toItems( ModificationOperation.ADD_ATTRIBUTE, attr );

        // create the non-admin user
        createUser( "billyd", "billyd" );

        createGroup( "TestGroup" );

        // try a modify operation which should fail without any ACI
        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        // Gives grantModify, and grantRead perm to all users in the TestGroup group for
        // entries and all attribute types and values
        createAccessControlSubentry(
            "administratorModifyAdd",
            "{ "
                + "identificationTag \"addAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { userGroup { \"cn=TestGroup,ou=groups,ou=system\" } }, "
                + "userPermissions { "
                + "{ protectedItems {entry}, grantsAndDenials { grantModify, grantBrowse } }, "
                + "{ protectedItems {attributeType {registeredAddress}, allAttributeValues {registeredAddress}}, grantsAndDenials { grantAdd } } "
                + "} } }" );

        // see if we can now add that test entry which we could not before
        // add op should still fail since billd is not in the admin group
        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        // now add billyd to the TestGroup group and try again
        addUserToGroup( "billyd", "TestGroup" );

        // try a modify operation which should succeed with ACI and group membership change
        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );
        deleteAccessControlSubentry( "administratorModifyAdd" );

        // ----------------------------------------------------------------------------------
        // Modify with Attribute Removal
        // ----------------------------------------------------------------------------------

        // now let's test to see if we can perform a modify with a delete op
        mods = toItems( ModificationOperation.REMOVE_ATTRIBUTE, new DefaultClientAttribute( "telephoneNumber",
            "867-5309" ) );

        // make sure we cannot remove the telephone number from the test entry
        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        // Gives grantModify, and grantRead perm to all users in the TestGroup group for
        // entries and all attribute types and values
        createAccessControlSubentry(
            "administratorModifyRemove",
            "{ "
                + "identificationTag \"addAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { userGroup { \"cn=TestGroup,ou=groups,ou=system\" } }, "
                + "userPermissions { "
                + "{ protectedItems {entry}, grantsAndDenials { grantModify, grantBrowse } }, "
                + "{ protectedItems {attributeType {telephoneNumber}, allAttributeValues {telephoneNumber}}, grantsAndDenials { grantRemove } } "
                + "} } }" );

        // try a modify operation which should succeed with ACI and group membership change
        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );
        deleteAccessControlSubentry( "administratorModifyRemove" );

        // ----------------------------------------------------------------------------------
        // Modify with Attribute Replace (requires both grantRemove and grantAdd on attrs)
        // ----------------------------------------------------------------------------------

        // now let's test to see if we can perform a modify with a delete op
        mods = toItems( ModificationOperation.REPLACE_ATTRIBUTE, new DefaultClientAttribute( "telephoneNumber",
            "867-5309" ) );

        // make sure we cannot remove the telephone number from the test entry
        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        // Gives grantModify, and grantRead perm to all users in the TestGroup group for
        // entries and all attribute types and values
        createAccessControlSubentry(
            "administratorModifyReplace",
            "{ "
                + "identificationTag \"addAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { userGroup { \"cn=TestGroup,ou=groups,ou=system\" } }, "
                + "userPermissions { "
                + "{ protectedItems {entry}, grantsAndDenials { grantModify, grantBrowse } }, "
                + "{ protectedItems {attributeType {registeredAddress}, allAttributeValues {telephoneNumber}}, grantsAndDenials { grantAdd, grantRemove } } "
                + "} } }" );

        // try a modify operation which should succeed with ACI and group membership change
        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );
        deleteAccessControlSubentry( "administratorModifyReplace" );

        /* =================================================================================
         *              DO IT ALL OVER AGAIN BUT USE THE OTHER MODIFY METHOD
         * ================================================================================= */

        // ----------------------------------------------------------------------------------
        // Modify with Attribute Addition
        // ----------------------------------------------------------------------------------
        // create the add modifications
        EntryAttribute changes = new DefaultClientAttribute( "registeredAddress", "100 Park Ave." );

        // try a modify operation which should fail without any ACI
        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", ModificationOperation.ADD_ATTRIBUTE, changes ) );

        // Gives grantModify, and grantRead perm to all users in the TestGroup group for
        // entries and all attribute types and values
        createAccessControlSubentry(
            "administratorModifyAdd",
            "{ "
                + "identificationTag \"addAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { userGroup { \"cn=TestGroup,ou=groups,ou=system\" } }, "
                + "userPermissions { "
                + "{ protectedItems {entry}, grantsAndDenials { grantModify, grantBrowse } }, "
                + "{ protectedItems {attributeType {registeredAddress}, allAttributeValues {registeredAddress}}, grantsAndDenials { grantAdd } } "
                + "} } }" );

        // try a modify operation which should succeed with ACI and group membership change
        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", ModificationOperation.ADD_ATTRIBUTE, changes ) );
        deleteAccessControlSubentry( "administratorModifyAdd" );

        // ----------------------------------------------------------------------------------
        // Modify with Attribute Removal
        // ----------------------------------------------------------------------------------

        // now let's test to see if we can perform a modify with a delete op
        changes = new DefaultClientAttribute( "telephoneNumber", "867-5309" );

        // make sure we cannot remove the telephone number from the test entry
        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", ModificationOperation.REMOVE_ATTRIBUTE, changes ) );

        // Gives grantModify, and grantRead perm to all users in the TestGroup group for
        // entries and all attribute types and values
        createAccessControlSubentry(
            "administratorModifyRemove",
            "{ "
                + "identificationTag \"addAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { userGroup { \"cn=TestGroup,ou=groups,ou=system\" } }, "
                + "userPermissions { "
                + "{ protectedItems {entry}, grantsAndDenials { grantModify, grantBrowse } }, "
                + "{ protectedItems {attributeType {telephoneNumber}, allAttributeValues {telephoneNumber}}, grantsAndDenials { grantRemove } } "
                + "} } }" );

        // try a modify operation which should succeed with ACI and group membership change
        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", ModificationOperation.REMOVE_ATTRIBUTE, changes ) );
        deleteAccessControlSubentry( "administratorModifyRemove" );

        // ----------------------------------------------------------------------------------
        // Modify with Attribute Replace (requires both grantRemove and grantAdd on attrs)
        // ----------------------------------------------------------------------------------

        // now let's test to see if we can perform a modify with a delete op
        changes = new DefaultClientAttribute( "telephoneNumber", "867-5309" );

        // make sure we cannot remove the telephone number from the test entry
        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", ModificationOperation.REPLACE_ATTRIBUTE, changes ) );

        // Gives grantModify, and grantRead perm to all users in the TestGroup group for
        // entries and all attribute types and values
        createAccessControlSubentry(
            "administratorModifyReplace",
            "{ "
                + "identificationTag \"addAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { userGroup { \"cn=TestGroup,ou=groups,ou=system\" } }, "
                + "userPermissions { "
                + "{ protectedItems {entry}, grantsAndDenials { grantModify, grantBrowse } }, "
                + "{ protectedItems {attributeType {registeredAddress}, allAttributeValues {telephoneNumber}}, grantsAndDenials { grantAdd, grantRemove } } "
                + "} } }" );

        // try a modify operation which should succeed with ACI and group membership change
        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", ModificationOperation.REPLACE_ATTRIBUTE, changes ) );
        deleteAccessControlSubentry( "administratorModifyReplace" );
    }


    //    /**
    //     * Checks to make sure name based userClass works for modify operations.
    //     *
    //     * @throws javax.naming.Exception if the test encounters an error
    //     */
    //    public void testGrantModifyByName() throws Exception
    //    {
    //        // create the non-admin user
    //        createUser( "billyd", "billyd" );
    //
    //        // try an modify operation which should fail without any ACI
    //        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", "867-5309" ) );
    //
    //        // now add a subentry that enables user billyd to modify an entry below ou=system
    //        createAccessControlSubentry( "billydAdd", "{ " +
    //                "identificationTag \"addAci\", " +
    //                "precedence 14, " +
    //                "authenticationLevel none, " +
    //                "itemOrUserFirst userFirst: { " +
    //                "userClasses { name { \"uid=billyd,ou=users,ou=system\" } }, " +
    //                "userPermissions { { " +
    //                "protectedItems {entry, allUserAttributeTypesAndValues}, " +
    //                "grantsAndDenials { grantModify, grantRead, grantBrowse } } } } }" );
    //
    //        // should work now that billyd is authorized by name
    //        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", "867-5309" ) );
    //    }
    //
    //
    //    /**
    //     * Checks to make sure subtree based userClass works for modify operations.
    //     *
    //     * @throws javax.naming.Exception if the test encounters an error
    //     */
    //    public void testGrantModifyBySubtree() throws Exception
    //    {
    //        // create the non-admin user
    //        createUser( "billyd", "billyd" );
    //
    //        // try a modify operation which should fail without any ACI
    //        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", "867-5309" ) );
    //
    //        // now add a subentry that enables user billyd to modify an entry below ou=system
    //        createAccessControlSubentry( "billyAddBySubtree", "{ " +
    //                "identificationTag \"addAci\", " +
    //                "precedence 14, " +
    //                "authenticationLevel none, " +
    //                "itemOrUserFirst userFirst: { " +
    //                "userClasses { subtree { { base \"ou=users,ou=system\" } } }, " +
    //                "userPermissions { { " +
    //                "protectedItems {entry, allUserAttributeTypesAndValues}, " +
    //                "grantsAndDenials { grantModify, grantRead, grantBrowse } } } } }" );
    //
    //        // should work now that billyd is authorized by the subtree userClass
    //        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", "867-5309" ) );
    //    }
    //
    //
    //    /**
    //     * Checks to make sure <b>allUsers</b> userClass works for modify operations.
    //     *
    //     * @throws javax.naming.Exception if the test encounters an error
    //     */
    //    public void testGrantModifyAllUsers() throws Exception
    //    {
    //        // create the non-admin user
    //        createUser( "billyd", "billyd" );
    //
    //        // try an add operation which should fail without any ACI
    //        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", "867-5309" ) );
    //
    //        // now add a subentry that enables anyone to add an entry below ou=system
    //        createAccessControlSubentry( "anybodyAdd", "{ " +
    //                "identificationTag \"addAci\", " +
    //                "precedence 14, " +
    //                "authenticationLevel none, " +
    //                "itemOrUserFirst userFirst: { " +
    //                "userClasses { allUsers }, " +
    //                "userPermissions { { " +
    //                "protectedItems {entry, allUserAttributeTypesAndValues}, " +
    //                "grantsAndDenials { grantModify, grantRead, grantBrowse } } } } }" );
    //
    //        // see if we can now modify that test entry's number which we could not before
    //        // should work with billyd now that all users are authorized
    //        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", "867-5309" ) );
    //    }

    @Test
    public void testPresciptiveACIModification() throws Exception
    {

        Modification[] mods = toItems( ModificationOperation.ADD_ATTRIBUTE, new DefaultClientAttribute(
            "registeredAddress", "100 Park Ave." ) );

        createUser( "billyd", "billyd" );

        createAccessControlSubentry(
            "modifyACI",
            "{ "
                + "identificationTag \"modifyAci\", "
                + "precedence 14, "
                + "authenticationLevel none, "
                + "itemOrUserFirst userFirst: { "
                + "userClasses { allUsers }, "
                + "userPermissions { "
                + "{ protectedItems {entry, allUserAttributeTypesAndValues}, grantsAndDenials { grantModify, grantBrowse, grantAdd, grantRemove } } } } }" );

        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        mods = toItems( ModificationOperation.REPLACE_ATTRIBUTE, new DefaultClientAttribute( "registeredAddress",
            "200 Park Ave." ) );

        changePresciptiveACI( "modifyACI", "{ " + "identificationTag \"modifyAci\", " + "precedence 14, "
            + "authenticationLevel none, " + "itemOrUserFirst userFirst: { " + "userClasses { allUsers }, "
            + "userPermissions { "
            + "{ protectedItems {entry, allUserAttributeTypesAndValues}, grantsAndDenials { denyModify } } } } }" );

        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        deleteAccessControlSubentry( "modifyACI" );

    }


    @Test
    public void testMaxValueCountProtectedItem() throws Exception
    {
        createUser( "billyd", "billyd" );
        createAccessControlSubentry( "mvcACI", " {" + " identificationTag \"mvcACI\"," + " precedence 10,"
            + " authenticationLevel simple," + " itemOrUserFirst userFirst:" + " {" + " userClasses { allUsers },"
            + " userPermissions" + " {" + " {" + " protectedItems { entry },"
            + " grantsAndDenials { grantModify, grantBrowse }" + " }" + " ," + " {" + " protectedItems" + " {"
            + " attributeType { description }," + " allAttributeValues { description }," + " maxValueCount" + " {"
            + " { type description, maxCount 1 }" + " }" + " }" + " ," + " grantsAndDenials" + " {" + " grantRemove,"
            + " grantAdd" + " }" + " }" + " }" + " }" + " }" );

        Modification[] mods = toItems( ModificationOperation.ADD_ATTRIBUTE, new DefaultClientAttribute( "description",
            "description 1" ) );

        assertTrue( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        EntryAttribute attr = new DefaultClientAttribute( "description" );
        attr.add( "description 1" );
        attr.add( "description 2" );

        mods = toItems( ModificationOperation.ADD_ATTRIBUTE, attr );

        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );

        mods = toItems( ModificationOperation.REPLACE_ATTRIBUTE, attr );

        assertFalse( checkCanModifyAs( "billyd", "billyd", "ou=testou", mods ) );
    }
}
