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
package org.apache.directory.server.core.kerberos;


import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.BinaryValue;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/**
 * An {@link Interceptor} that enforces password policy for users.  Add or modify operations
 * on the 'userPassword' attribute are checked against a password policy.  The password is
 * rejected if it does not pass the password policy checks.  The password MUST be passed to
 * the core as plaintext.
 * 
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class PasswordPolicyInterceptor extends BaseInterceptor
{
    /** The log for this class. */
    private static final Logger log = LoggerFactory.getLogger( PasswordPolicyInterceptor.class );

    /** The service name. */
    public static final String NAME = "passwordPolicyService";


    /**
     * Check added attributes for a 'userPassword'.  If a 'userPassword' is found, apply any
     * password policy checks.
     */
    public void add( NextInterceptor next, AddOperationContext addContext ) throws Exception
    {
        DN normName = addContext.getDn();

        ServerEntry entry = addContext.getEntry();

        log.debug( "Adding the entry '{}' for DN '{}'.", entry, normName.getName() );

        if ( entry.get( SchemaConstants.USER_PASSWORD_AT ) != null )
        {
            String username = null;

            BinaryValue userPassword = (BinaryValue)entry.get( SchemaConstants.USER_PASSWORD_AT ).get();

            // The password is stored in a non H/R attribute, but it's a String
            String strUserPassword = userPassword.getString();

            if ( log.isDebugEnabled() )
            {
                StringBuffer sb = new StringBuffer();
                sb.append( "'" + strUserPassword + "' ( " );
                sb.append( userPassword );
                sb.append( " )" );
                log.debug( "Adding Attribute id : 'userPassword',  Values : [ {} ]", sb.toString() );
            }

            if ( entry.get( SchemaConstants.CN_AT ) != null )
            {
                StringValue attr = (StringValue)entry.get( SchemaConstants.CN_AT ).get();
                username = attr.getString();
            }

            // If userPassword fails checks, throw new NamingException.
            check( username, strUserPassword );
        }

        next.add( addContext );
    }


    /**
     * Check modification items for a 'userPassword'.  If a 'userPassword' is found, apply any
     * password policy checks.
     */
    public void modify( NextInterceptor next, ModifyOperationContext modContext ) throws Exception
    {
        DN name = modContext.getDn();

        List<Modification> mods = modContext.getModItems();

        String operation = null;

        for ( Modification mod:mods )
        {
            if ( log.isDebugEnabled() )
            {
                switch ( mod.getOperation() )
                {
                    case ADD_ATTRIBUTE:
                        operation = "Adding";
                        break;
                        
                    case REMOVE_ATTRIBUTE:
                        operation = "Removing";
                        break;
                        
                    case REPLACE_ATTRIBUTE:
                        operation = "Replacing";
                        break;
                }
            }

            EntryAttribute attr = mod.getAttribute();

            if ( attr.instanceOf( SchemaConstants.USER_PASSWORD_AT ) )
            {
                Value<?> userPassword = attr.get();
                String pwd = "";

                if ( userPassword != null )
                {
                    if ( userPassword instanceof StringValue )
                    {
                        log.debug( "{} Attribute id : 'userPassword',  Values : [ '{}' ]", operation, attr );
                        pwd = ((StringValue)userPassword).getString();
                    }
                    else if ( userPassword instanceof BinaryValue )
                    {
                        BinaryValue password = (BinaryValue)userPassword;
                        
                        String string = password.getString();

                        if ( log.isDebugEnabled() )
                        {
                            StringBuffer sb = new StringBuffer();
                            sb.append( "'" + string + "' ( " );
                            sb.append( StringTools.dumpBytes( password.getBytes() ).trim() );
                            sb.append( " )" );
                            log.debug( "{} Attribute id : 'userPassword',  Values : [ {} ]", operation, sb.toString() );
                        }

                        pwd = string;
                    }

                    // if userPassword fails checks, throw new NamingException.
                    check( name.getName(), pwd );
                }
            }

            if ( log.isDebugEnabled() )
            {
                log.debug( operation + " for entry '" + name.getName() + "' the attribute " + mod.getAttribute() );
            }
        }

        next.modify( modContext );
    }


    void check( String username, String password ) throws Exception
    {
        int passwordLength = 6;
        int categoryCount = 2;
        int tokenSize = 3;

        if ( !isValid( username, password, passwordLength, categoryCount, tokenSize ) )
        {
            String explanation = buildErrorMessage( username, password, passwordLength, categoryCount, tokenSize );
            log.error( explanation );

            throw new Exception( explanation );
        }
    }


    /**
     * Tests that:
     * The password is at least six characters long.
     * The password contains a mix of characters.
     * The password does not contain three letter (or more) tokens from the user's account name.
     */
    boolean isValid( String username, String password, int passwordLength, int categoryCount, int tokenSize )
    {
        return isValidPasswordLength( password, passwordLength ) && isValidCategoryCount( password, categoryCount )
            && isValidUsernameSubstring( username, password, tokenSize );
    }


    /**
     * The password is at least six characters long.
     */
    boolean isValidPasswordLength( String password, int passwordLength )
    {
        return password.length() >= passwordLength;
    }


    /**
     * The password contains characters from at least three of the following four categories:
     * English uppercase characters (A - Z)
     * English lowercase characters (a - z)
     * Base 10 digits (0 - 9)
     * Any non-alphanumeric character (for example: !, $, #, or %)
     */
    boolean isValidCategoryCount( String password, int categoryCount )
    {
        int uppercase = 0;
        int lowercase = 0;
        int digit = 0;
        int nonAlphaNumeric = 0;

        char[] characters = password.toCharArray();

        for ( char character:characters )
        {
            if ( Character.isLowerCase( character ) )
            {
                lowercase = 1;
            }
            else
            {
                if ( Character.isUpperCase( character ) )
                {
                    uppercase = 1;
                }
                else
                {
                    if ( Character.isDigit( character ) )
                    {
                        digit = 1;
                    }
                    else
                    {
                        if ( !Character.isLetterOrDigit( character ) )
                        {
                            nonAlphaNumeric = 1;
                        }
                    }
                }
            }
        }

        return ( uppercase + lowercase + digit + nonAlphaNumeric ) >= categoryCount;
    }


    /**
     * The password does not contain three letter (or more) tokens from the user's account name.
     * 
     * If the account name is less than three characters long, this check is not performed
     * because the rate at which passwords would be rejected is too high. For each token that is
     * three or more characters long, that token is searched for in the password; if it is present,
     * the password change is rejected. For example, the name "First M. Last" would be split into
     * three tokens: "First", "M", and "Last". Because the second token is only one character long,
     * it would be ignored. Therefore, this user could not have a password that included either
     * "first" or "last" as a substring anywhere in the password. All of these checks are
     * case-insensitive.
     */
    boolean isValidUsernameSubstring( String username, String password, int tokenSize )
    {
        String[] tokens = username.split( "[^a-zA-Z]" );

        for ( int ii = 0; ii < tokens.length; ii++ )
        {
            if ( tokens[ii].length() >= tokenSize )
            {
                if ( password.matches( "(?i).*" + tokens[ii] + ".*" ) )
                {
                    return false;
                }
            }
        }

        return true;
    }


    private String buildErrorMessage( String username, String password, int passwordLength, int categoryCount,
        int tokenSize )
    {
        List<String> violations = new ArrayList<String>();

        if ( !isValidPasswordLength( password, passwordLength ) )
        {
            violations.add( "length too short" );
        }

        if ( !isValidCategoryCount( password, categoryCount ) )
        {
            violations.add( "insufficient character mix" );
        }

        if ( !isValidUsernameSubstring( username, password, tokenSize ) )
        {
            violations.add( "contains portions of username" );
        }

        StringBuffer sb = new StringBuffer( "Password violates policy:  " );

        boolean isFirst = true;

        for ( String violation : violations )
        {
            if ( isFirst )
            {
                isFirst = false;
            }
            else
            {
                sb.append( ", " );
            }

            sb.append( violation );
        }

        return sb.toString();
    }
}
