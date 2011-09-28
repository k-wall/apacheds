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
package org.apache.directory.server.core.authn;


import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.naming.Context;

import org.apache.commons.collections.map.LRUMap;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.authz.AciAuthorizationInterceptor;
import org.apache.directory.server.core.authz.DefaultAuthorizationInterceptor;
import org.apache.directory.server.core.collective.CollectiveAttributeInterceptor;
import org.apache.directory.server.core.event.EventInterceptor;
import org.apache.directory.server.core.exception.ExceptionInterceptor;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.normalization.NormalizationInterceptor;
import org.apache.directory.server.core.operational.OperationalAttributeInterceptor;
import org.apache.directory.server.core.schema.SchemaInterceptor;
import org.apache.directory.server.core.subtree.SubentryInterceptor;
import org.apache.directory.server.core.trigger.TriggerInterceptor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.LdapSecurityConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapAuthenticationException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import org.apache.directory.shared.ldap.util.Base64;
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.directory.shared.ldap.util.UnixCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A simple {@link Authenticator} that authenticates clear text passwords
 * contained within the <code>userPassword</code> attribute in DIT. If the
 * password is stored with a one-way encryption applied (e.g. SHA), the password
 * is hashed the same way before comparison.
 * 
 * We use a cache to speedup authentication, where the DN/password are stored.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class SimpleAuthenticator extends AbstractAuthenticator
{
    private static final Logger LOG = LoggerFactory.getLogger( SimpleAuthenticator.class );

    /** A speedup for logger in debug mode */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** The SHA1 hash length */
    private static final int SHA1_LENGTH = 20;

    /** The MD5 hash length */
    private static final int MD5_LENGTH = 16;

    /**
     * A cache to store passwords. It's a speedup, we will be able to avoid backend lookups.
     * 
     * Note that the backend also use a cache mechanism, but for performance gain, it's good 
     * to manage a cache here. The main problem is that when a user modify his password, we will
     * have to update it at three different places :
     * - in the backend,
     * - in the partition cache,
     * - in this cache.
     * 
     * The update of the backend and partition cache is already correctly handled, so we will
     * just have to offer an access to refresh the local cache.
     * 
     * We need to be sure that frequently used passwords be always in cache, and not discarded.
     * We will use a LRU cache for this purpose. 
     */
    private final LRUMap credentialCache;

    /** Declare a default for this cache. 100 entries seems to be enough */
    private static final int DEFAULT_CACHE_SIZE = 100;

    /**
     * Define the interceptors we should *not* go through when we will have to request the backend
     * about a userPassword.
     */
    private static final Collection<String> USERLOOKUP_BYPASS;

    static
    {
        Set<String> c = new HashSet<String>();
        c.add( NormalizationInterceptor.class.getName() );
        c.add( AuthenticationInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
        c.add( ExceptionInterceptor.class.getName() );
        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
        c.add( TriggerInterceptor.class.getName() );
        USERLOOKUP_BYPASS = Collections.unmodifiableCollection( c );
    }


    /**
     * Creates a new instance.
     * @see AbstractAuthenticator
     */
    public SimpleAuthenticator()
    {
        super( AuthenticationLevel.SIMPLE.toString() );
        credentialCache = new LRUMap( DEFAULT_CACHE_SIZE );
    }


    /**
     * Creates a new instance, with an initial cache size
     * @param cacheSize the size of the credential cache
     */
    public SimpleAuthenticator( int cacheSize )
    {
        super( AuthenticationLevel.SIMPLE.toString() );

        credentialCache = new LRUMap( cacheSize > 0 ? cacheSize : DEFAULT_CACHE_SIZE );
    }

    /**
     * A private class to store all informations about the existing
     * password found in the cache or get from the backend.
     * 
     * This is necessary as we have to compute :
     * - the used algorithm
     * - the salt if any
     * - the password itself.
     * 
     * If we have a on-way encrypted password, it is stored using this 
     * format :
     * {<algorithm>}<encrypted password>
     * where the encrypted password format can be :
     * - MD5/SHA : base64([<salt (4 or 8 bytes)>]<password>)
     * - crypt : <salt (2 btytes)><password> 
     * 
     * Algorithm are currently MD5, SMD5, SHA, SSHA, CRYPT and empty
     */
    private class EncryptionMethod
    {
        private byte[] salt;
        private LdapSecurityConstants algorithm;


        private EncryptionMethod( LdapSecurityConstants algorithm, byte[] salt )
        {
            this.algorithm = algorithm;
            this.salt = salt;
        }
    }


    /**
     * Get the password either from cache or from backend.
     * @param principalDN The DN from which we want the password
     * @return A byte array which can be empty if the password was not found
     * @throws Exception If we have a problem during the lookup operation
     */
    private LdapPrincipal getStoredPassword( BindOperationContext opContext ) throws Exception
    {
        LdapPrincipal principal = null;

        synchronized ( credentialCache )
        {
            principal = ( LdapPrincipal ) credentialCache.get( opContext.getDn().getNormName() );
        }

        byte[] storedPassword;

        if ( principal == null )
        {
            // Not found in the cache
            // Get the user password from the backend
            storedPassword = lookupUserPassword( opContext );

            // Deal with the special case where the user didn't enter a password
            // We will compare the empty array with the credentials. Sometime,
            // a user does not set a password. This is bad, but there is nothing
            // we can do against that, except education ...
            if ( storedPassword == null )
            {
                storedPassword = ArrayUtils.EMPTY_BYTE_ARRAY;
            }

            // Create the new principal before storing it in the cache
            principal = new LdapPrincipal( opContext.getDn(), AuthenticationLevel.SIMPLE, storedPassword );

            // Now, update the local cache.
            synchronized ( credentialCache )
            {
                credentialCache.put( opContext.getDn().getNormName(), principal );
            }
        }

        return principal;
    }


    /**
     * <p>
     * Looks up <tt>userPassword</tt> attribute of the entry whose name is the
     * value of {@link Context#SECURITY_PRINCIPAL} environment variable, and
     * authenticates a user with the plain-text password.
     * </p>
     * We have at least 6 algorithms to encrypt the password :
     * <ul>
     * <li>- SHA</li>
     * <li>- SHA-256</li>
     * <li>- SSHA (salted SHA)</li>
     * <li>- MD5</li>
     * <li>- SMD5 (slated MD5)</li>
     * <li>- crypt (unix crypt)</li>
     * <li>- plain text, ie no encryption.</li>
     * </ul>
     * <p>
     *  If we get an encrypted password, it is prefixed by the used algorithm, between
     *  brackets : {SSHA}password ...
     *  </p>
     *  If the password is using SSHA, SMD5 or crypt, some 'salt' is added to the password :
     *  <ul>
     *  <li>- length(password) - 20, starting at 21th position for SSHA</li>
     *  <li>- length(password) - 16, starting at 16th position for SMD5</li>
     *  <li>- length(password) - 2, starting at 3rd position for crypt</li>
     *  </ul>
     *  <p>
     *  For (S)SHA, SHA-256 and (S)MD5, we have to transform the password from Base64 encoded text
     *  to a byte[] before comparing the password with the stored one.
     *  </p>
     *  <p>
     *  For crypt, we only have to remove the salt.
     *  </p>
     *  <p>
     *  At the end, we use the digest() method for (S)SHA and (S)MD5, the crypt() method for
     *  the CRYPT algorithm and a straight comparison for PLAIN TEXT passwords.
     *  </p>
     *  <p>
     *  The stored password is always using the unsalted form, and is stored as a bytes array.
     *  </p>
     */
    public LdapPrincipal authenticate( BindOperationContext opContext ) throws Exception
    {
        if ( IS_DEBUG )
        {
            LOG.debug( "Authenticating {}", opContext.getDn() );
        }

        // ---- extract password from JNDI environment
        byte[] credentials = opContext.getCredentials();

        LdapPrincipal principal = getStoredPassword( opContext );

        // Get the stored password, either from cache or from backend
        byte[] storedPassword = principal.getUserPassword();

        // Short circuit for PLAIN TEXT passwords : we compare the byte array directly
        // Are the passwords equal ?
        if ( Arrays.equals( credentials, storedPassword ) )
        {
            if ( IS_DEBUG )
            {
                LOG.debug( "{} Authenticated", opContext.getDn() );
            }

            return principal;
        }

        // Let's see if the stored password was encrypted
        LdapSecurityConstants algorithm = findAlgorithm( storedPassword );

        if ( algorithm != null )
        {
            EncryptionMethod encryptionMethod = new EncryptionMethod( algorithm, null );

            // Let's get the encrypted part of the stored password
            // We should just keep the password, excluding the algorithm
            // and the salt, if any.
            // But we should also get the algorithm and salt to
            // be able to encrypt the submitted user password in the next step
            byte[] encryptedStored = splitCredentials( storedPassword, encryptionMethod );

            // Reuse the saltedPassword informations to construct the encrypted
            // password given by the user.
            byte[] userPassword = encryptPassword( credentials, encryptionMethod );

            // Now, compare the two passwords.
            if ( Arrays.equals( userPassword, encryptedStored ) )
            {
                if ( IS_DEBUG )
                {
                    LOG.debug( "{} Authenticated", opContext.getDn() );
                }

                return principal;
            }
            else
            {
                // Bad password ...
                String message = I18n.err( I18n.ERR_230, opContext.getDn().getName() );
                LOG.info( message );
                throw new LdapAuthenticationException( message );
            }
        }
        else
        {
            // Bad password ...
            String message = I18n.err( I18n.ERR_230, opContext.getDn().getName() );
            LOG.info( message );
            throw new LdapAuthenticationException( message );
        }
    }


    private static void split( byte[] all, int offset, byte[] left, byte[] right )
    {
        System.arraycopy( all, offset, left, 0, left.length );
        System.arraycopy( all, offset + left.length, right, 0, right.length );
    }


    /**
     * Decompose the stored password in an algorithm, an eventual salt
     * and the password itself.
     * 
     * If the algorithm is SHA, SSHA, MD5 or SMD5, the part following the algorithm
     * is base64 encoded
     * 
     * @param encryptionMethod The structure to feed
     * @return The password
     * @param credentials the credentials to split
     */
    private byte[] splitCredentials( byte[] credentials, EncryptionMethod encryptionMethod )
    {
        int algoLength = encryptionMethod.algorithm.getName().length() + 2;

        switch ( encryptionMethod.algorithm )
        {
            case HASH_METHOD_MD5:
            case HASH_METHOD_SHA:
            case HASH_METHOD_SHA256:
                try
                {
                    // We just have the password just after the algorithm, base64 encoded.
                    // Just decode the password and return it.
                    return Base64
                        .decode( new String( credentials, algoLength, credentials.length - algoLength, "UTF-8" )
                            .toCharArray() );
                }
                catch ( UnsupportedEncodingException uee )
                {
                    // do nothing 
                    return credentials;
                }

            case HASH_METHOD_SMD5:
                try
                {
                    // The password is associated with a salt. Decompose it 
                    // in two parts, after having decoded the password.
                    // The salt will be stored into the EncryptionMethod structure
                    // The salt is at the end of the credentials, and is 8 bytes long
                    byte[] passwordAndSalt = Base64.decode( new String( credentials, algoLength, credentials.length
                        - algoLength, "UTF-8" ).toCharArray() );

                    int saltLength = passwordAndSalt.length - MD5_LENGTH;
                    encryptionMethod.salt = new byte[saltLength];
                    byte[] password = new byte[MD5_LENGTH];
                    split( passwordAndSalt, 0, password, encryptionMethod.salt );

                    return password;
                }
                catch ( UnsupportedEncodingException uee )
                {
                    // do nothing 
                    return credentials;
                }

            case HASH_METHOD_SSHA:
                try
                {
                    // The password is associated with a salt. Decompose it 
                    // in two parts, after having decoded the password.
                    // The salt will be stored into the EncryptionMethod structure
                    // The salt is at the end of the credentials, and is 8 bytes long
                    byte[] passwordAndSalt = Base64.decode( new String( credentials, algoLength, credentials.length
                        - algoLength, "UTF-8" ).toCharArray() );

                    int saltLength = passwordAndSalt.length - SHA1_LENGTH;
                    encryptionMethod.salt = new byte[saltLength];
                    byte[] password = new byte[SHA1_LENGTH];
                    split( passwordAndSalt, 0, password, encryptionMethod.salt );

                    return password;
                }
                catch ( UnsupportedEncodingException uee )
                {
                    // do nothing 
                    return credentials;
                }

            case HASH_METHOD_CRYPT:
                // The password is associated with a salt. Decompose it 
                // in two parts, storing the salt into the EncryptionMethod structure.
                // The salt comes first, not like for SSHA and SMD5, and is 2 bytes long
                encryptionMethod.salt = new byte[2];
                byte[] password = new byte[credentials.length - encryptionMethod.salt.length - algoLength];
                split( credentials, algoLength, encryptionMethod.salt, password );

                return password;

            default:
                // unknown method
                return credentials;

        }
    }


    /**
     * Get the algorithm from the stored password. 
     * It can be found on the beginning of the stored password, between 
     * curly brackets.
     * @param credentials the credentials of the user
     * @return the name of the algorithm to use
     * TODO use an enum for the algorithm
     */
    private LdapSecurityConstants findAlgorithm( byte[] credentials )
    {
        if ( ( credentials == null ) || ( credentials.length == 0 ) )
        {
            return null;
        }

        if ( credentials[0] == '{' )
        {
            // get the algorithm
            int pos = 1;

            while ( pos < credentials.length )
            {
                if ( credentials[pos] == '}' )
                {
                    break;
                }

                pos++;
            }

            if ( pos < credentials.length )
            {
                if ( pos == 1 )
                {
                    // We don't have an algorithm : return the credentials as is
                    return null;
                }

                String algorithm = new String( credentials, 1, pos - 1 ).toLowerCase();

                return LdapSecurityConstants.getAlgorithm( algorithm );
            }
            else
            {
                // We don't have an algorithm
                return null;
            }
        }
        else
        {
            // No '{algo}' part
            return null;
        }
    }


    /**
     * Compute the hashed password given an algorithm, the credentials and 
     * an optional salt.
     *
     * @param algorithm the algorithm to use
     * @param password the credentials
     * @param salt the optional salt
     * @return the digested credentials
     */
    private static byte[] digest( LdapSecurityConstants algorithm, byte[] password, byte[] salt )
    {
        MessageDigest digest;

        try
        {
            digest = MessageDigest.getInstance( algorithm.getName() );
        }
        catch ( NoSuchAlgorithmException e1 )
        {
            return null;
        }

        if ( salt != null )
        {
            digest.update( password );
            digest.update( salt );
            return digest.digest();
        }
        else
        {
            return digest.digest( password );
        }
    }


    private byte[] encryptPassword( byte[] credentials, EncryptionMethod encryptionMethod )
    {
        byte[] salt = encryptionMethod.salt;

        switch ( encryptionMethod.algorithm )
        {
            case HASH_METHOD_SHA:
            case HASH_METHOD_SSHA:
                return digest( LdapSecurityConstants.HASH_METHOD_SHA, credentials, salt );

            case HASH_METHOD_SHA256:
                return digest( LdapSecurityConstants.HASH_METHOD_SHA256, credentials, salt );
                
            case HASH_METHOD_MD5:
            case HASH_METHOD_SMD5:
                return digest( LdapSecurityConstants.HASH_METHOD_MD5, credentials, salt );

            case HASH_METHOD_CRYPT:
                if ( salt == null )
                {
                    salt = new byte[2];
                    SecureRandom sr = new SecureRandom();
                    int i1 = sr.nextInt( 64 );
                    int i2 = sr.nextInt( 64 );

                    salt[0] = ( byte ) ( i1 < 12 ? ( i1 + '.' ) : i1 < 38 ? ( i1 + 'A' - 12 ) : ( i1 + 'a' - 38 ) );
                    salt[1] = ( byte ) ( i2 < 12 ? ( i2 + '.' ) : i2 < 38 ? ( i2 + 'A' - 12 ) : ( i2 + 'a' - 38 ) );
                }

                String saltWithCrypted = UnixCrypt.crypt( StringTools.utf8ToString( credentials ), StringTools
                    .utf8ToString( salt ) );
                String crypted = saltWithCrypted.substring( 2 );

                return StringTools.getBytesUtf8( crypted );

            default:
                return credentials;
        }
    }


    /**
     * Local function which request the password from the backend
     * @param principalDn the principal to lookup
     * @return the credentials from the backend
     * @throws Exception if there are problems accessing backend
     */
    private byte[] lookupUserPassword( BindOperationContext opContext ) throws Exception
    {
        // ---- lookup the principal entry's userPassword attribute
        ServerEntry userEntry;

        try
        {
            /*
             * NOTE: at this point the BindOperationContext does not has a 
             * null session since the user has not yet authenticated so we
             * cannot use opContext.lookup() yet.  This is a very special
             * case where we cannot rely on the opContext to perform a new
             * sub operation.
             */
            LookupOperationContext lookupContext = new LookupOperationContext( getDirectoryService().getAdminSession(),
                opContext.getDn() );
            lookupContext.setByPassed( USERLOOKUP_BYPASS );
            userEntry = getDirectoryService().getOperationManager().lookup( lookupContext );

            if ( userEntry == null )
            {
                DN dn = opContext.getDn();
                String upDn = ( dn == null ? "" : dn.getName() );

                throw new LdapAuthenticationException( I18n.err( I18n.ERR_231, upDn ) );
            }
        }
        catch ( Exception cause )
        {
            LOG.error( I18n.err( I18n.ERR_6, cause.getLocalizedMessage() ) );
            LdapAuthenticationException e = new LdapAuthenticationException( cause.getLocalizedMessage() );
            e.initCause( e );
            throw e;
        }

        Value<?> userPassword;

        EntryAttribute userPasswordAttr = userEntry.get( SchemaConstants.USER_PASSWORD_AT );

        // ---- assert that credentials match
        if ( userPasswordAttr == null )
        {
            return StringTools.EMPTY_BYTES;
        }
        else
        {
            userPassword = userPasswordAttr.get();

            return userPassword.getBytes();
        }
    }


    /**
     * Get the algorithm of a password, which is stored in the form "{XYZ}...".
     * The method returns null, if the argument is not in this form. It returns
     * XYZ, if XYZ is an algorithm known to the MessageDigest class of
     * java.security.
     * 
     * @param password a byte[]
     * @return included message digest alorithm, if any
     * @throws IllegalArgumentException if the algorithm cannot be identified
     */
    protected String getAlgorithmForHashedPassword( byte[] password ) throws IllegalArgumentException
    {
        String result = null;

        // Check if password arg is string or byte[]
        String sPassword = StringTools.utf8ToString( password );
        int rightParen = sPassword.indexOf( '}' );

        if ( ( sPassword.length() > 2 ) && ( sPassword.charAt( 0 ) == '{' ) && ( rightParen > -1 ) )
        {
            String algorithm = sPassword.substring( 1, rightParen );

            if ( LdapSecurityConstants.HASH_METHOD_CRYPT.getName().equalsIgnoreCase( algorithm ) )
            {
                return algorithm;
            }

            try
            {
                MessageDigest.getInstance( algorithm );
                result = algorithm;
            }
            catch ( NoSuchAlgorithmException e )
            {
                LOG.warn( "Unknown message digest algorithm in password: " + algorithm, e );
            }
        }

        return result;
    }


    /**
     * Creates a digested password. For a given hash algorithm and a password
     * value, the algorithm is applied to the password, and the result is Base64
     * encoded. The method returns a String which looks like "{XYZ}bbbbbbb",
     * whereas XYZ is the name of the algorithm, and bbbbbbb is the Base64
     * encoded value of XYZ applied to the password.
     * 
     * @param algorithm
     *            an algorithm which is supported by
     *            java.security.MessageDigest, e.g. SHA
     * @param password
     *            password value, a byte[]
     * 
     * @return a digested password, which looks like
     *         {SHA}LhkDrSoM6qr0fW6hzlfOJQW61tc=
     * 
     * @throws IllegalArgumentException
     *             if password is neither a String nor a byte[], or algorithm is
     *             not known to java.security.MessageDigest class
     */
    protected String createDigestedPassword( String algorithm, byte[] password ) throws IllegalArgumentException
    {
        // create message digest object
        try
        {
            if ( LdapSecurityConstants.HASH_METHOD_CRYPT.getName().equalsIgnoreCase( algorithm ) )
            {
                String saltWithCrypted = UnixCrypt.crypt( StringTools.utf8ToString( password ), "" );
                String crypted = saltWithCrypted.substring( 2 );
                return '{' + algorithm + '}' + Arrays.toString( StringTools.getBytesUtf8( crypted ) );
            }
            else
            {
                MessageDigest digest = MessageDigest.getInstance( algorithm );

                // calculate hashed value of password
                byte[] fingerPrint = digest.digest( password );
                char[] encoded = Base64.encode( fingerPrint );

                // create return result of form "{alg}bbbbbbb"
                return '{' + algorithm + '}' + new String( encoded );
            }
        }
        catch ( NoSuchAlgorithmException nsae )
        {
            LOG.error( I18n.err( I18n.ERR_7, algorithm ) );
            throw new IllegalArgumentException( nsae.getLocalizedMessage() );
        }
    }


    /**
     * Remove the principal form the cache. This is used when the user changes
     * his password.
     */
    public void invalidateCache( DN bindDn )
    {
        synchronized ( credentialCache )
        {
            credentialCache.remove( bindDn.getNormName() );
        }
    }
}
