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
package org.apache.directory.server.core.changelog;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.ldif.LdifEntry;


/**
 * A loggable directory change event.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ChangeLogEvent implements Externalizable
{
    private static final long serialVersionUID = 1L;
    private String zuluTime;
    private long revision;
    private LdifEntry forwardLdif;
    
    /** The revert changes. Can contain more than one single change */
    private List<LdifEntry> reverseLdifs;
    private LdapPrincipal committer;


    /**
     * Creates a new instance of ChangeLogEvent, used during the deserialization
     * process
     */
    public ChangeLogEvent()
    {
    }


    /**
     * Creates a new instance of ChangeLogEvent.
     *
     * @param revision the revision number for the change
     * @param zuluTime the timestamp for when the change occurred in generalizedTime format
     */
    public ChangeLogEvent( long revision, String zuluTime, LdapPrincipal committer, LdifEntry forwardLdif,
                           LdifEntry reverseLdif )
    {
        this.zuluTime = zuluTime;
        this.revision = revision;
        this.forwardLdif = forwardLdif;
        this.reverseLdifs = new ArrayList<LdifEntry>(1);
        reverseLdifs.add( reverseLdif );
        this.committer = committer;
    }


    /**
     * Creates a new instance of ChangeLogEvent.
     *
     * @param revision the revision number for the change
     * @param zuluTime the timestamp for when the change occurred in generalizedTime format
     * @param committer the user who did the modification
     * @param forwardLdif the original operation
     * @param reverseLdifs the reverted operations
     */
    public ChangeLogEvent( long revision, String zuluTime, LdapPrincipal committer, LdifEntry forwardLdif,
                           List<LdifEntry> reverseLdifs )
    {
        this.zuluTime = zuluTime;
        this.revision = revision;
        this.forwardLdif = forwardLdif;
        this.reverseLdifs = reverseLdifs;
        this.committer = committer;
    }


    /**
     * @return the forwardLdif
     */
    public LdifEntry getForwardLdif()
    {
        return forwardLdif;
    }


    /**
     * @return the reverseLdif
     */
    public List<LdifEntry> getReverseLdifs()
    {
        return reverseLdifs;
    }


    /**
     * @return the committer
     */
    public LdapPrincipal getCommitterPrincipal()
    {
        return committer;
    }


    /**
     * Gets the revision of this event.
     *
     * @return the revision
     */
    public long getRevision()
    {
        return revision;
    }


    /**
     * Gets the generalizedTime when this event occured.
     *
     * @return the zuluTime when this event occured
     */
    public String getZuluTime()
    {
        return zuluTime;
    }


    public EntryAttribute get( String attributeName )
    {
        return forwardLdif.get( attributeName );
    }


    /**
     * @see Externalizable#readExternal(ObjectInput)
     * 
     * @param in The stream from which the ChangeOlgEvent is read
     * @throws IOException If the stream can't be read
     * @throws ClassNotFoundException If the ChangeLogEvent can't be created 
     */
    public void readExternal( ObjectInput in ) throws IOException , ClassNotFoundException
    {
        // Read the committer
        committer = (LdapPrincipal)in.readObject();
        
        // Read the revision
        revision = in.readLong();
        
        // Read the time
        boolean hasZuluTime = in.readBoolean();
        
        if ( hasZuluTime )
        {
            zuluTime = in.readUTF();
        }
        
        // Read the forward LDIF
        boolean hasForwardLdif = in.readBoolean();
        
        if ( hasForwardLdif )
        {
            forwardLdif = (LdifEntry)in.readObject();
        }
        
        // Read the reverse LDIF number
        int nbReverseLdif = in.readInt();
        
        if ( nbReverseLdif > 0 )
        {
            // Read each reverse ldif
            reverseLdifs = new ArrayList<LdifEntry>(nbReverseLdif);
            
            for ( int i = 0; i < nbReverseLdif; i++ )
            {
                reverseLdifs.add( (LdifEntry)in.readObject() ); 
            }
        }
    }


    /**
     * @see Externalizable#readExternal(ObjectInput)<p>
     *
     *@param out The stream in which the ChangeLogEvent will be serialized. 
     *
     *@throws IOException If the serialization fail
     */
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        // Write the committer
        out.writeObject( committer );
        
        // write the revision
        out.writeLong( revision );
        
        // write the time
        
        if ( zuluTime != null )
        {
            out.writeBoolean( true );
            out.writeUTF( zuluTime );
        }
        else
        {
            out.writeBoolean( false );
        }
        
        // write the forward LDIF
        if ( forwardLdif != null )
        {
            out.writeBoolean( true );
            out.writeObject( forwardLdif );
        }
        else
        {
            out.writeBoolean( false );
        }
        
        // write the reverse LDIF
        if ( reverseLdifs != null )
        {
            out.writeInt( reverseLdifs.size() );
            
            // write each reverse
            for ( LdifEntry reverseLdif:reverseLdifs )
            {
                out.writeObject( reverseLdif );
            }
        }
        else
        {
            out.writeBoolean( false );
        }
        
        // and flush the result
        out.flush();
    }


    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "ChangeLogEvent { " );
        
        sb.append( "principal=" )
        .append( getCommitterPrincipal() )
        .append( ", " );
        
        sb.append( "zuluTime=" )
          .append( getZuluTime() )
          .append( ", " );
        
        sb.append( "revision=" )
        .append( getRevision() )
        .append( ", " );
        
        sb.append( "\nforwardLdif=" )
        .append( getForwardLdif() )
        .append( ", " );
        
        if ( reverseLdifs != null )
        {
            sb.append( "\nreverseLdif number=" ).append( reverseLdifs.size() );
            int i = 0;
            
            for ( LdifEntry reverseLdif:reverseLdifs )
            {
                sb.append( "\nReverse[" ).append( i++ ).append( "] :\n" );
                sb.append( reverseLdif );
            }
        }
        
        sb.append( " }" );

        return sb.toString();
    }
}
