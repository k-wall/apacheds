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
package org.apache.directory.server.kerberos.shared.io.encoder;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.directory.server.kerberos.shared.messages.components.Ticket;
import org.apache.directory.shared.asn1.der.ASN1OutputStream;
import org.apache.directory.shared.asn1.der.DERApplicationSpecific;
import org.apache.directory.shared.asn1.der.DERGeneralString;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 589780 $, $Date: 2007-10-29 19:14:59 +0100 (Lun, 29 oct 2007) $
 */
public class TicketEncoder
{
    /**
     * Encodes a {@link Ticket} into a its ASN.1 DER encoding.
     * 
     * @param ticket
     * @return The byte[] containing the ASN.1 DER encoding of the {@link Ticket}.
     * @throws IOException
     */
    public static byte[] encodeTicket( Ticket ticket ) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ASN1OutputStream aos = new ASN1OutputStream( baos );

        aos.writeObject( encode( ticket ) );
        aos.close();

        return baos.toByteArray();
    }


    /**
     * Ticket ::=                    [APPLICATION 1] SEQUENCE {
     *     tkt-vno[0]                   INTEGER,
     *     realm[1]                     Realm,
     *     sname[2]                     PrincipalName,
     *     enc-part[3]                  EncryptedData
     * }
     */
    protected static DERApplicationSpecific encode( Ticket ticket )
    {
        DERSequence vector = new DERSequence();

        vector.add( new DERTaggedObject( 0, DERInteger.valueOf( ticket.getTktVno() ) ) );
        vector.add( new DERTaggedObject( 1, DERGeneralString.valueOf( ticket.getRealm() ) ) );
        vector.add( new DERTaggedObject( 2, PrincipalNameEncoder.encode( ticket.getSName() ) ) );
        vector.add( new DERTaggedObject( 3, EncryptedDataEncoder.encodeSequence( ticket.getEncPart() ) ) );

        DERApplicationSpecific ticketSequence = null;

        try
        {
            ticketSequence = DERApplicationSpecific.valueOf( 1, vector );
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        return ticketSequence;
    }


    protected static DERSequence encodeSequence( Ticket[] tickets )
    {
        DERSequence outerVector = new DERSequence();

        for ( int ii = 0; ii < tickets.length; ii++ )
        {
            DERSequence vector = new DERSequence();
            vector.add( encode( tickets[ii] ) );
            outerVector.add( vector );
        }

        return outerVector;
    }
}
