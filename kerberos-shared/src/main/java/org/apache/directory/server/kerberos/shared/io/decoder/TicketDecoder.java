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
package org.apache.directory.server.kerberos.shared.io.decoder;


import java.io.IOException;
import java.util.Enumeration;

import org.apache.directory.server.kerberos.shared.messages.components.Ticket;
import org.apache.directory.shared.asn1.der.ASN1InputStream;
import org.apache.directory.shared.asn1.der.DERApplicationSpecific;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERGeneralString;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 589780 $, $Date: 2007-10-29 19:14:59 +0100 (Lun, 29 oct 2007) $
 */
public class TicketDecoder
{
    /**
     * Decodes a byte array into an {@link Ticket}.
     *
     * @param encodedTicket
     * @return The {@link Ticket}.
     * @throws IOException
     */
    public static Ticket decode( byte[] encodedTicket ) throws IOException
    {
        ASN1InputStream ais = new ASN1InputStream( encodedTicket );

        DERApplicationSpecific app = ( DERApplicationSpecific ) ais.readObject();

        return decode( app );
    }


    /**
     * Decodes a {@link DERSequence} into an array of {@link Ticket}s.
     *
     * @param sequence
     * @return The array of {@link Ticket}s.
     * @throws IOException
     */
    public static Ticket[] decodeSequence( DERSequence sequence ) throws IOException
    {
        Ticket[] tickets = new Ticket[sequence.size()];

        int ii = 0;
        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERApplicationSpecific object = ( DERApplicationSpecific ) e.nextElement();
            tickets[ii] = decode( object );
        }

        return tickets;
    }


    /**
     * Ticket ::=                    [APPLICATION 1] SEQUENCE {
     *     tkt-vno[0]                   INTEGER,
     *     realm[1]                     Realm,
     *     sname[2]                     PrincipalName,
     *     enc-part[3]                  EncryptedData
     * }
     */
    protected static Ticket decode( DERApplicationSpecific app ) throws IOException
    {
        DERSequence sequence = ( DERSequence ) app.getObject();

        Ticket ticket = new Ticket();

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger tag0 = ( DERInteger ) derObject;
                    ticket.setTktVno( tag0.intValue() );
                    break;
                    
                case 1:
                    DERGeneralString tag1 = ( DERGeneralString ) derObject;
                    ticket.setRealm( tag1.getString() );
                    break;
                    
                case 2:
                    DERSequence tag2 = ( DERSequence ) derObject;
                    ticket.setSName( PrincipalNameDecoder.decode( tag2 ) );
                    break;
                    
                case 3:
                    DERSequence tag3 = ( DERSequence ) derObject;
                    ticket.setEncPart( EncryptedDataDecoder.decode( tag3 ) );
                    break;
            }
        }

        return ticket;
    }
}
