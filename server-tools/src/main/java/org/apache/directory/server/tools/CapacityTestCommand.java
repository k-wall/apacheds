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
package org.apache.directory.server.tools;


import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Hashtable;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.directory.daemon.AvailablePortFinder;
import org.apache.directory.server.i18n.I18n;


/**
 * A capacity testing tool.  This command will generate bogus user
 * entries and add them under a base DN.  It will output a table 
 * of values mapping the capacity of the partition to the time it
 * took to add an entry to it.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class CapacityTestCommand extends ToolCommand
{
    public static final String PORT_RANGE = "(" + AvailablePortFinder.MIN_PORT_NUMBER + ", "
        + AvailablePortFinder.MAX_PORT_NUMBER + ")";

    private int port = 10389;
    private String host = "localhost";
    private String password = "secret";
    private String baseDn = "ou=users,dc=example,dc=com";


    public CapacityTestCommand()
    {
        super( "capacity" );
    }


    public void execute( CommandLine cmdline ) throws Exception
    {
        processOptions( cmdline );
        getLayout().verifyInstallation();
        String outputFile = cmdline.getOptionValue( 'f' );
        PrintWriter out = null;

        if ( outputFile == null )
        {
            out = new PrintWriter( System.out );
        }
        else
        {
            out = new PrintWriter( new FileWriter( outputFile ) );
        }

        if ( isDebugEnabled() )
        {
            out.println( "Parameters for capacity extended request:" );
            out.println( "port = " + port );
            out.println( "host = " + host );
            out.println( "password = " + password );
        }

        Hashtable env = new Hashtable();
        env.put( "java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( "java.naming.provider.url", "ldap://" + host + ":" + port );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( "java.naming.security.credentials", password );
        env.put( "java.naming.security.authentication", "simple" );

        LdapContext ctx = new InitialLdapContext( env, null );

        // create the base dn if it does not exist
        createBase( ctx );

        StringBuffer dnBuf = new StringBuffer();
        StringBuffer outBuf = new StringBuffer();
        int counter = 0;
        if ( cmdline.hasOption( "s" ) )
        {
            counter = Integer.parseInt( cmdline.getOptionValue( 's' ) ) - 1;
        }
        int end = Integer.MAX_VALUE;
        if ( cmdline.hasOption( "e" ) )
        {
            end = Integer.parseInt( cmdline.getOptionValue( 'e' ) );
        }

        while ( counter < end )
        {
            counter++;
            Attributes attrs = generateLdif( counter );
            dnBuf.setLength( 0 );
            dnBuf.append( "uid=user." ).append( counter ).append( "," ).append( baseDn );

            long startTime = System.currentTimeMillis();
            ctx.createSubcontext( dnBuf.toString(), attrs );

            outBuf.setLength( 0 );
            outBuf.append( counter ).append( " " ).append( System.currentTimeMillis() - startTime );
            out.println( outBuf.toString() );
            out.flush();
        }
    }


    private boolean createBase( LdapContext ctx ) throws NamingException
    {
        Attributes attrs = new BasicAttributes( "objectClass", "organizationalUnit", true );
        attrs.put( "ou", "users" );

        try
        {
            ctx.createSubcontext( "ou=users,dc=example,dc=com", attrs );
            return true;
        }
        catch ( NameAlreadyBoundException e )
        {
            return false;
        }
    }


    private Attributes generateLdif( int counter )
    {
        Attributes attrs = new BasicAttributes( "objectClass", "top", true );
        Attribute oc = attrs.get( "objectClass" );
        oc.add( "person" );
        oc.add( "organizationalPerson" );
        oc.add( "inetOrgPerson" );

        attrs.put( "givenName", RandomStringUtils.randomAlphabetic( 6 ) );
        attrs.put( "sn", RandomStringUtils.randomAlphabetic( 9 ) );
        attrs.put( "cn", RandomStringUtils.randomAlphabetic( 15 ) );
        attrs.put( "initials", RandomStringUtils.randomAlphabetic( 2 ) );
        attrs.put( "mail", RandomStringUtils.randomAlphabetic( 15 ) );
        attrs.put( "userPassword", "password" );
        attrs.put( "telephoneNumber", RandomStringUtils.randomNumeric( 10 ) );
        attrs.put( "homePhone", RandomStringUtils.randomNumeric( 10 ) );
        attrs.put( "pager", RandomStringUtils.randomNumeric( 10 ) );
        attrs.put( "mobile", RandomStringUtils.randomNumeric( 10 ) );
        attrs.put( "employeeNumber", String.valueOf( counter ) );
        attrs.put( "street", RandomStringUtils.randomAlphabetic( 20 ) );
        attrs.put( "l", RandomStringUtils.randomAlphabetic( 10 ) );
        attrs.put( "st", RandomStringUtils.randomAlphabetic( 2 ) );
        attrs.put( "postalCode", RandomStringUtils.randomAlphabetic( 5 ) );
        attrs.put( "postalAddress", RandomStringUtils.randomAlphabetic( 20 ) );
        attrs.put( "description", RandomStringUtils.randomAlphabetic( 20 ) );
        return attrs;
    }


    private void processOptions( CommandLine cmd )
    {
        if ( isDebugEnabled() )
        {
            System.out.println( "Processing options for capacity test ..." );
        }

        // -------------------------------------------------------------------
        // figure out and error check the port value
        // -------------------------------------------------------------------

        if ( cmd.hasOption( 'p' ) ) // - user provided port w/ -p takes precedence
        {
            String val = cmd.getOptionValue( 'p' );
            try
            {
                port = Integer.parseInt( val );
            }
            catch ( NumberFormatException e )
            {
                System.err.println( I18n.err( I18n.ERR_193, val ) );
                System.exit( 1 );
            }

            if ( port > AvailablePortFinder.MAX_PORT_NUMBER )
            {
                System.err.println( I18n.err( I18n.ERR_194, val, AvailablePortFinder.MAX_PORT_NUMBER ) );
                System.exit( 1 );
            }
            else if ( port < AvailablePortFinder.MIN_PORT_NUMBER )
            {
                System.err.println( I18n.err( I18n.ERR_195, val, AvailablePortFinder.MIN_PORT_NUMBER ) );
                System.exit( 1 );
            }

            if ( isDebugEnabled() )
            {
                System.out.println( "port overriden by -p option: " + port );
            }
        }
        else if ( getApacheDS() != null )
        {
            port = getApacheDS().getLdapServer().getPort();

            if ( isDebugEnabled() )
            {
                System.out.println( "port overriden by server.xml configuration: " + port );
            }
        }
        else if ( isDebugEnabled() )
        {
            System.out.println( "port set to default: " + port );
        }

        // -------------------------------------------------------------------
        // figure out the host value
        // -------------------------------------------------------------------

        if ( cmd.hasOption( 'h' ) )
        {
            host = cmd.getOptionValue( 'h' );

            if ( isDebugEnabled() )
            {
                System.out.println( "host overriden by -h option: " + host );
            }
        }
        else if ( isDebugEnabled() )
        {
            System.out.println( "host set to default: " + host );
        }

        // -------------------------------------------------------------------
        // figure out the password value
        // -------------------------------------------------------------------

        if ( cmd.hasOption( 'w' ) )
        {
            password = cmd.getOptionValue( 'w' );

            if ( isDebugEnabled() )
            {
                System.out.println( "password overriden by -w option: " + password );
            }
        }
        else if ( isDebugEnabled() )
        {
            System.out.println( "password set to default: " + password );
        }
    }


    public Options getOptions()
    {
        Options opts = new Options();
        Option op = new Option( "f", "file", true, "file to output the stats to" );
        op.setRequired( false );
        opts.addOption( op );
        op = new Option( "i", "install-path", true, "path to apacheds installation directory" );
        op.setRequired( true );
        opts.addOption( op );
        op = new Option( "h", "host", true, "server host: defaults to localhost" );
        op.setRequired( false );
        opts.addOption( op );
        op = new Option( "p", "port", true, "server port: defaults to 10389 or server.xml specified port" );
        op.setRequired( false );
        opts.addOption( op );
        op = new Option( "w", "password", true, "the apacheds administrator's password: defaults to secret" );
        op.setRequired( false );
        opts.addOption( op );

        op = new Option( "s", "start", true, "start on id: number to start on (user.start)" );
        op.setRequired( false );
        opts.addOption( op );

        op = new Option( "e", "end", true, "end on id: number to end on (user.end)" );
        op.setRequired( false );
        opts.addOption( op );

        return opts;
    }
}
